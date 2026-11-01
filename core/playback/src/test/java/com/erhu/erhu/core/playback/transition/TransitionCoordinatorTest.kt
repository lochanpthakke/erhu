package com.erhu.erhu.core.playback.transition

import com.erhu.erhu.core.model.CollectionRef
import com.erhu.erhu.core.model.RepeatMode
import com.erhu.erhu.core.model.TransitionCurve
import com.erhu.erhu.core.model.GlobalTransitionSettings
import com.erhu.erhu.core.model.QueueOrigin
import com.erhu.erhu.core.model.TrackOccurrence
import com.erhu.erhu.core.model.TransitionChoice
import com.erhu.erhu.core.model.TransitionConfig
import com.erhu.erhu.core.model.TransitionResolver
import com.erhu.erhu.core.model.TransitionRole
import com.erhu.erhu.core.model.TransitionSpec
import com.erhu.erhu.core.playback.Cancellable
import com.erhu.erhu.core.sound.dsp.TransitionAutomation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitionCoordinatorTest {
    private val album = CollectionRef.album(1)
    private val playlist = CollectionRef.playlist(1)

    /** One queue entry: its own uid (duplicates of a song differ), origin and duration. */
    private data class Entry(
        val uid: String,
        val collection: CollectionRef?,
        val durationMs: Long = 100_000,
    ) {
        fun origin() = collection?.let { QueueOrigin(it, TrackOccurrence(uid.hashCode().toLong())) }
    }

    private class Timed(
        val at: Long,
        val action: () -> Unit,
    ) {
        var cancelled = false
        var fired = false
    }

    private inner class FakeHost : TransitionHost {
        val entries = mutableListOf<Entry>()
        var index = 0
        /** Collection settings (Custom or Off); global default off, so ordinary entries never transition. */
        var settings = mutableMapOf<CollectionRef, TransitionChoice>()
        var repeat = RepeatMode.OFF
        var abLoop = false
        override var positionMs = 0L
        override var playWhenReady = true
        override var speed = 1f
        override var volume = 1f
        val scheduled = mutableListOf<Timed>()
        val delayed = mutableListOf<Timed>()
        var advances = 0
        lateinit var coordinator: TransitionCoordinator

        override val currentEntry: Any? get() = entries.getOrNull(index)?.uid

        override fun entryAt(index: Int): Any? = entries.getOrNull(index)?.uid

        override val currentSource: Any? get() = entries.getOrNull(index)

        private fun nextIndex(): Int =
            when {
                index + 1 < entries.size -> index + 1
                repeat == RepeatMode.ALL && entries.isNotEmpty() -> 0
                else -> -1
            }

        override fun boundary(): BoundaryInfo? {
            val current = entries.getOrNull(index) ?: return null
            val next = entries.getOrNull(nextIndex())
            val config = TransitionConfig(GlobalTransitionSettings(true, TransitionChoice.Off), settings.toMap())
            val spec = if (next == null) null else TransitionResolver.resolve(config, current.origin(), next.origin()).spec
            return BoundaryInfo(spec, nextIndex(), repeat, abLoop, current.durationMs, next?.durationMs ?: 0)
        }

        override fun scheduleAt(
            positionMs: Long,
            action: () -> Unit,
        ): Cancellable = Timed(positionMs, action).also { scheduled += it }.let { t -> Cancellable { t.cancelled = true } }

        override fun postDelayed(
            delayMs: Long,
            action: () -> Unit,
        ): Cancellable = Timed(delayMs, action).also { delayed += it }.let { t -> Cancellable { t.cancelled = true } }

        var advancedToPositionMs = -1L

        override fun advanceTo(
            index: Int,
            positionMs: Long,
        ) {
            advances++
            this.index = index
            this.positionMs = positionMs
            advancedToPositionMs = positionMs
            coordinator.reevaluate() // as the player's media-item-transition callback does
        }

        /** Playback reaches [position]: due player messages fire once. */
        fun reach(position: Long) {
            positionMs = position
            scheduled.filter { !it.cancelled && !it.fired && it.at <= position }.forEach {
                it.fired = true
                it.action()
            }
        }

        fun runDelayed() {
            delayed.filter { !it.cancelled && !it.fired }.forEach {
                it.fired = true
                it.action()
            }
        }

        /** The user skips to [target] (a seek in the player). */
        fun userSkipTo(target: Int) {
            index = target
            positionMs = 0
            coordinator.reevaluate()
        }
    }

    private class FakeTail : TailPlayer {
        var source: Any? = null
        var startMs = -1L
        var automation: TransitionAutomation? = null
        var listener: TailListener? = null
        var started = 0
        var playing: Boolean? = null
        var lastVolume = 1f
        var released = false
        var ready = true
        override val isReady: Boolean get() = ready && source != null && !released

        override fun prepare(
            source: Any,
            startMs: Long,
            automation: TransitionAutomation,
            speed: Float,
            volume: Float,
            listener: TailListener,
        ) {
            this.source = source
            this.startMs = startMs
            this.automation = automation
            this.lastVolume = volume
            this.listener = listener
        }

        override fun start() {
            started++
        }

        override fun setPlaying(playing: Boolean) {
            this.playing = playing
        }

        override fun setVolume(volume: Float) {
            lastVolume = volume
        }

        override fun setSpeed(speed: Float) = Unit

        override fun release() {
            released = true
        }
    }

    private class FakeFade : IncomingFade {
        var armed: TransitionAutomation? = null
        var cancels = 0
        override var hasStarted = false

        override fun arm(automation: TransitionAutomation) {
            armed = automation
        }

        override fun cancel() {
            cancels++
            armed = null
        }
    }

    private val host = FakeHost()
    private val fade = FakeFade()
    private val tails = mutableListOf<FakeTail>()
    private val coordinator = TransitionCoordinator(host, fade) { FakeTail().also { tails += it } }.also { host.coordinator = it }

    private val spec = TransitionSpec(durationMs = 2_000, curve = TransitionCurve.EQUAL_POWER)
    private val on = TransitionChoice.Custom(spec)

    // Transition point 98 000; the tail starts 120 ms before it and is prepared 2.5 s before that.
    private val prepareAt = 98_000 - TransitionCoordinator.START_LEAD_MS - TransitionCoordinator.PREPARE_AHEAD_MS
    private val switchAt = 98_000 - TransitionCoordinator.START_LEAD_MS

    private fun albumQueue(vararg uids: String) {
        host.entries += uids.map { Entry(it, album) }
        host.settings[album] = on
        coordinator.reevaluate()
    }

    private fun runTransition() {
        host.reach(prepareAt)
        host.reach(switchAt)
        tails.last().listener!!.onAudible()
        host.runDelayed()
    }

    @Test
    fun `a full transition plays the tail and moves the main player on with a fade-in`() {
        albumQueue("a1", "a2", "a3")
        assertTrue(tails.isEmpty())
        host.reach(prepareAt)
        val tail = tails.single()
        assertEquals(Entry("a1", album), tail.source)
        assertEquals(98_000L, tail.startMs)
        // The tail carries the outgoing curve, the main player the incoming one: two independent automations.
        assertEquals(TransitionRole.OUTGOING, tail.automation!!.role)
        assertEquals(2_000, tail.automation!!.durationMs)
        assertEquals(spec, tail.automation!!.spec)

        host.reach(switchAt)
        assertEquals(1, tail.started)
        assertEquals(0, host.advances) // not before the tail is audible

        tail.listener!!.onAudible()
        host.runDelayed()
        assertEquals(1, host.index)
        assertEquals(TransitionAutomation(TransitionRole.INCOMING, spec, 2_000), fade.armed)
        assertTrue(coordinator.isTransitioning)
        assertFalse(tail.released)

        tail.listener!!.onFinished()
        assertTrue(tail.released)
        assertFalse(coordinator.isTransitioning)
        assertEquals(0, fade.cancels) // the fade-in finishes on its own
    }

    @Test
    fun `nothing happens for ordinary queues, disabled collections or across collections`() {
        host.entries += listOf(Entry("q1", null), Entry("q2", null))
        coordinator.reevaluate()
        assertTrue(host.scheduled.isEmpty())

        host.entries.clear()
        host.entries += listOf(Entry("a1", album), Entry("p1", playlist))
        host.settings[album] = on
        host.settings[playlist] = on
        coordinator.reevaluate()
        assertTrue(host.scheduled.isEmpty())
        assertEquals(TransitionDecision.None(NoTransitionReason.NOT_CONFIGURED), coordinator.lastDecision)

        host.entries[1] = Entry("a2", album)
        host.settings[album] = TransitionChoice.Off
        coordinator.reevaluate()
        assertTrue(host.scheduled.isEmpty())
    }

    @Test
    fun `a skip before the switch cancels the prepared tail and stale callbacks do nothing`() {
        albumQueue("a1", "a2", "a3")
        host.reach(prepareAt)
        val staleSwitch = host.scheduled.first { it.at == switchAt }
        host.userSkipTo(2)
        assertTrue(tails.single().released)
        assertTrue(staleSwitch.cancelled)
        // Even if a stale message still arrived, its generation and entry no longer match.
        staleSwitch.action()
        assertEquals(0, tails.single().started)
        assertEquals(0, host.advances)
    }

    @Test
    fun `rapid skips never start the same incoming track twice`() {
        albumQueue("a1", "a2", "a3")
        host.reach(prepareAt)
        host.reach(switchAt)
        val tail = tails.single()
        tail.listener!!.onAudible()
        tail.listener!!.onAudible()
        host.runDelayed()
        host.runDelayed()
        assertEquals(1, host.advances)
        assertEquals(1, tail.started)
    }

    @Test
    fun `skipping during the tail stops it and drops the fade-in`() {
        albumQueue("a1", "a2", "a3")
        runTransition()
        val tail = tails.single()
        host.userSkipTo(2)
        assertTrue(tail.released)
        assertEquals(1, fade.cancels)
        assertFalse(coordinator.isTransitioning)
    }

    @Test
    fun `pause and resume during the tail are followed, volume too`() {
        albumQueue("a1", "a2")
        runTransition()
        val tail = tails.single()
        coordinator.onPlayingChanged(false)
        assertEquals(false, tail.playing)
        coordinator.onPlayingChanged(true)
        assertEquals(true, tail.playing)
        coordinator.onVolumeChanged(0.4f)
        assertEquals(0.4f, tail.lastVolume)
    }

    @Test
    fun `a seek close to the end plays the boundary normally, a seek back re-plans once`() {
        albumQueue("a1", "a2")
        host.positionMs = 97_700
        coordinator.onSeek()
        host.reach(99_000)
        assertTrue(tails.isEmpty())
        assertEquals(0, host.advances)

        host.positionMs = 10_000
        coordinator.onSeek()
        assertEquals(2, host.scheduled.count { !it.cancelled })
        host.reach(prepareAt)
        assertEquals(1, tails.size)
    }

    @Test
    fun `a paused track resumed near its end prepares at once if there is still time`() {
        albumQueue("a1", "a2")
        host.positionMs = 96_000 // past the prepare point, before the switch
        coordinator.onSeek()
        assertEquals(1, tails.size)
        host.reach(switchAt)
        assertEquals(1, tails.single().started)
    }

    @Test
    fun `a tail that is not ready, never audible or fails falls back to the normal boundary`() {
        albumQueue("a1", "a2")
        host.reach(prepareAt)
        tails.single().ready = false
        host.reach(switchAt)
        assertEquals(0, tails.single().started)
        assertTrue(tails.single().released)

        host.userSkipTo(0)
        host.reach(prepareAt)
        host.reach(switchAt)
        host.runDelayed() // start timeout passes without the tail becoming audible
        assertTrue(tails[1].released)
        assertEquals(0, host.advances)

        host.userSkipTo(0)
        host.reach(prepareAt)
        tails[2].listener!!.onError()
        assertTrue(tails[2].released)
        host.reach(switchAt)
        assertEquals(0, tails[2].started)
        assertEquals(0, host.advances)
    }

    @Test
    fun `removing or replacing the next entry re-plans, replacing the queue leaves the context`() {
        albumQueue("a1", "a2", "a3")
        host.reach(prepareAt)
        host.entries.removeAt(1) // the upcoming track is removed: a3 is next now
        coordinator.reevaluate()
        assertTrue(tails[0].released)
        assertEquals(Entry("a3", album).uid, host.entryAt(1))

        // The queue is replaced by a playlist without transitions: nothing stays scheduled.
        host.entries.clear()
        host.entries += listOf(Entry("p1", playlist), Entry("p2", playlist))
        host.index = 0
        coordinator.reevaluate()
        assertTrue(host.scheduled.none { !it.cancelled && !it.fired })
    }

    @Test
    fun `the same song twice in a collection is two entries and transitions normally`() {
        host.entries += listOf(Entry("song5#1", album), Entry("song5#2", album))
        host.settings[album] = on
        coordinator.reevaluate()
        runTransition()
        assertEquals(1, host.index)
        assertNull(host.boundary()!!.spec) // last entry: nothing further
        assertTrue(host.scheduled.none { !it.cancelled && !it.fired })
    }

    @Test
    fun `the last track of a collection ends normally, repeat all wraps within it`() {
        albumQueue("a1", "a2")
        host.userSkipTo(1)
        assertTrue(host.scheduled.none { !it.cancelled && !it.fired })
        host.repeat = RepeatMode.ALL
        coordinator.reevaluate()
        runTransition()
        assertEquals(0, host.index)
    }

    @Test
    fun `turning transitions off before the switch cancels the plan`() {
        albumQueue("a1", "a2")
        host.reach(prepareAt)
        host.settings[album] = TransitionChoice.Off
        coordinator.reevaluate()
        assertTrue(tails.single().released)
        host.reach(switchAt)
        assertEquals(0, host.advances)
    }

    @Test
    fun `a long pause mid-transition frees the tail, a short one keeps it`() {
        albumQueue("a1", "a2", "a3")
        runTransition()
        fade.hasStarted = true // the main player's flush arrived
        val tail = tails.single()
        coordinator.onPlayingChanged(false)
        coordinator.onPlayingChanged(true)
        host.runDelayed() // the cancelled release does nothing
        assertFalse(tail.released)
        coordinator.onPlayingChanged(false)
        host.runDelayed() // 60 s later, still paused
        assertTrue(tail.released)
        assertEquals(0, fade.cancels) // the incoming track finishes its own fade
    }

    @Test
    fun `release on stop or service shutdown frees everything`() {
        albumQueue("a1", "a2", "a3")
        runTransition()
        coordinator.release()
        assertTrue(tails.all { it.released })
        assertFalse(coordinator.isTransitioning)
    }

    @Test
    fun `cue points move the hand-over earlier and start the next track at its cue`() {
        host.entries += listOf(Entry("a1", album), Entry("a2", album))
        host.settings[album] = TransitionChoice.Custom(spec.copy(outgoingCutMs = 10_000, incomingStartMs = 7_000))
        coordinator.reevaluate()
        // Transition ends at 90 s (10 s cut from 100 s), so it starts at 88 s.
        val prepare = 88_000 - TransitionCoordinator.START_LEAD_MS - TransitionCoordinator.PREPARE_AHEAD_MS
        host.reach(prepare)
        assertEquals(88_000L, tails.single().startMs)
        host.reach(88_000 - TransitionCoordinator.START_LEAD_MS)
        tails.single().listener!!.onAudible()
        host.runDelayed()
        assertEquals(1, host.index)
        assertEquals(7_000L, host.advancedToPositionMs)
    }
}
