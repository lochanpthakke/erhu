package com.lochanpthakke.empee3.core.playback

import com.lochanpthakke.empee3.core.model.PlaybackState
import com.lochanpthakke.empee3.core.model.RepeatMode
import com.lochanpthakke.empee3.core.model.Song
import kotlinx.coroutines.flow.StateFlow

interface PlaybackController {
    val playbackState: StateFlow<PlaybackState>

    fun play()

    fun pause()

    fun seekTo(positionMs: Long)

    fun skipNext()

    fun skipPrevious()

    fun setShuffleModeEnabled(enabled: Boolean)

    fun setRepeatMode(mode: RepeatMode)

    // Queue ops — delegate to PlaybackQueue internally in the impl.
    // PlaybackRepository calls these; PlaybackQueue itself stays private
    // to PlaybackControllerImpl.
    fun playNext(song: Song)

    fun addToQueue(song: Song)

    fun reorder(
        from: Int,
        to: Int,
    )
}
