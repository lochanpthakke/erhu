package com.lochanpthakke.empee3.core.playback.transition

import com.lochanpthakke.empee3.core.model.TransitionRole
import com.lochanpthakke.empee3.core.playback.Cancellable
import com.lochanpthakke.empee3.core.sound.dsp.TransitionAutomation

/** The main player as the transition coordinator sees it. All calls on the main thread. */
internal interface TransitionHost {
    /** Identity of the current queue entry (compared with equals): unique per entry, so duplicates of a song differ. */
    val currentEntry: Any?

    fun entryAt(index: Int): Any?

    /** What a tail player needs to play the current entry (a MediaItem in the app). */
    val currentSource: Any?

    val positionMs: Long
    val playWhenReady: Boolean
    val speed: Float
    val volume: Float

    /** The boundary after the current entry, or null when there is no current entry. */
    fun boundary(): BoundaryInfo?

    /** Runs [action] when playback of the current entry reaches [positionMs] (media time). */
    fun scheduleAt(
        positionMs: Long,
        action: () -> Unit,
    ): Cancellable

    /** Runs [action] after [delayMs] of wall-clock time. */
    fun postDelayed(
        delayMs: Long,
        action: () -> Unit,
    ): Cancellable

    /** Moves the main player to queue entry [index] at [positionMs] (a seek; the queue itself is not changed). */
    fun advanceTo(
        index: Int,
        positionMs: Long,
    )
}

/** The incoming track's fade, applied inside the main player's audio pipeline. */
internal interface IncomingFade {
    val hasStarted: Boolean

    fun arm(automation: TransitionAutomation)

    fun cancel()
}

/** Callbacks from a [TailPlayer], on the main thread. */
internal interface TailListener {
    /** The tail's audio is actually advancing (it can be heard). */
    fun onAudible()

    /** The tail played to its end (or its fade is over). */
    fun onFinished()

    fun onError()
}

/**
 * A private, secondary player that plays only the last seconds of the outgoing track, faded out.
 * It never requests audio focus and never appears in the media session; it follows the main
 * player's play/pause, speed and volume.
 */
internal interface TailPlayer {
    val isReady: Boolean

    fun prepare(
        source: Any,
        startMs: Long,
        automation: TransitionAutomation,
        speed: Float,
        volume: Float,
        listener: TailListener,
    )

    fun start()

    fun setPlaying(playing: Boolean)

    fun setVolume(volume: Float)

    fun setSpeed(speed: Float)

    /** Stops at once (with a very short ramp) and frees the player. */
    fun release()
}

/**
 * Crossfades the boundaries of collections that have Smooth Transitions on.
 *
 * How a transition works: shortly before the outgoing track's transition point a [TailPlayer] is
 * prepared on that track at that point. Just before the point it is started; once its audio is
 * audible the main player is moved to the next entry with an [IncomingFade] armed, so the next
 * track rises in the main player while the tail falls in the secondary one. The main player stays
 * the only source of truth for queue, notification, focus and lock-screen controls.
 *
 * Safety: every scheduled callback carries a generation number and the identity of the entry it was
 * planned for, and does nothing when either is stale. A skip, seek, queue edit, repeat or settings
 * change re-plans (cancelling what was scheduled), and a tail that no longer belongs to the current
 * entry is stopped. Any failure falls back to the normal (gapless) boundary.
 */
