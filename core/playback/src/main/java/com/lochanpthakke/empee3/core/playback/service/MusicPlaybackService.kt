
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
import com.lochanpthakke.empee3.core.common.logging.Logger
import com.lochanpthakke.empee3.core.playback.PlaybackController
import com.lochanpthakke.empee3.core.playback.browse.BrowseTree
import com.lochanpthakke.empee3.core.playback.receiver.NoisyAudioReceiver
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
@UnstableApi
class MusicPlaybackService : MediaLibraryService() {
    @Inject
    lateinit var player: Player

    @Inject
    lateinit var playbackController: PlaybackController

    @Inject
    lateinit var logger: Logger

    private var mediaLibrarySession: MediaLibrarySession? = null
    private var noisyAudioReceiver: NoisyAudioReceiver? = null

    override fun onCreate() {
        super.onCreate()
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

        // Release the SESSION only. `player` is @Singleton-scoped and owned by
        // PlayerProviderModule — releasing it here would kill playback state
        // for the rest of the app (PlaybackControllerImpl holds the same instance).
        mediaLibrarySession?.run {
            release()
            mediaLibrarySession = null
        }
        super.onDestroy()
    }

    private inner class LibrarySessionCallback : MediaLibrarySession.Callback {
        // Deliverable #9 — structural browse tree. All category nodes return
        // empty children until Phase 3 (Room + MediaStore) supplies real data
        // via SongLookup. Pagination params (page/pageSize) are accepted but
        // ignored for now since every list is empty or trivially small —
        // real paging (Paging3) lands with Phase 3's Library Layer.

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
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val children =
                BrowseTree.getChildren(parentId)
                    ?: return Futures.immediateFuture(
                        LibraryResult.ofError(SessionError.ERROR_BAD_VALUE),
                    )
            return Futures.immediateFuture(
                LibraryResult.ofItemList(ImmutableList.copyOf(children), params),
            )
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val item =
                BrowseTree.getItem(mediaId)
                    ?: return Futures.immediateFuture(
                        LibraryResult.ofError(SessionError.ERROR_BAD_VALUE),
                    )
            return Futures.immediateFuture(LibraryResult.ofItem(item, null))
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: MediaLibraryService.LibraryParams?,
        ): ListenableFuture<LibraryResult<Void>> {
            // No SongLookup implementation exists yet (Phase 3). Report zero
            // results rather than RESULT_ERROR_NOT_SUPPORTED — some Auto
            // clients treat "not supported" as a reason to hide search
            // entirely, whereas "zero results" keeps the search UI available
            // for when real data lands.
            session.notifySearchResultChanged(browser, query, 0, params)
            return Futures.immediateFuture(LibraryResult.ofVoid(params))
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: MediaLibraryService.LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
            Futures.immediateFuture(
                LibraryResult.ofItemList(ImmutableList.of(), params),
            )
    }
}
