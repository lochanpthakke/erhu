package com.lochanpthakke.empee3.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import com.lochanpthakke.empee3.core.model.PlaybackStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private suspend fun updateAllWidgets(context: Context) {
    val manager = GlanceAppWidgetManager(context)
    val glassWidget = GlassMusicWidget()
    manager.getGlanceIds(GlassMusicWidget::class.java).forEach { glassWidget.update(context, it) }
    val musicWidget = MusicWidget()
    manager.getGlanceIds(MusicWidget::class.java).forEach { musicWidget.update(context, it) }
}

class GlassPlayPauseAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) = withContext(Dispatchers.Main) {
        val controller = context.playbackController()
        val status = controller.playbackState.value.status
        val isPlaying = status == PlaybackStatus.PLAYING || status == PlaybackStatus.BUFFERING
        if (isPlaying) controller.pause() else controller.play()
        updateAllWidgets(context)
    }
}

class GlassSkipNextAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) = withContext(Dispatchers.Main) {
        context.playbackController().skipNext()
        updateAllWidgets(context)
    }
}

class GlassSkipPreviousAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) = withContext(Dispatchers.Main) {
        context.playbackController().skipPrevious()
        updateAllWidgets(context)
    }
}

class GlassToggleFavoriteAction : ActionCallback {
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
        updateAllWidgets(context)
    }
}

class GlassSeekToPercentAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) = withContext(Dispatchers.Main) {
        val percent = parameters[KEY_PERCENT] ?: return@withContext
        val controller = context.playbackController()
        val durationMs = controller.playbackState.value.durationMs
        if (durationMs > 0) {
            val targetPos = (durationMs * percent).toLong()
            controller.seekTo(targetPos)
        }
    }

    companion object {
        val KEY_PERCENT = ActionParameters.Key<Float>("percent")
    }
}