@Suppress("TooManyFunctions")
internal class TransitionCoordinator(
    private val host: TransitionHost,
    private val incomingFade: IncomingFade,
    private val newTailPlayer: () -> TailPlayer,
) {
    private enum class Phase { WAITING, PREPARED, STARTING, HANDING_OVER }

    private class Pending(
        val generation: Int,
        val entry: Any,
        val nextEntry: Any?,
        val plan: TransitionDecision.Planned,
    ) {
        var phase = Phase.WAITING
        var tail: TailPlayer? = null
        val scheduled = mutableListOf<Cancellable>()
    }

    private class Active(
        val generation: Int,
        val tail: TailPlayer,
        val incomingEntry: Any?,
    )

    private var generation = 0
    private var pending: Pending? = null
    private var active: Active? = null
    private var fadeCheck: Cancellable? = null
    private var pausedRelease: Cancellable? = null

    /** The decision for the current boundary, for tests and diagnostics. */
    var lastDecision: TransitionDecision? = null
        private set

    /** True while a tail is playing (a transition is audible). */
    val isTransitioning: Boolean get() = active != null

    /**
     * Re-plans the boundary after the current entry. Call on every entry change, seek, queue
     * change, repeat or A–B loop change, and settings change. Cheap when nothing changed.
     */
    fun reevaluate() {
        val current = host.currentEntry
        active?.let { if (it.incomingEntry != current) stopActive(cancelFade = true) }
        val decision = host.boundary()?.let(TransitionPlanner::plan) ?: TransitionDecision.None(NoTransitionReason.NOT_CONFIGURED)
        lastDecision = decision
        if (decision is TransitionDecision.Planned && pending?.isFor(current, decision) == true) return
        abortPending()
        if (decision is TransitionDecision.Planned && current != null) schedule(current, decision)
    }

    /** The user seeked within the current entry: what was scheduled may now be in the past. */
    fun onSeek() {
        abortPending()
        reevaluate()
    }

    fun onPlayingChanged(playing: Boolean) {
        active?.tail?.setPlaying(playing)
        // A long pause mid-transition: free the tail rather than hold a decoder; on resume the incoming track just finishes its fade.
        pausedRelease?.cancel()
        pausedRelease = if (!playing && active != null) host.postDelayed(PAUSED_RELEASE_MS) { stopActive(cancelFade = false) } else null
        // Paused at the very moment of the switch: let the boundary play normally.
        if (!playing && (pending?.phase == Phase.STARTING || pending?.phase == Phase.HANDING_OVER)) abortPending()
    }

    fun onVolumeChanged(volume: Float) {
        active?.tail?.setVolume(volume)
        pending?.tail?.setVolume(volume)
    }

    fun onSpeedChanged(speed: Float) {
        active?.tail?.setSpeed(speed)
        pending?.tail?.setSpeed(speed)
    }

    fun release() {
        abortPending()
        stopActive(cancelFade = true)
    }

    private fun schedule(
        entry: Any,
        plan: TransitionDecision.Planned,
    ) {
        val switchAt = plan.startAtMs - START_LEAD_MS
        val position = host.positionMs
        if (position > switchAt - MIN_PREPARE_MS) return // Too close to the end (e.g. seeked there): play normally.
        val item = Pending(++generation, entry, host.entryAt(plan.nextIndex), plan)
        pending = item
        val prepareAt = switchAt - PREPARE_AHEAD_MS
        if (prepareAt <= position) {
            prepare(item)
        } else {
            item.scheduled += host.scheduleAt(prepareAt) { prepare(item) }
        }
        item.scheduled += host.scheduleAt(switchAt) { startTail(item) }
    }

    /** Same entry, same plan, and the same entry still follows: nothing to re-plan. */
    private fun Pending.isFor(
        current: Any?,
        decision: TransitionDecision.Planned,
    ): Boolean = entry == current && plan == decision && nextEntry == host.entryAt(decision.nextIndex)

    private fun isCurrent(item: Pending): Boolean = pending === item && item.generation == generation && host.currentEntry == item.entry

    private fun prepare(item: Pending) {
        if (!isCurrent(item) || item.tail != null) return
        val source = host.currentSource ?: return abortPending()
        val tail = newTailPlayer()
        item.tail = tail
        val automation =
            TransitionAutomation(TransitionRole.OUTGOING, item.plan.spec, item.plan.durationMs, entryRampMs = ENTRY_RAMP_MS)
        tail.prepare(source, item.plan.startAtMs, automation, host.speed, host.volume, listenerFor(item, tail))
        item.phase = Phase.PREPARED
    }

    private fun startTail(item: Pending) {
        if (!isCurrent(item)) return
        val tail = item.tail?.takeIf { item.phase == Phase.PREPARED && it.isReady }
        if (tail == null || !host.playWhenReady) return abortPending()
        item.phase = Phase.STARTING
        tail.start()
        // If the tail does not become audible quickly, give up: the boundary plays normally.
        item.scheduled += host.postDelayed(START_TIMEOUT_MS) { if (pending === item && item.phase == Phase.STARTING) abortPending() }
    }

    private fun onTailAudible(item: Pending) {
        if (!isCurrent(item) || item.phase != Phase.STARTING) return
        // Let the tail's short entry ramp get under way before the main player lets go of the track.
        item.phase = Phase.HANDING_OVER
        item.scheduled += host.postDelayed(HANDOVER_DELAY_MS) { handOver(item) }
    }

    private fun handOver(item: Pending) {
        if (!isCurrent(item) || item.phase != Phase.HANDING_OVER) return
        // The queue changed under us (the planned next entry moved or went): play the boundary normally.
        val tail = item.tail?.takeIf { host.entryAt(item.plan.nextIndex) == item.nextEntry } ?: return abortPending()
        // The tail now carries the outgoing track; the main player moves on with a fade-in.
        item.scheduled.forEach(Cancellable::cancel)
        pending = null
        stopActive(cancelFade = false)
        active = Active(item.generation, tail, item.nextEntry)
        incomingFade.arm(TransitionAutomation(TransitionRole.INCOMING, item.plan.spec, item.plan.durationMs))
        fadeCheck?.cancel()
        fadeCheck = host.postDelayed(FADE_START_TIMEOUT_MS) { if (!incomingFade.hasStarted) incomingFade.cancel() }
        host.advanceTo(item.plan.nextIndex, item.plan.incomingStartMs)
    }

    private fun listenerFor(
        item: Pending,
        tail: TailPlayer,
    ) = object : TailListener {
        override fun onAudible() = onTailAudible(item)

        override fun onFinished() {
            if (active?.tail === tail) {
                stopActive(cancelFade = false)
            } else if (item.tail === tail) {
                abortPending()
            }
        }

        override fun onError() {
            if (active?.tail === tail) {
                stopActive(cancelFade = false)
            } else if (item.tail === tail) {
                abortPending()
            }
        }
    }

    private fun abortPending() {
        val item = pending ?: return
        pending = null
        item.scheduled.forEach(Cancellable::cancel)
        item.tail?.release()
    }

    private fun stopActive(cancelFade: Boolean) {
        val current = active ?: return
        active = null
        current.tail.release()
        if (cancelFade) {
            fadeCheck?.cancel()
            incomingFade.cancel()
        }
    }

    companion object {
        /** The tail is started this long before the transition point, to cover its start-up. */
        const val START_LEAD_MS = 120L

        /** The tail is prepared (buffered, paused) this long before it starts. */
        const val PREPARE_AHEAD_MS = 2_500L

        /** Less time than this to prepare: skip the transition for this boundary. */
        const val MIN_PREPARE_MS = 400L

        /** The tail must be audible within this time after it is started. */
        const val START_TIMEOUT_MS = 600L

        /** The incoming fade must start within this time after the switch, or it is dropped. */
        const val FADE_START_TIMEOUT_MS = 1_500L

        /** A tail paused for longer than this is released. */
        const val PAUSED_RELEASE_MS = 60_000L

        /** Short rise at the start of the tail so the hand-over from the main player does not click. */
        const val ENTRY_RAMP_MS = 40

        /** Time between the tail becoming audible and the main player moving on (half the entry ramp). */
        const val HANDOVER_DELAY_MS = 20L
    }
}
