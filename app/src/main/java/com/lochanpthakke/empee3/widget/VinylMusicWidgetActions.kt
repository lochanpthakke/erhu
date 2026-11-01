// Published Android/Glance/WorkManager identity retained for upgrade compatibility.
package com.lochanpthakke.empee3.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import com.erhu.erhu.core.model.PlaybackStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private suspend fun updateAllWidgets(context: Context) {
    val manager = GlanceAppWidgetManager(context)
    val vinylWidget = VinylMusicWidget()
    manager.getGlanceIds(VinylMusicWidget::class.java).forEach { vinylWidget.update(context, it) }
    val glassWidget = GlassMusicWidget()
    manager.getGlanceIds(GlassMusicWidget::class.java).forEach { glassWidget.update(context, it) }
    val musicWidget = MusicWidget()
    manager.getGlanceIds(MusicWidget::class.java).forEach { musicWidget.update(context, it) }
}

class VinylPlayPauseAction : ActionCallback {
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

class VinylSkipNextAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) = withContext(Dispatchers.Main) {
        context.playbackController().skipNext()
        updateAllWidgets(context)
    }
}

class VinylSkipPreviousAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) = withContext(Dispatchers.Main) {
        context.playbackController().skipPrevious()
        updateAllWidgets(context)
    }
}
