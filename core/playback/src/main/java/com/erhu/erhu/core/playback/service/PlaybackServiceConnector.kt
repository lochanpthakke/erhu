package com.erhu.erhu.core.playback.service

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.erhu.erhu.core.common.logging.Logger
import com.google.common.util.concurrent.ListenableFuture
import com.lochanpthakke.empee3.core.playback.service.MusicPlaybackService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps a [MediaController] bound to [MusicPlaybackService].
 *
 * The app drives the shared ExoPlayer directly, but Media3 only creates the
 * MediaLibraryService (and with it the media notification, lock-screen
 * controls and the foreground-service status required for background
 * playback) once a controller binds to it. Without this binding, playback ran
 * in an ordinary background process and could be killed as soon as the app
 * left the foreground.
 */
@Singleton
class PlaybackServiceConnector
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val logger: Logger,
    ) {
        private val mainHandler = Handler(Looper.getMainLooper())
        private var controllerFuture: ListenableFuture<MediaController>? = null

        /** Idempotent; safe to call from any thread. */
        fun ensureConnected() {
            if (Looper.myLooper() != Looper.getMainLooper()) {
                mainHandler.post { ensureConnected() }
                return
            }
            val existing = controllerFuture
            if (existing != null && (!existing.isDone || existing.connectedControllerOrNull() != null)) return

            existing?.let { MediaController.releaseFuture(it) }
            val token = SessionToken(context, ComponentName(context, MusicPlaybackService::class.java))
            controllerFuture = MediaController.Builder(context, token).buildAsync()
            logger.d(TAG, "Binding to playback service")
        }

        /** Called only for an explicit widget Play tap, never for widget refresh/preview work. */
        fun playFromWidget() {
            androidx.core.content.ContextCompat.startForegroundService(
                context,
                android.content.Intent(context, MusicPlaybackService::class.java).setAction(MusicPlaybackService.ACTION_WIDGET_PLAY),
            )
        }

        @Suppress("TooGenericExceptionCaught")
        private fun ListenableFuture<MediaController>.connectedControllerOrNull(): MediaController? =
            try {
                get()?.takeIf { it.isConnected }
            } catch (e: Exception) {
                logger.w(TAG, "Playback service connection failed: ${e.message}", e)
                null
            }

        companion object {
            private const val TAG = "PlaybackServiceConnector"
        }
    }
