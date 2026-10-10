package com.lochanpthakke.empee3.core.playback

import com.lochanpthakke.empee3.core.model.AbLoopEdit
import com.lochanpthakke.empee3.core.model.PlaybackState
import com.lochanpthakke.empee3.core.model.RepeatMode
import com.lochanpthakke.empee3.core.model.Song
import kotlinx.coroutines.flow.StateFlow

@Suppress("TooManyFunctions")
interface PlaybackController {
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

    fun setVolume(volume: Float)

    fun reorder(
        from: Int,
        to: Int,
    )
}
