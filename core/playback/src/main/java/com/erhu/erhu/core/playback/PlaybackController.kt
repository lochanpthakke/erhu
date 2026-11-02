package com.erhu.erhu.core.playback

import com.erhu.erhu.core.model.AbLoopEdit
import com.erhu.erhu.core.model.CollectionRef
import com.erhu.erhu.core.model.PlaybackState
import com.erhu.erhu.core.model.RepeatMode
import com.erhu.erhu.core.model.Song
import kotlinx.coroutines.flow.StateFlow

@Suppress("TooManyFunctions")
interface PlaybackController {
    val playbackState: StateFlow<PlaybackState>

    /** Suspends until saved queue restoration has completed; safe for cold-start widget actions. */
    suspend fun awaitRestored() = Unit

    /** User-initiated widget playback must go through Media3's foreground-service command path. */
    suspend fun playFromWidget() = play()

    fun play()

    fun pause()

    fun playSong(song: Song)

    fun playSongs(songs: List<Song>)

    fun playSongsFromQueue(
        songs: List<Song>,
        startIndex: Int,
    )

    /**
     * Like [playSongsFromQueue], with every entry marked as started from [origin] (an album or
     * playlist), so that collection's transition settings apply to it. Null: an ordinary queue.
     */
    fun playCollection(
        songs: List<Song>,
        startIndex: Int,
        origin: CollectionRef?,
        /** Playlist positions of [songs] (parallel), so duplicates stay distinct; null for albums. */
        slots: List<Int>? = null,
    )

    fun seekTo(positionMs: Long)

    fun skipNext()

    fun skipPrevious()

    /** The previous song in play order, never a restart of the current one (the track swipe). */
    fun skipToPreviousItem()

    /** Jumps to [index] in the current queue (playback-list order). */
    fun skipToQueueItem(index: Int)

    fun setShuffleModeEnabled(enabled: Boolean)

    fun setRepeatMode(mode: RepeatMode)

    fun playNext(song: Song)

    fun addToQueue(song: Song)

    /** Removes the entry at [index]; it can be put back with [undoQueueRemoval]. */
    fun removeFromQueue(index: Int)

    /**
     * Puts the most recently removed entry back at its original position (newest removal first).
     * Returns false when there is nothing to undo (or the queue has been replaced since).
     */
    fun undoQueueRemoval(): Boolean

    /** Shuffles only the entries after the current one. Returns false when fewer than two are upcoming. */
    fun shuffleUpcoming(): Boolean

    /** Replaces the entries after the current one with [songs]; the current song keeps playing. */
    fun replaceUpcoming(songs: List<Song>)

    /**
     * Plays the song of session-history entry [entryId] again, inserted right after the current
     * entry, so the upcoming queue is kept. No-op when the entry is unknown.
     */
    fun playFromHistory(entryId: Long)

    /** Sets loop point A of the current track to the playback position. */
    fun setLoopStart(): AbLoopEdit

    /** Sets loop point B of the current track to the playback position. */
    fun setLoopEnd(): AbLoopEdit

    /** Turns A–B looping on (needs A before B) or off; the markers stay. */
    fun setLoopEnabled(enabled: Boolean): AbLoopEdit

    /** Removes both markers and stops looping. */
    fun clearLoop()

    /** The player's exact media position right now (main thread), finer than [playbackState]'s periodic position. */
    fun currentPositionMs(): Long

    fun setVolume(volume: Float)

    fun reorder(
        from: Int,
        to: Int,
    )
}
