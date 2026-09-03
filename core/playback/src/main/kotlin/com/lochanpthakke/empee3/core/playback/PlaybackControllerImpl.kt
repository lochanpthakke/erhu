package com.lochanpthakke.empee3.core.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.lochanpthakke.empee3.core.common.dispatcher.DispatcherProvider
import com.lochanpthakke.empee3.core.common.logging.Logger
import com.lochanpthakke.empee3.core.model.PlaybackState
import com.lochanpthakke.empee3.core.model.PlaybackStatus
import com.lochanpthakke.empee3.core.model.RepeatMode
import com.lochanpthakke.empee3.core.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

class PlaybackControllerImpl
    @Inject
    constructor(
        private val player: Player,
        private val dispatcherProvider: DispatcherProvider,
        private val logger: Logger,
    ) : PlaybackController {
        private val scope = CoroutineScope(dispatcherProvider.main)
        private val queue = PlaybackQueue(player)

        private val _playbackState = MutableStateFlow(PlaybackState())
        override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

        private var positionPollJob: Job? = null

        private val playerListener =
            object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    updateStatus()
                    if (isPlaying) startPositionPolling() else stopPositionPolling()
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    updateStatus()
                }

                override fun onMediaItemTransition(
                    mediaItem: MediaItem?,
                    reason: Int,
                ) {
                    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                        player.currentMediaItem?.let { queue.pushHistory(it) }
                    }
                    _playbackState.update {
                        it.copy(
                            currentSong = mediaItem?.toSong(),
                            durationMs = player.duration.coerceAtLeast(0L),
                            positionMs = 0L,
                        )
                    }
                }

                override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                    _playbackState.update { it.copy(shuffleModeEnabled = shuffleModeEnabled) }
                }

                override fun onRepeatModeChanged(repeatMode: Int) {
                    _playbackState.update { it.copy(repeatMode = repeatMode.toDomainRepeatMode()) }
                }
            }

        init {
            player.addListener(playerListener)
        }

        private fun updateStatus() {
            val status =
                when {
                    player.playbackState == Player.STATE_BUFFERING -> PlaybackStatus.BUFFERING
                    player.playbackState == Player.STATE_IDLE -> PlaybackStatus.IDLE
                    player.playbackState == Player.STATE_ENDED -> PlaybackStatus.IDLE
                    player.isPlaying -> PlaybackStatus.PLAYING
                    else -> PlaybackStatus.PAUSED
                }
            _playbackState.update { it.copy(status = status) }
            logger.d(TAG, "Playback status: $status")
        }

        private fun startPositionPolling() {
            if (positionPollJob?.isActive == true) return
            positionPollJob =
                scope.launch {
                    while (true) {
                        _playbackState.update { it.copy(positionMs = player.currentPosition) }
                        delay(POSITION_POLL_INTERVAL_MS)
                    }
                }
        }

        private fun stopPositionPolling() {
            positionPollJob?.cancel()
            positionPollJob = null
        }

        override fun play() {
            player.play()
        }

        override fun pause() {
            player.pause()
        }

        override fun seekTo(positionMs: Long) {
            player.seekTo(positionMs)
            _playbackState.update { it.copy(positionMs = positionMs) }
        }

        override fun skipNext() {
            player.seekToNext()
        }

        override fun skipPrevious() {
            val historyItem = queue.popHistory()
            if (historyItem != null && player.shuffleModeEnabled) {
                player.setMediaItem(historyItem)
                player.prepare()
                player.play()
            } else {
                player.seekToPrevious()
            }
        }

        override fun setShuffleModeEnabled(enabled: Boolean) {
            player.shuffleModeEnabled = enabled
            if (!enabled) queue.clearHistory()
        }

        override fun setRepeatMode(mode: RepeatMode) {
            player.repeatMode = mode.toPlayerRepeatMode()
        }

        override fun playNext(song: Song) {
            queue.playNext(song.toMediaItem())
        }

        override fun addToQueue(song: Song) {
            queue.addToQueue(song.toMediaItem())
        }

        override fun reorder(
            from: Int,
            to: Int,
        ) {
            queue.reorder(from, to)
        }

        companion object {
            private const val TAG = "PlaybackController"
            private const val POSITION_POLL_INTERVAL_MS = 150L
        }
    }
