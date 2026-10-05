package com.lochanpthakke.empee3.core.playback

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

    fun removeFromQueue(index: Int)

    fun setVolume(volume: Float)

    fun reorder(
        from: Int,
        to: Int,
    )
}
