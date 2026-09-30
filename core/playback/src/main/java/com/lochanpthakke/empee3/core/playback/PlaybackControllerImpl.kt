package com.lochanpthakke.empee3.core.playback

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import com.lochanpthakke.empee3.core.common.dispatcher.DispatcherProvider
import com.lochanpthakke.empee3.core.common.logging.Logger
import com.lochanpthakke.empee3.core.common.result.onError
import com.lochanpthakke.empee3.core.datastore.PlaybackStatePersistence
import com.lochanpthakke.empee3.core.model.PersistedPlaybackState
import com.lochanpthakke.empee3.core.model.PlaybackState
import com.lochanpthakke.empee3.core.model.PlaybackStatus
import com.lochanpthakke.empee3.core.model.RepeatMode
import com.lochanpthakke.empee3.core.model.Song
import com.lochanpthakke.empee3.core.model.SongLookup
import com.lochanpthakke.empee3.core.playback.service.PlaybackServiceConnector
import com.lochanpthakke.empee3.core.sound.repository.SoundRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the shared ExoPlayer, which is also the player behind the
 * MediaLibrarySession in [com.lochanpthakke.empee3.core.playback.service.MusicPlaybackService],
 * so notification / lock-screen / Bluetooth commands and in-app commands act on
 * the same state. [playbackState] is derived from player callbacks only.
 *
 * Audio focus and becoming-noisy handling are delegated to ExoPlayer (see
 * PlayerProviderModule) so they also apply to commands that bypass this class.
 */
