@file:Suppress("detekt:All")

// Published Android/Glance/WorkManager identity retained for upgrade compatibility.

package com.lochanpthakke.empee3.core.playback.service

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import com.erhu.erhu.core.common.dispatcher.DispatcherProvider
import com.erhu.erhu.core.common.logging.Logger
import com.erhu.erhu.core.database.repository.LibraryRepository
import com.erhu.erhu.core.database.repository.RecentlyPlayedRecorder
import com.erhu.erhu.core.playback.PlaybackController
import com.erhu.erhu.core.playback.browse.BrowseTree
import com.erhu.erhu.core.playback.browse.MediaId
import com.erhu.erhu.core.playback.service.PlaybackServiceConnector
import com.erhu.erhu.core.playback.service.SongArtworkBitmapLoader
import com.erhu.erhu.core.playback.toMediaItem
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

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
    lateinit var libraryRepository: LibraryRepository

    @Inject
    lateinit var dispatchers: DispatcherProvider

    @Inject
    lateinit var recentlyPlayed: RecentlyPlayedRecorder

    @Inject
    lateinit var logger: Logger

    private var mediaLibrarySession: MediaLibrarySession? = null
    private lateinit var serviceScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        serviceScope = CoroutineScope(SupervisorJob() + dispatchers.main)

        val sessionPlayer =
            object : ForwardingPlayer(player) {
                override fun isPlaying(): Boolean =
                    super.isPlaying() ||
                        (playWhenReady && playbackState != Player.STATE_IDLE && playbackState != Player.STATE_ENDED)
            }

        val builder =
            MediaLibrarySession
                .Builder(this, sessionPlayer, LibrarySessionCallback())
                .setBitmapLoader(SongArtworkBitmapLoader(this))
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

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaLibrarySession

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
        serviceScope.launch(dispatchers.io) {
            try {
                future.set(block())
            } catch (e: Exception) {
                logger.w("MusicPlaybackService", "Library browse request failed: ${e.message}", e)
                future.setException(e)
            }
        }
        return future
    }

    @Suppress("MagicNumber", "LongMethod", "CyclomaticComplexMethod")
    private inner class LibrarySessionCallback : MediaLibrarySession.Callback {
        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: MediaLibraryService.LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(
                LibraryResult.ofItem(BrowseTree.getRoot(), params),
            )

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: MediaLibraryService.LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
            runAsync {
                val children: List<MediaItem> =
                    when {
                        parentId == MediaId.ROOT -> MediaId.ROOT_CHILDREN.map { BrowseTree.getCategoryFolder(it) }
                        parentId == MediaId.SONGS -> libraryRepository.getAllSongs().first().map { it.toMediaItem() }
                        parentId == MediaId.ALBUMS -> libraryRepository.getAllAlbums().first().map { BrowseTree.albumFolder(it) }
                        parentId.startsWith(MediaId.ALBUM_PREFIX) -> {
                            val albumId = parentId.removePrefix(MediaId.ALBUM_PREFIX).toLongOrNull() ?: 0L
                            libraryRepository.getSongsByAlbumId(albumId).first().map { it.toMediaItem() }
                        }
                        parentId == MediaId.ARTISTS -> libraryRepository.getAllArtists().first().map { BrowseTree.artistFolder(it) }
                        parentId.startsWith(MediaId.ARTIST_PREFIX) -> {
                            val artistId = parentId.removePrefix(MediaId.ARTIST_PREFIX).toLongOrNull() ?: 0L
                            libraryRepository.getSongsByArtistId(artistId).first().map { it.toMediaItem() }
                        }
                        parentId == MediaId.PLAYLISTS -> emptyList()
                        else -> return@runAsync LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
                    }
                LibraryResult.ofItemList(ImmutableList.copyOf(children), params)
            }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> =
            runAsync {
                val songId = mediaId.toLongOrNull()
                if (songId != null) {
                    val song = libraryRepository.getSongById(songId).first()
                    if (song != null) {
                        return@runAsync LibraryResult.ofItem(song.toMediaItem(), null)
                    }
                }
                LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
            }

        /**
         * Browse results handed to external controllers (Android Auto, etc.) carry
         * no URI; resolve them against the library before they reach the player.
         */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> =
            runAsync {
                mediaItems
                    .mapNotNull { item ->
                        if (item.localConfiguration != null) {
                            item
                        } else {
                            item.mediaId.toLongOrNull()?.let { id ->
                                libraryRepository.getSongById(id).first()?.toMediaItem()
                            }
                        }
                    }.toMutableList()
            }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: MediaLibraryService.LibraryParams?,
        ): ListenableFuture<LibraryResult<Void>> =
            runAsync {
                val results = libraryRepository.searchSongs(query).first()
                session.notifySearchResultChanged(browser, query, results.size, params)
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
            runAsync {
                val results = libraryRepository.searchSongs(query).first()
                val mediaItems = results.map { it.toMediaItem() }
                LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), params)
            }
    }
}
