package com.lochanpthakke.empee3.core.playback.repository

import com.lochanpthakke.empee3.core.common.result.AppError
import com.lochanpthakke.empee3.core.common.result.AppResult
import com.lochanpthakke.empee3.core.common.result.appResultOf
import com.lochanpthakke.empee3.core.model.PlaybackState
import com.lochanpthakke.empee3.core.model.RepeatMode
import com.lochanpthakke.empee3.core.model.Song
import com.lochanpthakke.empee3.core.playback.PlaybackController
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Thin delegation over [PlaybackController]. No playback logic lives here —
 * this only adapts PlaybackController's calls into the domain-facing
 * PlaybackRepository contract, AppResult-wrapping the operations with a
 * real failure mode (see PlaybackRepository doc comment).
 */
class PlaybackRepositoryImpl
    @Inject
    constructor(
        private val playbackController: PlaybackController,
    ) : PlaybackRepository {
        override val playbackState: StateFlow<PlaybackState>
            get() = playbackController.playbackState

        override fun play() = playbackController.play()

        override fun pause() = playbackController.pause()

        override fun playSong(song: Song) = playbackController.playSong(song)

        override fun playSongs(songs: List<Song>) = playbackController.playSongs(songs)

        override fun playSongsFromQueue(
            songs: List<Song>,
            startIndex: Int,
        ) = playbackController.playSongsFromQueue(songs, startIndex)

        override fun seekTo(positionMs: Long) = playbackController.seekTo(positionMs)

        override fun skipNext() = playbackController.skipNext()

        override fun skipPrevious() = playbackController.skipPrevious()

        override fun skipToQueueItem(index: Int) = playbackController.skipToQueueItem(index)

        override fun setShuffleModeEnabled(enabled: Boolean) = playbackController.setShuffleModeEnabled(enabled)

        override fun setRepeatMode(repeatMode: RepeatMode) = playbackController.setRepeatMode(repeatMode)

        override fun setVolume(volume: Float) = playbackController.setVolume(volume)

        override suspend fun playNext(song: Song): AppResult<Unit> =
            appResultOf(mapError = { AppError.ValidationError(it.message ?: "Failed to queue song next") }) {
                playbackController.playNext(song)
            }

        override suspend fun addToQueue(song: Song): AppResult<Unit> =
            appResultOf(mapError = { AppError.ValidationError(it.message ?: "Failed to add song to queue") }) {
                playbackController.addToQueue(song)
            }

        override suspend fun removeFromQueue(index: Int): AppResult<Unit> =
            appResultOf(mapError = { AppError.ValidationError(it.message ?: "Invalid queue index") }) {
                playbackController.removeFromQueue(index)
            }

        override suspend fun reorder(
            from: Int,
            to: Int,
        ): AppResult<Unit> =
            appResultOf(mapError = { AppError.ValidationError(it.message ?: "Invalid reorder indices") }) {
                playbackController.reorder(from, to)
            }
    }