@Suppress("TooManyFunctions")
class PlaybackControllerImpl
    @Inject
    constructor(
        private val player: Player,
        dispatcherProvider: DispatcherProvider,
        private val logger: Logger,
        private val serviceConnector: PlaybackServiceConnector,
        private val persistence: PlaybackStatePersistence,
        private val songLookup: SongLookup,
        private val soundRepository: SoundRepository,
    ) : PlaybackController {
        private val scope = CoroutineScope(SupervisorJob() + dispatcherProvider.main)
        private val queue = PlaybackQueue(player)

        private val _playbackState = MutableStateFlow(PlaybackState())
        override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

        private var positionPollJob: Job? = null

        /** Persisting before the restore finished would overwrite the saved queue with an empty one. */
        private var restoreFinished = false
        private var consecutiveErrors = 0

        private val playerListener =
            object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    updateStatus()
                    if (isPlaying) {
                        consecutiveErrors = 0
                        startPositionPolling()
                    } else {
                        stopPositionPolling()
                        refreshPosition()
                        persistState()
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    updateStatus()
                }

                override fun onMediaItemTransition(
                    mediaItem: MediaItem?,
                    reason: Int,
                ) {
                    refreshQueueAndCurrent()
                    _playbackState.update { it.copy(positionMs = player.currentPosition.coerceAtLeast(0L)) }
                    persistState()
                }

                override fun onTimelineChanged(
                    timeline: Timeline,
                    reason: Int,
                ) {
                    if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
                        refreshQueueAndCurrent()
                        persistState()
                    }
                }

                override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                    _playbackState.update { it.copy(shuffleModeEnabled = shuffleModeEnabled) }
                    persistState()
                }

                override fun onRepeatModeChanged(repeatMode: Int) {
                    _playbackState.update { it.copy(repeatMode = repeatMode.toDomainRepeatMode()) }
                    persistState()
                }

                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int,
                ) {
                    refreshPosition()
                }

                override fun onAudioSessionIdChanged(audioSessionId: Int) {
                    soundRepository.attachAudioSession(audioSessionId)
                }

                override fun onPlayerError(error: PlaybackException) {
                    handlePlayerError(error)
                }
            }

        init {
            player.addListener(playerListener)
            (player as? ExoPlayer)?.audioSessionId?.takeIf { it != C.AUDIO_SESSION_ID_UNSET }?.let {
                soundRepository.attachAudioSession(it)
            }
            scope.launch { restorePersistedState() }
        }

        private fun updateStatus() {
            val status =
                when {
                    player.isPlaying -> PlaybackStatus.PLAYING
                    player.playbackState == Player.STATE_BUFFERING && player.playWhenReady -> PlaybackStatus.BUFFERING
                    player.playbackState == Player.STATE_IDLE && player.mediaItemCount == 0 -> PlaybackStatus.IDLE
                    player.playbackState == Player.STATE_ENDED -> PlaybackStatus.IDLE
                    else -> PlaybackStatus.PAUSED
                }
            _playbackState.update {
                it.copy(
                    status = status,
                    durationMs = resolveDuration(it.currentSong),
                )
            }
        }

        private fun refreshQueueAndCurrent() {
            val items = List(player.mediaItemCount) { index -> player.getMediaItemAt(index) }
            val songs = items.mapNotNull { it.toSongOrNull() }
            val currentIndex = if (songs.isEmpty()) -1 else player.currentMediaItemIndex.coerceIn(0, songs.lastIndex)
            val current = player.currentMediaItem?.toSongOrNull()
            _playbackState.update {
                it.copy(
                    queue = songs,
                    currentIndex = currentIndex,
                    currentSong = current,
                    durationMs = resolveDuration(current),
                    positionMs = if (current == null) 0L else it.positionMs,
                )
            }
            if (current == null) updateStatus()
        }

        private fun resolveDuration(song: Song?): Long {
            val rawDuration = player.duration
            val duration = if (rawDuration != C.TIME_UNSET && rawDuration > 0) rawDuration else song?.durationMs ?: 0L
            return duration.coerceAtLeast(0L)
        }

        private fun refreshPosition() {
            _playbackState.update {
                it.copy(
                    positionMs = player.currentPosition.coerceAtLeast(0L),
                    durationMs = resolveDuration(it.currentSong),
                )
            }
        }

        private fun startPositionPolling() {
            if (positionPollJob?.isActive == true) return
            positionPollJob =
                scope.launch {
                    var ticks = 0
                    while (true) {
                        refreshPosition()
                        ticks++
                        if (ticks % PERSIST_EVERY_N_POLLS == 0) persistState()
                        delay(POSITION_POLL_INTERVAL_MS)
                    }
                }
        }

        private fun stopPositionPolling() {
            positionPollJob?.cancel()
            positionPollJob = null
        }

        /**
         * A file that was deleted, moved or is unreadable must not stall the
         * queue: skip to the next item, but give up after one full pass so a
         * queue made entirely of broken files cannot loop forever.
         */
        private fun handlePlayerError(error: PlaybackException) {
            val failedId = player.currentMediaItem?.mediaId
            logger.e(TAG, "Playback error for media $failedId: ${error.errorCodeName}", error)
            consecutiveErrors++
            if (consecutiveErrors < player.mediaItemCount && player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
                player.prepare()
            } else {
                consecutiveErrors = 0
                player.stop()
                updateStatus()
            }
        }

        private suspend fun restorePersistedState() {
            try {
                if (player.mediaItemCount > 0) return
                val saved = persistence.observe().first()
                val restored = buildRestoredQueue(saved, songLookup.getSongsByIds(saved.queueSongIds.distinct()))
                // The user may have started playback while we were reading from disk.
                if (restored == null || player.mediaItemCount > 0) return
                player.setMediaItems(
                    restored.songs.map { it.toMediaItem() },
                    restored.startIndex,
                    restored.startPositionMs,
                )
                player.shuffleModeEnabled = saved.shuffleModeEnabled
                player.repeatMode = saved.repeatMode.toPlayerRepeatMode()
                player.prepare()
                refreshQueueAndCurrent()
                refreshPosition()
                logger.d(TAG, "Restored queue of ${restored.songs.size} songs at index ${restored.startIndex}")
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                logger.w(TAG, "Could not restore playback state: ${e.message}", e)
            } finally {
                restoreFinished = true
            }
        }

        private fun persistState() {
            if (!restoreFinished) return
            val snapshot =
                PersistedPlaybackState(
                    queueSongIds = List(player.mediaItemCount) { player.getMediaItemAt(it).mediaId.toLongOrNull() ?: -1L },
                    currentIndex = player.currentMediaItemIndex,
                    positionMs = player.currentPosition.coerceAtLeast(0L),
                    shuffleModeEnabled = player.shuffleModeEnabled,
                    repeatMode = player.repeatMode.toDomainRepeatMode(),
                )
            scope.launch {
                persistence.save(snapshot).onError { logger.w(TAG, "Failed to persist playback state: ${it.message}") }
            }
        }

        override fun play() {
            serviceConnector.ensureConnected()
            if (player.mediaItemCount == 0) return
            when (player.playbackState) {
                Player.STATE_IDLE -> player.prepare()
                Player.STATE_ENDED -> player.seekToDefaultPosition()
                else -> Unit
            }
            player.play()
        }

        override fun pause() {
            player.pause()
        }

        override fun playSong(song: Song) {
            playSongsFromQueue(listOf(song), 0)
        }

        override fun playSongs(songs: List<Song>) {
            playSongsFromQueue(songs, 0)
        }

        override fun playSongsFromQueue(
            songs: List<Song>,
            startIndex: Int,
        ) {
            val playable = songs.filter { it.uri.isNotBlank() }
            if (playable.isEmpty()) {
                logger.w(TAG, "Ignoring request to play an empty queue")
                return
            }
            val requested = songs.getOrNull(startIndex)
            val index = playable.indexOf(requested).takeIf { it >= 0 } ?: 0
            consecutiveErrors = 0
            player.setMediaItems(playable.map { it.toMediaItem() }, index, 0L)
            player.prepare()
            play()
        }

        override fun seekTo(positionMs: Long) {
            if (player.mediaItemCount == 0) return
            val target = positionMs.coerceAtLeast(0L)
            player.seekTo(target)
            _playbackState.update { it.copy(positionMs = target) }
        }

        override fun skipNext() {
            if (player.hasNextMediaItem()) player.seekToNextMediaItem()
        }

        override fun skipPrevious() {
            // Restarts the current song past the first few seconds, otherwise moves
            // to the previous item — ExoPlayer follows the shuffle order itself.
            player.seekToPrevious()
        }

        override fun skipToQueueItem(index: Int) {
            if (index !in 0 until player.mediaItemCount) return
            player.seekToDefaultPosition(index)
            play()
        }

        override fun setShuffleModeEnabled(enabled: Boolean) {
            player.shuffleModeEnabled = enabled
        }

        override fun setRepeatMode(mode: RepeatMode) {
            player.repeatMode = mode.toPlayerRepeatMode()
        }

        override fun playNext(song: Song) {
            val wasEmpty = player.mediaItemCount == 0
            queue.playNext(song.toMediaItem())
            if (wasEmpty) player.prepare()
        }

        override fun addToQueue(song: Song) {
            val wasEmpty = player.mediaItemCount == 0
            queue.addToQueue(song.toMediaItem())
            if (wasEmpty) player.prepare()
        }

        override fun removeFromQueue(index: Int) {
            queue.removeAt(index)
        }

        override fun setVolume(volume: Float) {
            player.volume = volume.coerceIn(0f, 1f)
        }

        override fun reorder(
            from: Int,
            to: Int,
        ) {
            queue.reorder(from, to)
        }

        companion object {
            private const val TAG = "PlaybackController"
            private const val POSITION_POLL_INTERVAL_MS = 250L

            /** Persist the position roughly every 5 s while playing. */
            private const val PERSIST_EVERY_N_POLLS = 20
        }
    }
