package com.lochanpthakke.empee3.core.playback.service

import androidx.media3.common.Player
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MusicPlaybackService : MediaLibraryService() {
    @Inject
    lateinit var player: Player

    private var mediaLibrarySession: MediaLibrarySession? = null

    override fun onCreate() {
        super.onCreate()
        mediaLibrarySession =
            MediaLibrarySession
                .Builder(this, player, LibrarySessionCallback())
                .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaLibrarySession

    override fun onDestroy() {
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
        // Intentionally empty. MediaLibrarySession.Callback's browse methods
        // (onGetLibraryRoot, onGetChildren, onSearch, etc.) have default
        // implementations that return RESULT_ERROR_NOT_SUPPORTED — that's fine
        // here. This step only needs the session bound to the shared Player so
        // MediaController-based playback commands (play/pause/skip/seek) work.
        // Full Android Auto browse tree is Deliverable #9.
    }
}
