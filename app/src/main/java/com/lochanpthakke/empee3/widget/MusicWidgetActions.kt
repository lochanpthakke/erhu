// Published callback identities retained for existing widget PendingIntents.
package com.lochanpthakke.empee3.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.erhu.erhu.core.model.PlaybackStatus
import com.erhu.erhu.widget.PlaybackControllerEntryPoint
import com.erhu.erhu.widget.performWidgetAction
import dagger.hilt.android.EntryPointAccessors

private fun Context.widgetEntryPoint() = EntryPointAccessors.fromApplication(this, PlaybackControllerEntryPoint::class.java)

internal fun Context.playbackController() = widgetEntryPoint().playbackController()

internal fun Context.libraryRepository() = widgetEntryPoint().libraryRepository()

class PlayPauseAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        performWidgetAction(context, "PlayPause") { controller ->
            val playing =
                controller.playbackState.value.status
                    .let { it == PlaybackStatus.PLAYING || it == PlaybackStatus.BUFFERING }
            if (playing) controller.pause() else controller.playFromWidget()
        }
    }
}

class SkipNextAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        performWidgetAction(context, "SkipNext") { controller ->
            controller.skipNext()
        }
    }
}

class SkipPreviousAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        performWidgetAction(context, "SkipPrevious") { controller ->
            controller.skipPrevious()
        }
    }
}

class ToggleFavoriteAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        performWidgetAction(context, "ToggleFavorite") { controller ->
            controller.playbackState.value.currentSong
                ?.let { context.libraryRepository().toggleFavorite(it.id) }
        }
    }
}

class SeekToPercentAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        performWidgetAction(context, "SeekToPercent") { controller ->
            val percent = parameters[KEY_PERCENT]?.takeIf { it.isFinite() }?.coerceIn(0f, 1f)
            val duration = controller.playbackState.value.durationMs
            if (percent != null && duration > 0) controller.seekTo((duration * percent).toLong())
        }
    }

    companion object {
        val KEY_PERCENT = ActionParameters.Key<Float>("percent")
    }
}

class SeekForwardAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        performWidgetAction(context, "SeekForward") { controller ->
            controller.seekTo(controller.playbackState.value.positionMs + 10_000L)
        }
    }
}
