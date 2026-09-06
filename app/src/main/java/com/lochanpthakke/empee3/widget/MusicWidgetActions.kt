package com.lochanpthakke.empee3.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import com.lochanpthakke.empee3.core.model.PlaybackStatus
import dagger.hilt.android.EntryPointAccessors

internal fun Context.playbackController() =
    EntryPointAccessors
        .fromApplication(this, PlaybackControllerEntryPoint::class.java)
        .playbackController()

class PlayPauseAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val controller = context.playbackController()
        val isPlaying = controller.playbackState.value.status == PlaybackStatus.PLAYING
        if (isPlaying) controller.pause() else controller.play()
    }
}

class SkipNextAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        context.playbackController().skipNext()
    }
}

class SkipPreviousAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        context.playbackController().skipPrevious()
    }
}

class ToggleFavoriteAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[favoriteToggleKey] = !(prefs[favoriteToggleKey] ?: false)
        }
        MusicWidget().update(context, glanceId)
    }
}
