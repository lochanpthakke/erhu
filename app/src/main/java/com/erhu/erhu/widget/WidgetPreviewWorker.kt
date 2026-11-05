package com.erhu.erhu.widget

import android.content.Context
import android.os.Build
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lochanpthakke.empee3.widget.GlassMusicWidgetReceiver
import com.lochanpthakke.empee3.widget.MusicWidgetReceiver
import com.lochanpthakke.empee3.widget.VinylMusicWidgetReceiver
import java.util.concurrent.TimeUnit

/** Generated previews supplement XML/bitmap fallbacks; rate limits never block widget rendering. */
class WidgetPreviewWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (Build.VERSION.SDK_INT < 35) return Result.success()
        val prefs = applicationContext.getSharedPreferences("widget-preview-publication", Context.MODE_PRIVATE)
        val manager = GlanceAppWidgetManager(applicationContext)
        var limited = false
        for (receiver in listOf(VinylMusicWidgetReceiver::class, GlassMusicWidgetReceiver::class, MusicWidgetReceiver::class)) {
            val key = receiver.java.name
            if (prefs.getInt(key, 0) == 1) continue
            try {
                if (manager.setWidgetPreviews(receiver) ==
                    GlanceAppWidgetManager.SET_WIDGET_PREVIEWS_RESULT_SUCCESS
                ) {
                    prefs.edit().putInt(key, 1).apply()
                } else {
                    limited = true
                }
            } catch (
                cancelled: kotlinx.coroutines.CancellationException,
            ) {
                throw cancelled
            } catch (
                error: Exception,
            ) {
                android.util.Log.w("ErhuWidget", "Preview publication failed: ${receiver.simpleName}", error)
                limited =
                    true
            }
        }
        return if (limited) Result.retry() else Result.success()
    }

    companion object {
        fun enqueue(context: Context) {
            if (Build.VERSION.SDK_INT < 35) return
            WorkManager.getInstance(context).enqueueUniqueWork(
                "publish-widget-previews-v1",
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<WidgetPreviewWorker>().setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.HOURS).build(),
            )
        }
    }
}
