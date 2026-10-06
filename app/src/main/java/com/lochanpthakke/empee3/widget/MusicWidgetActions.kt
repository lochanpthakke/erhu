package com.lochanpthakke.empee3.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.lochanpthakke.empee3.core.model.PlaybackStatus
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun Context.widgetEntryPoint() = EntryPointAccessors.fromApplication(this, PlaybackControllerEntryPoint::class.java)

internal fun Context.playbackController() = widgetEntryPoint().playbackController()

internal fun Context.libraryRepository() = widgetEntryPoint().libraryRepository()

// ExoPlayer must be driven from the thread it was created on (main); Glance
// runs action callbacks on a background dispatcher.

class PlayPauseAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) = withContext(Dispatchers.Main) {
        val controller = context.playbackController()
        val status = controller.playbackState.value.status
        val isPlaying = status == PlaybackStatus.PLAYING || status == PlaybackStatus.BUFFERING
        if (isPlaying) controller.pause() else controller.play()
    }
}

class SkipNextAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) = withContext(Dispatchers.Main) {
        context.playbackController().skipNext()
    }
}

class SkipPreviousAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) = withContext(Dispatchers.Main) {
        context.playbackController().skipPrevious()
    }
}

/** Toggles the favorite flag of the playing song in Room (same state the app shows). */
class ToggleFavoriteAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val songId =
            context
                .playbackController()
                .playbackState.value.currentSong
                ?.id ?: return
        context.libraryRepository().toggleFavorite(songId)
        MusicWidget().update(context, glanceId)
    }
}
