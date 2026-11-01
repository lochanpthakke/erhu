package com.erhu.erhu.core.playback

import android.content.Context
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import com.erhu.erhu.core.common.artwork.ArtworkLoader
import com.erhu.erhu.core.common.dispatcher.DispatcherProvider
import com.erhu.erhu.core.common.logging.Logger
import com.erhu.erhu.core.common.result.onError
import com.erhu.erhu.core.database.repository.ListeningStatsRepository
import com.erhu.erhu.core.datastore.PlaybackStatePersistence
import com.erhu.erhu.core.model.AbLoopEdit
import com.erhu.erhu.core.model.CollectionRef
import com.erhu.erhu.core.model.PersistedPlaybackState
import com.erhu.erhu.core.model.PlaybackState
import com.erhu.erhu.core.model.PlaybackStatus
import com.erhu.erhu.core.model.QueueOrigin
import com.erhu.erhu.core.model.RepeatMode
import com.erhu.erhu.core.model.Song
import com.erhu.erhu.core.model.SongLookup
import com.erhu.erhu.core.model.TrackOccurrence
import com.erhu.erhu.core.playback.service.PlaybackServiceConnector
import com.erhu.erhu.core.playback.transition.ExoTailPlayer
import com.erhu.erhu.core.playback.transition.PlayerTransitionHost
import com.erhu.erhu.core.playback.transition.ProcessorIncomingFade
import com.erhu.erhu.core.playback.transition.TransitionCoordinator
import com.erhu.erhu.core.playback.transition.TransitionSettingsRepository
import com.erhu.erhu.core.sound.dsp.TransitionGainProcessor
import com.erhu.erhu.core.sound.repository.SoundRepository
import dagger.hilt.android.qualifiers.ApplicationContext
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
        @ApplicationContext private val context: Context,
        private val player: Player,
        private val dispatcherProvider: DispatcherProvider,
        private val logger: Logger,
        private val serviceConnector: PlaybackServiceConnector,
        private val persistence: PlaybackStatePersistence,
        private val songLookup: SongLookup,
        private val soundRepository: SoundRepository,
        private val listeningStats: ListeningStatsRepository,
        playerDiagnostics: com.erhu.erhu.core.playback.diagnostics.PlayerDiagnosticsTracker,
        private val transitionSettings: TransitionSettingsRepository,
        transitionGain: TransitionGainProcessor,
    ) : PlaybackController {
        private val scope = CoroutineScope(SupervisorJob() + dispatcherProvider.main)
        private val queue = PlaybackQueue(player)

        /** Removed queue entries, for undo. Cleared whenever the queue is replaced as a whole. */
        private val removals = QueueRemovals<MediaItem>()

        /** Tracks played this session (in memory), from the player's own transitions. */
        private val history = SessionHistory(now = System::currentTimeMillis)

        /**
         * A–B loop of the current entry, enforced here (player messages), not by any screen, so it
         * keeps looping with Now Playing closed or the display off.
         */
        private val abLoop =
            AbLoopController(
                scheduler = (player as? ExoPlayer)?.let(::PlayerMessageLoopScheduler),
                player =
                    object : LoopPlayer {
                        override val currentSongId: Long? get() = player.currentMediaItem?.mediaId?.toLongOrNull()
                        override val currentIndex: Int get() = player.currentMediaItemIndex
                        override val positionMs: Long get() = player.currentPosition.coerceAtLeast(0L)
                        override val durationMs: Long get() = currentDurationMs()

                        override fun seekTo(positionMs: Long) = player.seekTo(positionMs)
                    },
                onStateChanged = { loop ->
                    _playbackState.update { it.copy(abLoop = loop) }
                    transitions?.reevaluate()
                },
            )

        /**
         * Smooth Transitions for albums and playlists that have them on (null without an ExoPlayer,
         * e.g. in tests). Only entries started from such a collection are ever affected.
         */
        private val transitions: TransitionCoordinator? =
            (player as? ExoPlayer)?.let { exo ->
                TransitionCoordinator(
                    host =
                        PlayerTransitionHost(
                            player = exo,
                            resolve = transitionSettings::resolve,
                            abLoopEnabled = { abLoop.state?.enabled == true },
                        ),
                    incomingFade = ProcessorIncomingFade(transitionGain),
                    newTailPlayer = { ExoTailPlayer(context, soundRepository.soundSettings.value) },
                )
            }

        private val _playbackState = MutableStateFlow(PlaybackState())
        override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

        private var positionPollJob: Job? = null

        /** Persisting before the restore finished would overwrite the saved queue with an empty one. */
        private var restoreFinished = false
        private var consecutiveErrors = 0

        /** Sound Capsule: plays (half a song actually heard) and listening time, from the player's own callbacks. */
        private val listening =
            ListeningTracker(
                elapsedRealtime = SystemClock::elapsedRealtime,
                onPlayCounted = { songId -> recordListening { listeningStats.recordPlay(songId) } },
                onListened = { songId, ms -> recordListening { listeningStats.addListeningTime(songId, ms) } },
            )

        private val playerListener =
            object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    listening.onPlayingChanged(isPlaying, currentDurationMs())
                    if (isPlaying && history.onPlaybackStarted()) publishHistory()
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
                    if (playbackState == Player.STATE_ENDED) listening.onEnded()
                    if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) transitions?.release()
                    updateStatus()
                }

                override fun onPlayWhenReadyChanged(
                    playWhenReady: Boolean,
                    reason: Int,
                ) {
                    transitions?.onPlayingChanged(tailShouldPlay())
                }

                override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
                    transitions?.onPlayingChanged(tailShouldPlay())
                }

                override fun onVolumeChanged(volume: Float) {
                    transitions?.onVolumeChanged(volume)
                }

                override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                    transitions?.onSpeedChanged(playbackParameters.speed)
                }

                override fun onMediaItemTransition(
                    mediaItem: MediaItem?,
                    reason: Int,
                ) {
                    listening.onItemChanged(mediaItem?.mediaId?.toLongOrNull(), currentDurationMs(), player.isPlaying)
                    if (history.onItemBecameCurrent(mediaItem?.toSongOrNull(), player.isPlaying)) publishHistory()
                    // Markers belong to the entry that was playing: another track never loops on them.
                    abLoop.onEntryChanged(sameEntryRepeated = reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT)
                    refreshQueueAndCurrent()
                    _playbackState.update { it.copy(positionMs = player.currentPosition.coerceAtLeast(0L)) }
                    persistState()
                    transitions?.reevaluate()
                }

                override fun onTimelineChanged(
                    timeline: Timeline,
                    reason: Int,
                ) {
                    if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
                        refreshQueueAndCurrent()
                        persistState()
                    }
                    // Also when a duration becomes known (source update).
                    transitions?.reevaluate()
                }

                override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                    _playbackState.update { it.copy(shuffleModeEnabled = shuffleModeEnabled) }
                    // The next / previous song follow the play order.
                    refreshQueueAndCurrent()
                    persistState()
                }

                override fun onRepeatModeChanged(repeatMode: Int) {
                    _playbackState.update { it.copy(repeatMode = repeatMode.toDomainRepeatMode()) }
                    refreshQueueAndCurrent()
                    persistState()
                    transitions?.reevaluate()
                }

                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int,
                ) {
                    refreshPosition()
                    if (reason == Player.DISCONTINUITY_REASON_SEEK || reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT) {
                        transitions?.onSeek()
                    }
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
            // Records what the player reports about the audio (decoder, formats) for the audio inspector.
            playerDiagnostics.start()
            (player as? ExoPlayer)?.audioSessionId?.takeIf { it != C.AUDIO_SESSION_ID_UNSET }?.let {
                soundRepository.attachAudioSession(it)
            }
            scope.launch { restorePersistedState() }
            // Turning a collection's transitions on or off (or changing them) applies to the boundary ahead.
            scope.launch { transitionSettings.config.collect { transitions?.reevaluate() } }
        }

        private fun tailShouldPlay(): Boolean =
            player.playWhenReady && player.playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE

        private fun publishHistory() {
            _playbackState.update { it.copy(history = history.entries) }
        }

        /** Replaces the whole queue; positions of earlier removals no longer apply. */
        private fun setQueue(
            items: List<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ) {
            removals.clear()
            player.setMediaItems(items, startIndex, startPositionMs)
        }

        private fun updateStatus() {
            val status =
                when {
                    player.isPlaying -> PlaybackStatus.PLAYING
                    player.playbackState == Player.STATE_IDLE && player.mediaItemCount == 0 -> PlaybackStatus.IDLE
                    player.playbackState == Player.STATE_ENDED -> PlaybackStatus.IDLE
                    !player.playWhenReady -> PlaybackStatus.PAUSED
                    else -> PlaybackStatus.BUFFERING
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
                    currentCollection = player.currentMediaItem?.collectionRef(),
                    nextIndex = player.nextMediaItemIndex.takeIf { songs.isNotEmpty() && it in songs.indices } ?: -1,
                    previousIndex = player.previousMediaItemIndex.takeIf { songs.isNotEmpty() && it in songs.indices } ?: -1,
                    durationMs = resolveDuration(current),
                    positionMs = if (current == null) 0L else it.positionMs,
                )
            }
            if (current == null) {
                updateStatus()
            } else {
                preloadUpcomingQueueArtwork()
            }
        }

        private var preloadJob: Job? = null

        private fun preloadUpcomingQueueArtwork() {
            preloadJob?.cancel()
            val state = _playbackState.value
            val current = state.currentSong ?: return
            val next = state.nextSong
            val prev = state.previousSong

            preloadJob =
                scope.launch(dispatcherProvider.io) {
                    ArtworkLoader.load(context, current.uri, current.artworkUri, PRELOAD_ARTWORK_SIZE_PX)
                    if (next != null) {
                        ArtworkLoader.load(context, next.uri, next.artworkUri, PRELOAD_ARTWORK_SIZE_PX)
                    }
                    if (prev != null) {
                        ArtworkLoader.load(context, prev.uri, prev.artworkUri, PRELOAD_ARTWORK_SIZE_PX)
                    }
                }
        }

        private fun resolveDuration(song: Song?): Long {
            val rawDuration = player.duration
            val duration = if (rawDuration != C.TIME_UNSET && rawDuration > 0) rawDuration else song?.durationMs ?: 0L
            return duration.coerceAtLeast(0L)
        }

        private fun currentDurationMs(): Long = resolveDuration(player.currentMediaItem?.toSongOrNull())

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
                        listening.onProgress(_playbackState.value.durationMs)
                        ticks++
                        if (ticks % PERSIST_EVERY_N_POLLS == 0) persistState()
                        if (ticks % LISTENING_CHECKPOINT_POLLS == 0) listening.checkpoint()
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
                setQueue(
                    restored.songs.mapIndexed { i, song -> song.toMediaItem(restored.origins.getOrNull(i)) },
                    restored.startIndex,
                    restored.startPositionMs,
                )
                player.shuffleModeEnabled = false
                player.repeatMode = saved.repeatMode.toPlayerRepeatMode()
                _playbackState.update { it.copy(shuffleModeEnabled = saved.shuffleModeEnabled) }
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

        /** One small statistics write, off the main thread; a failure never reaches playback. */
        private fun recordListening(write: suspend () -> Unit) {
            scope.launch(dispatcherProvider.io) {
                try {
                    write()
                } catch (
                    @Suppress("TooGenericExceptionCaught") e: Exception,
                ) {
                    logger.w(TAG, "Could not record listening statistics: ${e.message}", e)
                }
            }
        }

        private fun persistState() {
            if (!restoreFinished) return
            val snapshot =
                PersistedPlaybackState(
                    queueSongIds = List(player.mediaItemCount) { player.getMediaItemAt(it).mediaId.toLongOrNull() ?: -1L },
                    currentIndex = player.currentMediaItemIndex,
                    positionMs = player.currentPosition.coerceAtLeast(0L),
                    shuffleModeEnabled = _playbackState.value.shuffleModeEnabled,
                    repeatMode = player.repeatMode.toDomainRepeatMode(),
                    queueCollectionKeys =
                        List(player.mediaItemCount) {
                            player
                                .getMediaItemAt(it)
                                .queueOrigin()
                                ?.key
                                .orEmpty()
                        },
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
        ) = playCollection(songs, startIndex, origin = null)

        override fun playCollection(
            songs: List<Song>,
            startIndex: Int,
            origin: CollectionRef?,
            slots: List<Int>?,
        ) {
            // Indices, not songs: the same song can be in the list twice, and each entry keeps its own occurrence.
            val playable = songs.indices.filter { songs[it].uri.isNotBlank() }
            if (playable.isEmpty()) {
                logger.w(TAG, "Ignoring request to play an empty queue")
                return
            }
            val index = playable.indexOf(startIndex).takeIf { it >= 0 } ?: 0
            consecutiveErrors = 0

            val isShuffleActive = _playbackState.value.shuffleModeEnabled
            val order =
                if (isShuffleActive && playable.size > 1) {
                    listOf(playable[index]) + playable.filterIndexed { i, _ -> i != index }.shuffled()
                } else {
                    playable
                }
            val playIndex = if (isShuffleActive && playable.size > 1) 0 else index

            player.shuffleModeEnabled = false
            // Every entry carries where it was started from; a new queue replaces the old context.
            val items =
                order.map { i ->
                    val song = songs[i]
                    song.toMediaItem(origin?.let { QueueOrigin(it, TrackOccurrence(song.id, slots?.getOrNull(i))) })
                }
            setQueue(items, playIndex, 0L)
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

        override fun skipToPreviousItem() {
            if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem()
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
            player.shuffleModeEnabled = false
            _playbackState.update { it.copy(shuffleModeEnabled = enabled) }
            if (enabled) {
                removals.clear()
                queue.shuffleFullQueueFromCurrent()
            }
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
            removals.push(index, queue.removeAt(index))
        }

        override fun undoQueueRemoval(): Boolean {
            val removal = removals.pop() ?: return false
            val wasEmpty = player.mediaItemCount == 0
            queue.insertAt(removal.index, removal.item)
            if (wasEmpty) player.prepare()
            return true
        }

        override fun shuffleUpcoming(): Boolean = queue.shuffleUpcoming()

        override fun replaceUpcoming(songs: List<Song>) {
            val playable = songs.filter { it.uri.isNotBlank() }
            val wasEmpty = player.mediaItemCount == 0
            // Undo positions would point into the entries being replaced.
            removals.clear()
            queue.replaceUpcoming(playable.map { it.toMediaItem() })
            if (wasEmpty && playable.isNotEmpty()) player.prepare()
        }

        override fun setLoopStart(): AbLoopEdit = abLoop.setStart()

        override fun setLoopEnd(): AbLoopEdit = abLoop.setEnd()

        override fun setLoopEnabled(enabled: Boolean): AbLoopEdit = abLoop.setEnabled(enabled)

        override fun clearLoop() = abLoop.clear()

        override fun currentPositionMs(): Long = player.currentPosition.coerceAtLeast(0L)

        override fun playFromHistory(entryId: Long) {
            val song = history.find(entryId)?.song?.takeIf { it.uri.isNotBlank() } ?: return
            if (player.mediaItemCount == 0) {
                playSong(song)
            } else {
                val target = (player.currentMediaItemIndex + 1).coerceIn(0, player.mediaItemCount)
                queue.insertAt(target, song.toMediaItem())
                skipToQueueItem(target)
            }
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
            private const val PRELOAD_ARTWORK_SIZE_PX = 512

            /** Record the listening time so far about once a minute while playing. */
            private const val LISTENING_CHECKPOINT_POLLS = 240
        }
    }
