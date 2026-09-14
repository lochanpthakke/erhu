@file:Suppress("detekt:All")

package com.lochanpthakke.empee3.core.playback.service

import android.content.IntentFilter
import android.media.AudioManager
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.lochanpthakke.empee3.core.common.dispatcher.DispatcherProvider
import com.lochanpthakke.empee3.core.common.logging.Logger
import com.lochanpthakke.empee3.core.database.repository.LibraryRepository
import com.lochanpthakke.empee3.core.playback.PlaybackController
import com.lochanpthakke.empee3.core.playback.browse.BrowseTree
import com.lochanpthakke.empee3.core.playback.browse.MediaId
import com.lochanpthakke.empee3.core.playback.receiver.NoisyAudioReceiver
import com.lochanpthakke.empee3.core.playback.toMediaItem
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
@UnstableApi
class MusicPlaybackService : MediaLibraryService() {
    @Inject
    lateinit var player: Player

    @Inject
    lateinit var playbackController: PlaybackController

    @Inject
    lateinit var libraryRepository: LibraryRepository

    @Inject
    lateinit var dispatchers: DispatcherProvider

    @Inject
    lateinit var logger: Logger

    private var mediaLibrarySession: MediaLibrarySession? = null
    private var noisyAudioReceiver: NoisyAudioReceiver? = null
    private lateinit var serviceScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        serviceScope = CoroutineScope(SupervisorJob() + dispatchers.main)

        mediaLibrarySession =
            MediaLibrarySession
                .Builder(this, player, LibrarySessionCallback())
                .build()

        noisyAudioReceiver = NoisyAudioReceiver(playbackController, logger)
        registerReceiver(
            noisyAudioReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaLibrarySession

    override fun onDestroy() {
        noisyAudioReceiver?.let {
            unregisterReceiver(it)
            noisyAudioReceiver = null
        }

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
                // Suppress logging spam and complete future with error
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
