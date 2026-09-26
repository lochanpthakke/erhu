package com.lochanpthakke.empee3.core.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.lochanpthakke.empee3.core.common.dispatcher.DispatcherProvider
import com.lochanpthakke.empee3.core.common.logging.Logger
import com.lochanpthakke.empee3.core.model.PlaybackState
import com.lochanpthakke.empee3.core.model.PlaybackStatus
import com.lochanpthakke.empee3.core.model.RepeatMode
import com.lochanpthakke.empee3.core.model.Song
import com.lochanpthakke.empee3.core.playback.focus.AudioFocusListener
import com.lochanpthakke.empee3.core.playback.focus.AudioFocusManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Suppress("TooManyFunctions")
class PlaybackControllerImpl
    @Inject
    constructor(
        private val player: Player,
        private val dispatcherProvider: DispatcherProvider,
        private val logger: Logger,
        private val audioFocusManager: AudioFocusManager,
    ) : PlaybackController,
        AudioFocusListener {
        private val scope = CoroutineScope(dispatcherProvider.main)
        private val queue = PlaybackQueue(player)

        private val _playbackState = MutableStateFlow(PlaybackState())
        override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

        private var positionPollJob: Job? = null
        private var resumeAfterFocusGain = false

        private val playerListener =
            object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    updateStatus()
                    if (isPlaying) startPositionPolling() else stopPositionPolling()
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    updateStatus()
                    if (playbackState == Player.STATE_ENDED) {
                        audioFocusManager.abandonFocus()
                    }
                }

                override fun onMediaItemTransition(
                    mediaItem: MediaItem?,
                    reason: Int,
                ) {
                    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                        player.currentMediaItem?.let { queue.pushHistory(it) }
                    }
                    val song = mediaItem?.toSong()
                    val rawDuration = player.duration
                    val validDuration = if (rawDuration > 0) rawDuration else song?.durationMs ?: 0L
                    _playbackState.update {
                        it.copy(
                            currentSong = song,
                            durationMs = validDuration.coerceAtLeast(0L),
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
            audioFocusManager.setListener(this)
        }

        override fun onFocusLost() {
            resumeAfterFocusGain = false
            pause()
        }

        override fun onFocusLostTransient() {
            resumeAfterFocusGain =
                playbackState.value.status == PlaybackStatus.PLAYING
            pause()
        }

        override fun onFocusLostTransientCanDuck() {
            setVolume(DUCK_VOLUME)
        }

        override fun onFocusGained() {
            setVolume(FULL_VOLUME)

            if (resumeAfterFocusGain) {
                resumeAfterFocusGain = false
                play()
            }
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
            val song = player.currentMediaItem?.toSong() ?: _playbackState.value.currentSong
            val rawDuration = player.duration
            val validDuration = if (rawDuration > 0) rawDuration else song?.durationMs ?: _playbackState.value.durationMs
            _playbackState.update {
                it.copy(
                    status = status,
                    currentSong = song,
                    durationMs = validDuration.coerceAtLeast(0L),
                )
            }
            logger.d(TAG, "Playback status: $status, duration: $validDuration")
        }

        private fun startPositionPolling() {
            if (positionPollJob?.isActive == true) return
            positionPollJob =
                scope.launch {
                    while (true) {
                        val song = player.currentMediaItem?.toSong() ?: _playbackState.value.currentSong
                        val rawDuration = player.duration
                        val validDuration = if (rawDuration > 0) rawDuration else song?.durationMs ?: _playbackState.value.durationMs
                        _playbackState.update {
                            it.copy(
                                positionMs = player.currentPosition.coerceAtLeast(0L),
                                durationMs = validDuration.coerceAtLeast(0L),
                                currentSong = song,
                            )
                        }
                        delay(POSITION_POLL_INTERVAL_MS)
                    }
                }
        }

        private fun stopPositionPolling() {
            positionPollJob?.cancel()
            positionPollJob = null
        }

        override fun play() {
            if (audioFocusManager.requestFocus()) {
                if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) {
                    player.prepare()
                }
                player.play()
            } else {
                logger.w(TAG, "Audio focus request denied — not starting playback")
            }
        }

        override fun pause() {
            player.pause()
        }

        override fun playSong(song: Song) {
            player.setMediaItem(song.toMediaItem())
            player.prepare()
            play()
        }

        override fun playSongs(songs: List<Song>) {
            if (songs.isEmpty()) return
            player.setMediaItems(songs.map { it.toMediaItem() })
            player.prepare()
            play()
        }

        override fun playSongsFromQueue(
            songs: List<Song>,
            startIndex: Int,
        ) {
            if (songs.isEmpty()) return
            player.setMediaItems(
                songs.map { it.toMediaItem() },
                startIndex.coerceIn(0, songs.lastIndex),
                0L,
            )
            player.prepare()
            play()
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

        override fun setVolume(volume: Float) {
            player.volume = volume
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
            private const val DUCK_VOLUME = 0.2f
            private const val FULL_VOLUME = 1.0f
        }
    }
