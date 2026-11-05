// Published callback identities retained for existing widget PendingIntents.
package com.lochanpthakke.empee3.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.erhu.erhu.core.model.PlaybackStatus
import com.erhu.erhu.widget.performWidgetAction

class VinylPlayPauseAction : ActionCallback {
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

class VinylSkipNextAction : ActionCallback {
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

class VinylSkipPreviousAction : ActionCallback {
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
