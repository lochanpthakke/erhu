package com.erhu.erhu.widget

import android.content.Context
import android.util.Log
import com.erhu.erhu.BuildConfig
import com.erhu.erhu.core.playback.PlaybackController
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Glance owns explicit, immutable per-widget/action PendingIntents; callbacks share this path. */
internal suspend fun performWidgetAction(
    context: Context,
    name: String,
    action: suspend (PlaybackController) -> Unit,
) {
    if (BuildConfig.DEBUG) Log.d("ErhuWidget", "Control: $name")
    try {
        withTimeout(5_000L) {
            withContext(Dispatchers.Main.immediate) {
                val entry = EntryPointAccessors.fromApplication(context, PlaybackControllerEntryPoint::class.java)
                val controller = entry.playbackController()
                controller.awaitRestored()
                action(controller)
                entry.widgetUpdater().requestUpdate()
            }
        }
    } catch (cancelled: CancellationException) {
        Log.w("ErhuWidget", "Control cancelled or timed out: $name")
        throw cancelled
    } catch (error: Exception) {
        Log.e("ErhuWidget", "Control failed: $name", error)
    }
}
