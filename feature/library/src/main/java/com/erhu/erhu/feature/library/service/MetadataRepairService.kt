package com.erhu.erhu.feature.library.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.erhu.erhu.feature.library.MetadataRepairOperation
import com.erhu.erhu.feature.library.RepairPhase
import com.erhu.erhu.feature.library.RepairProgress
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Owns the single existing repair runner until fetch completion, pause, cancellation or timeout. */
@AndroidEntryPoint
class MetadataRepairService : Service() {
    @Inject lateinit var operation: MetadataRepairOperation
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var running: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val notifications get() = getSystemService(NotificationManager::class.java)

    override fun onBind(intent: Intent?): IBinder? = null

    @Suppress("ReturnCount", "TooGenericExceptionCaught")
    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            ACTION_START -> {
                if (running?.isActive == true) return START_NOT_STICKY
                createChannels()
                notifications.cancel(COMPLETION_NOTIFICATION_ID)
                try {
                    val initial = progressNotification(operation.progress.value)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(NOTIFICATION_ID, initial, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                    } else {
                        startForeground(NOTIFICATION_ID, initial)
                    }
                    wakeLock =
                        (getSystemService(POWER_SERVICE) as PowerManager)
                            .newWakeLock(
                                PowerManager.PARTIAL_WAKE_LOCK,
                                "erhu:metadata-fetch",
                            ).apply { acquire(MAX_WAKE_MS) }
                } catch (e: RuntimeException) {
                    operation.startFailed("Background metadata fetching could not start: ${e.message}")
                    stopSelf()
                    return START_NOT_STICKY
                }
                running =
                    scope.launch {
                        val updates =
                            launch {
                                operation.progress.collect {
                                    try {
                                        notifications.notify(NOTIFICATION_ID, progressNotification(it))
                                    } catch (_: SecurityException) {
                                    }
                                }
                            }
                        try {
                            operation.execute()
                        } finally {
                            updates.cancel()
                            releaseWakeLock()
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            completionNotification(operation.progress.value)
                            stopSelf()
                        }
                    }
            }
            ACTION_STOP -> {
                running?.cancel()
                if (running == null) stopSelf()
            }
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(
        startId: Int,
        fgsType: Int,
    ) {
        running?.cancel()
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        running?.cancel()
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    internal fun progressNotification(progress: RepairProgress): Notification {
        val cancel =
            PendingIntent.getService(
                this,
                2,
                Intent(this, MetadataRepairService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        return NotificationCompat
            .Builder(this, PROGRESS_CHANNEL)
            .setContentTitle("erhu – Fixing metadata")
            .setContentText("${progress.processed} / ${progress.total} tracks processed")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "${progress.processed} / ${progress.total} tracks processed. " +
                        (progress.reason ?: "Fetching information for your music library."),
                ),
            ).setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentIntent(openIntent())
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(progress.total, progress.processed, progress.total == 0)
            .addAction(android.R.drawable.ic_delete, "Cancel", cancel)
            .build()
    }

    internal fun completionNotification(progress: RepairProgress) {
        if (progress.phase == RepairPhase.IDLE || progress.phase == RepairPhase.FETCHING) return
        val title =
            when (progress.phase) {
                RepairPhase.COMPLETE -> "Metadata fetching complete"
                RepairPhase.FAILED -> "Metadata fetching stopped"
                RepairPhase.CANCELLED -> "Metadata fetching cancelled"
                else -> "Metadata fetching paused"
            }
        val text =
            if (progress.pending > 0) {
                "${progress.pending} tracks ready. Allow erhu to modify your files to apply the changes."
            } else {
                "${progress.processed} / ${progress.total} tracks processed. " +
                    (progress.reason ?: "Open Auto Fix Tags to review the results.")
            }
        val notification =
            NotificationCompat
                .Builder(this, COMPLETION_CHANNEL)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setSmallIcon(android.R.drawable.ic_popup_sync)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setAutoCancel(true)
                .setContentIntent(openIntent())
                .apply { if (progress.pending > 0) addAction(android.R.drawable.ic_menu_edit, "Allow changes", openIntent()) }
                .build()
        // Notification denial never prevents fetching or access to the in-app repair results.
        try {
            notifications.notify(COMPLETION_NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
        }
    }

    private fun openIntent(): PendingIntent? =
        packageManager.getLaunchIntentForPackage(packageName)?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            it.putExtra(EXTRA_OPEN_REPAIR, true)
            PendingIntent.getActivity(
                this,
                COMPLETION_NOTIFICATION_ID,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

    internal fun createChannels() {
        notifications.createNotificationChannel(
            NotificationChannel(PROGRESS_CHANNEL, "Metadata fetching progress", NotificationManager.IMPORTANCE_LOW),
        )
        notifications.createNotificationChannel(
            NotificationChannel(COMPLETION_CHANNEL, "Metadata fetching results", NotificationManager.IMPORTANCE_HIGH),
        )
    }

    companion object {
        const val ACTION_START = "ACTION_START_REPAIR"
        const val ACTION_STOP = "ACTION_STOP_REPAIR"
        const val EXTRA_OPEN_REPAIR = "com.erhu.erhu.OPEN_METADATA_REPAIR"
        internal const val PROGRESS_CHANNEL = "metadata_repair_service_channel"
        internal const val COMPLETION_CHANNEL = "metadata_repair_results"
        internal const val NOTIFICATION_ID = 2004
        internal const val COMPLETION_NOTIFICATION_ID = 2005
        private const val MAX_WAKE_MS = 6 * 60 * 60 * 1000L
    }
}
