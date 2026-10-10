package com.lochanpthakke.empee3.core.playback

import com.lochanpthakke.empee3.core.model.AbLoopEdit
import com.lochanpthakke.empee3.core.model.AbLoopState

/** Something scheduled that can be called off. */
internal fun interface Cancellable {
    fun cancel()
}

/**
 * Calls [onReached] whenever playback of queue entry [mediaItemIndex] reaches [positionMs], every
 * time it does (also after seeking back), on the player's thread, until cancelled. Driven by the
 * media position, not wall-clock time, so playback speed does not matter.
 */
internal fun interface LoopScheduler {
    fun scheduleAt(
        mediaItemIndex: Int,
        positionMs: Long,
        onReached: () -> Unit,
    ): Cancellable
}

/** The player as the loop sees it. */
internal interface LoopPlayer {
    val currentSongId: Long?
    val currentIndex: Int
    val positionMs: Long
    val durationMs: Long

    fun seekTo(positionMs: Long)
}

/**
 * A–B loop of the current track. A and B are set from the playback position; the loop can only be
 * enabled with A at least [MIN_LOOP_MS] before B, which also rules out a rapid seek loop. While
 * enabled, reaching B seeks back to A (no restart of the track, no player re-preparation); the
 * schedule lives in the playback layer, so it keeps working with the screen closed or off.
 *
 * Any change of the current queue entry other than a repeat of the same entry clears the loop:
 * stale markers never loop another track. Every scheduled callback carries a generation number and
 * checks it, the enabled flag and the current song before acting.
 */
internal class AbLoopController(
    private val scheduler: LoopScheduler?,
    private val player: LoopPlayer,
    private val onStateChanged: (AbLoopState?) -> Unit,
) {
    var state: AbLoopState? = null
        private set

    private var scheduled: Cancellable? = null
    private var generation = 0

    fun setStart(): AbLoopEdit {
        val base = currentBase()
        val position = player.positionMs.coerceAtLeast(0L)
        return when {
            base == null -> AbLoopEdit.NO_TRACK
            !isValidRange(position, base.endMs) -> AbLoopEdit.A_NOT_BEFORE_B
            else -> apply(base.copy(startMs = position))
        }
    }

    fun setEnd(): AbLoopEdit {
        val base = currentBase()
        val duration = player.durationMs
        // B must come before the very end, or the track could finish before the loop returns.
        val position = if (duration > 0) player.positionMs.coerceAtMost(duration - END_GUARD_MS) else player.positionMs
        return when {
            base == null -> AbLoopEdit.NO_TRACK
            position <= 0L || !isValidRange(base.startMs, position) -> AbLoopEdit.A_NOT_BEFORE_B
            else -> apply(base.copy(endMs = position))
        }
    }

    fun setEnabled(enabled: Boolean): AbLoopEdit {
        val current = state
        return when {
            !enabled -> {
                if (current != null) apply(current.copy(enabled = false))
                AbLoopEdit.OK
            }
            current == null || current.songId != player.currentSongId -> AbLoopEdit.NOT_READY
            !current.isComplete || !isValidRange(current.startMs, current.endMs) -> AbLoopEdit.NOT_READY
            scheduler == null -> AbLoopEdit.UNSUPPORTED
            else -> {
                val result = apply(current.copy(enabled = true))
                // Already past B: start the loop at A instead of waiting for the next pass.
                if (player.positionMs >= current.endMs!!) player.seekTo(current.startMs!!)
                result
            }
        }
    }

    fun clear() {
        if (state == null) return
        cancelSchedule()
        state = null
        onStateChanged(null)
    }

    /** The current queue entry changed; [sameEntryRepeated]: the same entry started again (repeat one). */
    fun onEntryChanged(sameEntryRepeated: Boolean) {
        if (!sameEntryRepeated) clear()
    }

    private fun currentBase(): AbLoopState? {
        val songId = player.currentSongId ?: return null
        return state?.takeIf { it.songId == songId } ?: AbLoopState(songId)
    }

    private fun apply(next: AbLoopState): AbLoopEdit {
        cancelSchedule()
        val start = next.startMs
        val end = next.endMs
        state = next
        val activeScheduler = scheduler?.takeIf { next.enabled }
        if (activeScheduler != null && start != null && end != null) {
            val mine = generation
            val songId = next.songId
            scheduled =
                activeScheduler.scheduleAt(player.currentIndex, end) {
                    if (isLive(mine, songId)) player.seekTo(start)
                }
        }
        onStateChanged(next)
        return AbLoopEdit.OK
    }

    private fun cancelSchedule() {
        generation++
        scheduled?.cancel()
        scheduled = null
    }

    /** A scheduled callback still belongs to the loop that is on, for the track that is playing. */
    private fun isLive(
        mine: Int,
        songId: Long,
    ): Boolean {
        val sameRun = mine == generation && player.currentSongId == songId
        val live = state
        val stillOn = live?.enabled == true && live.songId == songId
        return sameRun && stillOn
    }

    /** A at least [MIN_LOOP_MS] before B; true while either is still unset. */
    private fun isValidRange(
        start: Long?,
        end: Long?,
    ): Boolean = start == null || end == null || end - start >= MIN_LOOP_MS

    companion object {
        /** Shortest loop: also the guard against seeking back and forth in a tight loop. */
        const val MIN_LOOP_MS = 1_000L

        /** B stays at least this far before the end of the track. */
        const val END_GUARD_MS = 250L
    }
}
