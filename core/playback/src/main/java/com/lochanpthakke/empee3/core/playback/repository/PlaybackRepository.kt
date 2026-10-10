package com.lochanpthakke.empee3.core.playback.repository

import com.lochanpthakke.empee3.core.common.result.AppResult
import com.lochanpthakke.empee3.core.model.AbLoopEdit
import com.lochanpthakke.empee3.core.model.PlaybackState
import com.lochanpthakke.empee3.core.model.RepeatMode
import com.lochanpthakke.empee3.core.model.Song
import kotlinx.coroutines.flow.StateFlow

/**
 * Domain-facing playback contract. The boundary ViewModels build against —
 * never [com.lochanpthakke.empee3.core.playback.PlaybackController] directly.
 *
 * Thin delegation over PlaybackController: no new playback logic lives here.
 * AppResult wrapping applies only to operations with a real failure mode
 * (malformed song URI, invalid reorder indices) — fire-and-forget transport
 * commands (play/pause/skip/etc.) stay plain, since there's no meaningful
 * failure branch a caller would act on.
 */
interface PlaybackRepository {
    val playbackState: StateFlow<PlaybackState>

    fun play()

    fun pause()

    fun playSong(song: Song)

    fun playSongs(songs: List<Song>)

    fun playSongsFromQueue(
        songs: List<Song>,
        startIndex: Int,
    )

    fun seekTo(positionMs: Long)

    fun skipNext()

    fun skipPrevious()

    /** The previous song in play order, never a restart of the current one (the track swipe). */
    fun skipToPreviousItem()

    fun skipToQueueItem(index: Int)

    fun setShuffleModeEnabled(enabled: Boolean)

    fun setRepeatMode(repeatMode: RepeatMode)

    fun setVolume(volume: Float)

    suspend fun playNext(song: Song): AppResult<Unit>

    suspend fun addToQueue(song: Song): AppResult<Unit>

    /** Removes the entry at [index]; [undoQueueRemoval] puts it back. */
    suspend fun removeFromQueue(index: Int): AppResult<Unit>

    /** Restores the most recently removed queue entry at its original position; false if none. */
    fun undoQueueRemoval(): Boolean

    /** Shuffles only the upcoming entries (after the current one); false when fewer than two are upcoming. */
    fun shuffleUpcoming(): Boolean

    /** Plays a session-history entry again, right after the current entry. */
    fun playFromHistory(entryId: Long)

    /** A–B loop of the current track: A / B at the playback position, on / off, clear. */
    fun setLoopStart(): AbLoopEdit

    fun setLoopEnd(): AbLoopEdit

    fun setLoopEnabled(enabled: Boolean): AbLoopEdit

    fun clearLoop()

    /** The player's exact media position right now (call on the main thread). Read-only. */
    fun currentPositionMs(): Long

    suspend fun reorder(
        from: Int,
        to: Int,
    ): AppResult<Unit>
}
