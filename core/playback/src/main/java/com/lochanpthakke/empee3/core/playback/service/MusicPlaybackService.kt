@file:Suppress("detekt:All")

// Published Android/Glance/WorkManager identity retained for upgrade compatibility.

package com.lochanpthakke.empee3.core.playback.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import com.erhu.erhu.core.common.dispatcher.DispatcherProvider
import com.erhu.erhu.core.common.locale.AppLocaleManager
import com.erhu.erhu.core.common.logging.Logger
import com.erhu.erhu.core.database.repository.RecentlyPlayedRecorder
import com.erhu.erhu.core.playback.PlaybackController
import com.erhu.erhu.core.playback.browse.BrowseTree
import com.erhu.erhu.core.playback.browse.CarArtworkProvider
import com.erhu.erhu.core.playback.browse.CarMediaLibrary
import com.erhu.erhu.core.playback.browse.MediaId
import com.erhu.erhu.core.playback.service.PlaybackServiceConnector
import com.erhu.erhu.core.playback.service.SongArtworkBitmapLoader
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import com.erhu.erhu.core.common.R as CommonR

/**
 * Hosts the MediaLibrarySession around the app-wide ExoPlayer. Media3 turns
 * this into a foreground service with a media notification while playing,
 * which is what keeps playback alive in the background / on the lock screen.
 * The app binds to it through [PlaybackServiceConnector].
 */
@AndroidEntryPoint
@UnstableApi
class MusicPlaybackService : MediaLibraryService() {
    @Inject
    lateinit var player: Player

    // Injected so the controller (and its player listeners / state restore)
    // exists even when the service is started by an external controller.
    @Inject
    lateinit var playbackController: PlaybackController

    @Inject
    lateinit var dispatchers: DispatcherProvider

    @Inject
    lateinit var recentlyPlayed: RecentlyPlayedRecorder

    @Inject
    lateinit var logger: Logger

    @Inject
    internal lateinit var carLibrary: CarMediaLibrary

    private val artworkClients = ConcurrentHashMap.newKeySet<String>()

    private var mediaLibrarySession: MediaLibrarySession? = null
    private lateinit var serviceScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        serviceScope = CoroutineScope(SupervisorJob() + dispatchers.main)

        val sessionPlayer =
            object : ForwardingPlayer(player) {
                override fun play() {
                    if (Build.VERSION.SDK_INT >= 35) ensurePlaybackForeground()
                    super.play()
                }

                override fun setPlayWhenReady(playWhenReady: Boolean) {
                    if (playWhenReady && Build.VERSION.SDK_INT >= 35) ensurePlaybackForeground()
                    super.setPlayWhenReady(playWhenReady)
                }

                override fun isPlaying(): Boolean =
                    super.isPlaying() ||
                        (playWhenReady && playbackState != Player.STATE_IDLE && playbackState != Player.STATE_ENDED)
            }

        val builder =
            MediaLibrarySession
                .Builder(this, sessionPlayer, LibrarySessionCallback())
                .setBitmapLoader(SongArtworkBitmapLoader(this))
                .setLibraryErrorReplicationMode(MediaLibrarySession.LIBRARY_ERROR_REPLICATION_MODE_NON_FATAL)
        packageManager.getLaunchIntentForPackage(packageName)?.let { launchIntent ->
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            builder.setSessionActivity(
                PendingIntent.getActivity(
                    this,
                    0,
                    launchIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
        }
        mediaLibrarySession = builder.build()
        player.addListener(playHistoryListener)
    }

    /**
     * Recently played albums come from real playback only: a song counts when it
     * actually starts playing (play pressed, or the next track while playing), not
     * when a saved queue is merely restored at launch.
     */
    private val playHistoryListener =
        object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) recordCurrentSong()
            }

            override fun onMediaItemTransition(
                mediaItem: MediaItem?,
                reason: Int,
            ) {
                if (player.isPlaying) recordCurrentSong()
            }
        }

    private fun recordCurrentSong() {
        val songId = player.currentMediaItem?.mediaId?.toLongOrNull() ?: return
        serviceScope.launch(dispatchers.io) { recentlyPlayed.recordSongPlayed(songId) }
    }

    /** Android 17 requires a user-started media FGS before the first audio-focus request.
     * Media3 1.5.1 only performs its pre-play promotion on API 31/32. Bootstrap the
     * existing media notification here; Media3 immediately owns subsequent updates. */
    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (intent?.action != ACTION_WIDGET_PLAY) return super.onStartCommand(intent, flags, startId)
        val session = mediaLibrarySession
        if (session == null || player.mediaItemCount == 0) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        ensurePlaybackForeground()
        playbackController.play()
        return START_STICKY
    }

    /** Promote before requesting audio focus, including cold-start Android Auto commands. */
    private fun ensurePlaybackForeground() {
        val session = mediaLibrarySession ?: return
        if (player.mediaItemCount == 0) return
        val channel = androidx.media3.session.DefaultMediaNotificationProvider.DEFAULT_CHANNEL_ID
        getSystemService(android.app.NotificationManager::class.java).createNotificationChannel(
            android.app.NotificationChannel(
                channel,
                getString(androidx.media3.session.DefaultMediaNotificationProvider.DEFAULT_CHANNEL_NAME_RESOURCE_ID),
                android.app.NotificationManager.IMPORTANCE_LOW,
            ),
        )
        val notification =
            androidx.core.app.NotificationCompat
                .Builder(this, channel)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(
                    playbackController.playbackState.value.currentSong
                        ?.title,
                ).setContentText(
                    playbackController.playbackState.value.currentSong
                        ?.artist,
                ).setContentIntent(session.sessionActivity)
                .setStyle(
                    androidx.media3.session.MediaStyleNotificationHelper
                        .MediaStyle(session),
                ).setOnlyAlertOnce(true)
                .setSilent(true)
                .build()
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            startForeground(
                androidx.media3.session.DefaultMediaNotificationProvider.DEFAULT_NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(androidx.media3.session.DefaultMediaNotificationProvider.DEFAULT_NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val ACTION_WIDGET_PLAY = "com.erhu.erhu.action.WIDGET_PLAY"
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaLibrarySession

    /**
     * Swiping erhu away from Recents ends playback instead of leaving it playing with no app open.
     * The queue and position are kept, so playback can resume from the notification or the app.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        player.playWhenReady = false
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        // The player is an app-wide singleton shared with PlaybackController, so
        // only the session is released here — never the player itself.
        player.removeListener(playHistoryListener)
        mediaLibrarySession?.run {
            release()
            mediaLibrarySession = null
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    @Suppress("TooGenericExceptionCaught")
    private fun <T> runAsync(block: suspend () -> T): ListenableFuture<T> {
        val future = SettableFuture.create<T>()
        val job =
            serviceScope.launch(dispatchers.io) {
                try {
                    future.set(block())
                } catch (e: CancellationException) {
                    future.cancel(false)
                    throw e
                } catch (e: Exception) {
                    logger.w("MusicPlaybackService", "Library browse request failed", e)
                    future.setException(e)
                }
            }
        future.addListener(
            { if (future.isCancelled) job.cancel() },
            com.google.common.util.concurrent.MoreExecutors
                .directExecutor(),
        )
        return future
    }

    private fun grantArtwork(controller: MediaSession.ControllerInfo) {
        if (artworkClients.contains(controller.packageName)) return
        try {
            grantUriPermission(
                controller.packageName,
                CarArtworkProvider.rootUri(this),
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION,
            )
            artworkClients.add(controller.packageName)
        } catch (e: IllegalArgumentException) {
            logger.w("MusicPlaybackService", "Controller cannot receive artwork access", e)
        } catch (e: SecurityException) {
            logger.w("MusicPlaybackService", "Artwork access could not be granted", e)
        }
    }

    private fun carError(
        code: Int,
        messageRes: Int,
    ): SessionError {
        val localized = AppLocaleManager.localizedContext(this)
        val extras = Bundle()
        packageManager.getLaunchIntentForPackage(packageName)?.let { intent ->
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val openPhone = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            extras.putParcelable(MediaConstants.EXTRAS_KEY_ERROR_RESOLUTION_ACTION_INTENT_COMPAT, openPhone)
            extras.putString(
                MediaConstants.EXTRAS_KEY_ERROR_RESOLUTION_ACTION_LABEL_COMPAT,
                localized.getString(CommonR.string.car_open_phone),
            )
        }
        return SessionError(code, localized.getString(messageRes), extras)
    }

    private fun <T : Any> libraryRequest(
        controller: MediaSession.ControllerInfo,
        block: suspend () -> LibraryResult<T>,
    ): ListenableFuture<LibraryResult<T>> {
        grantArtwork(controller)
        return runAsync<LibraryResult<T>> {
            if (!carLibrary.hasAudioPermission()) {
                LibraryResult.ofError<T>(carError(SessionError.ERROR_SESSION_SETUP_REQUIRED, CommonR.string.car_error_music_access))
            } else {
                try {
                    block()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: SecurityException) {
                    LibraryResult.ofError<T>(carError(SessionError.ERROR_SESSION_SETUP_REQUIRED, CommonR.string.car_error_music_access))
                } catch (e: Exception) {
                    logger.w("MusicPlaybackService", "Could not load car library", e)
                    LibraryResult.ofError<T>(carError(SessionError.ERROR_IO, CommonR.string.car_error_load))
                }
            }
        }
    }

    /** Fail without replacing the current queue; legacy car clients also receive a visible error. */
    private fun <T : Any> playbackRequest(
        controller: MediaSession.ControllerInfo,
        missingMessage: Int,
        block: suspend () -> T?,
    ): ListenableFuture<T> {
        grantArtwork(controller)
        val future = SettableFuture.create<T>()
        val job =
            serviceScope.launch {
                var error: SessionError? = null
                try {
                    playbackController.awaitRestored()
                    if (!carLibrary.hasAudioPermission()) {
                        error = carError(SessionError.ERROR_SESSION_SETUP_REQUIRED, CommonR.string.car_error_music_access)
                    } else {
                        val resolved = withContext(dispatchers.io) { block() }
                        if (resolved != null) {
                            future.set(resolved)
                        } else {
                            error = carError(SessionError.ERROR_BAD_VALUE, missingMessage)
                        }
                    }
                } catch (e: CancellationException) {
                    future.cancel(false)
                    throw e
                } catch (_: SecurityException) {
                    error = carError(SessionError.ERROR_SESSION_SETUP_REQUIRED, CommonR.string.car_error_music_access)
                } catch (e: Exception) {
                    logger.w("MusicPlaybackService", "Could not resolve car playback", e)
                    error = carError(SessionError.ERROR_IO, CommonR.string.car_error_load)
                }
                error?.let {
                    mediaLibrarySession?.sendError(controller, it)
                    future.setException(IllegalArgumentException(it.message))
                }
            }
        future.addListener(
            { if (future.isCancelled) job.cancel() },
            com.google.common.util.concurrent.MoreExecutors
                .directExecutor(),
        )
        return future
    }

    private inner class LibrarySessionCallback : MediaLibrarySession.Callback {
        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: MediaLibraryService.LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            grantArtwork(browser)
            val extras =
                Bundle(params?.extras ?: Bundle.EMPTY).apply {
                    putBoolean("android.media.browse.SEARCH_SUPPORTED", true)
                    putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM)
                    putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM)
                }
            val root =
                BrowseTree.getRoot().let {
                    it
                        .buildUpon()
                        .setMediaMetadata(
                            it.mediaMetadata
                                .buildUpon()
                                .setExtras(extras)
                                .build(),
                        ).build()
                }
            return Futures.immediateFuture(
                LibraryResult.ofItem(
                    root,
                    LibraryParams
                        .Builder()
                        .setOffline(true)
                        .setExtras(extras)
                        .build(),
                ),
            )
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: MediaLibraryService.LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
            libraryRequest(browser) {
                if (page < 0 || pageSize <= 0) {
                    LibraryResult.ofError(carError(SessionError.ERROR_BAD_VALUE, CommonR.string.car_error_request))
                } else {
                    val children = carLibrary.children(parentId, page, pageSize)
                    if (children == null) {
                        LibraryResult.ofError(carError(SessionError.ERROR_BAD_VALUE, CommonR.string.car_error_unavailable))
                    } else {
                        LibraryResult.ofItemList(ImmutableList.copyOf(children), params)
                    }
                }
            }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> =
            libraryRequest(browser) {
                val item = carLibrary.item(mediaId)
                if (item == null) {
                    LibraryResult.ofError(carError(SessionError.ERROR_BAD_VALUE, CommonR.string.car_error_unavailable))
                } else {
                    LibraryResult.ofItem(item, null)
                }
            }

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> =
            playbackRequest(
                controller,
                if (mediaItems.any {
                        it.requestMetadata.searchQuery != null
                    }
                ) {
                    CommonR.string.car_error_no_results
                } else {
                    CommonR.string.car_error_unavailable
                },
            ) {
                if (startIndex != C.INDEX_UNSET && startIndex !in mediaItems.indices) return@playbackRequest null
                if (startPositionMs != C.TIME_UNSET && startPositionMs < 0) return@playbackRequest null
                when {
                    mediaItems.isEmpty() -> MediaSession.MediaItemsWithStartPosition(emptyList(), C.INDEX_UNSET, C.TIME_UNSET)
                    controller.packageName == packageName && mediaItems.all { it.localConfiguration != null } ->
                        MediaSession.MediaItemsWithStartPosition(mediaItems, startIndex, startPositionMs)
                    mediaItems.size == 1 ->
                        carLibrary.selection(mediaItems.single())?.let {
                            MediaSession.MediaItemsWithStartPosition(it.items, it.startIndex, startPositionMs)
                        }
                    else -> {
                        val resolved = mediaItems.map { carLibrary.playable(it) ?: return@playbackRequest null }
                        MediaSession.MediaItemsWithStartPosition(resolved, startIndex, startPositionMs)
                    }
                }
            }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> =
            playbackRequest(controller, CommonR.string.car_error_unavailable) {
                mediaItems
                    .map { item ->
                        if (controller.packageName == packageName && item.localConfiguration != null) {
                            item
                        } else {
                            carLibrary.playable(item) ?: return@playbackRequest null
                        }
                    }.toMutableList()
            }

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> =
            playbackRequest(controller, CommonR.string.car_error_unavailable) {
                val restored =
                    withContext(dispatchers.main) {
                        if (player.mediaItemCount == 0) {
                            null
                        } else {
                            MediaSession.MediaItemsWithStartPosition(
                                List(player.mediaItemCount) {
                                    player.getMediaItemAt(it)
                                },
                                player.currentMediaItemIndex,
                                player.currentPosition,
                            )
                        }
                    }
                restored ?: carLibrary.selection(MediaItem.Builder().setMediaId(MediaId.SONGS).build())?.let {
                    MediaSession.MediaItemsWithStartPosition(it.items, it.startIndex, C.TIME_UNSET)
                }
            }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: MediaLibraryService.LibraryParams?,
        ): ListenableFuture<LibraryResult<Void>> =
            libraryRequest(browser) {
                val count = carLibrary.searchCount(query)
                withContext(dispatchers.main) { session.notifySearchResultChanged(browser, query, count, params) }
                LibraryResult.ofVoid(params)
            }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: MediaLibraryService.LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
            libraryRequest(browser) {
                if (page < 0 || pageSize <= 0) {
                    LibraryResult.ofError(carError(SessionError.ERROR_BAD_VALUE, CommonR.string.car_error_request))
                } else {
                    LibraryResult.ofItemList(ImmutableList.copyOf(carLibrary.search(query, page, pageSize)), params)
                }
            }
    }
}
