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
import com.erhu.erhu.core.common.locale.AppLocaleManager
import com.erhu.erhu.core.common.locale.LocaleFormatters
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
import com.erhu.erhu.core.common.R as CommonR

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
                    val localized = AppLocaleManager.localizedContext(this)
                    operation.startFailed(
                        localized.getString(CommonR.string.metadata_service_start_failed, e.message.orEmpty()),
                    )
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
        val localized = AppLocaleManager.localizedContext(this)
        val locale = AppLocaleManager.activeLocale(localized)
        val cancel =
            PendingIntent.getService(
                this,
                2,
                Intent(this, MetadataRepairService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val processedCount = LocaleFormatters.formatInteger(progress.processed, locale)
        val totalCount = LocaleFormatters.formatInteger(progress.total, locale)
        val summaryText =
            localized.resources.getQuantityString(
                CommonR.plurals.metadata_service_notif_processed,
                progress.total,
                processedCount,
                totalCount,
            )
        val detailText = progress.reason ?: localized.getString(CommonR.string.metadata_service_notif_fetching_default)
        return NotificationCompat
            .Builder(this, PROGRESS_CHANNEL)
            .setContentTitle(localized.getString(CommonR.string.metadata_service_notif_fixing_title))
            .setContentText(summaryText)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    localized.getString(
                        CommonR.string.metadata_service_notif_processed_with_detail,
                        summaryText,
                        detailText,
                    ),
                ),
            ).setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentIntent(openIntent())
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(progress.total, progress.processed, progress.total == 0)
            .addAction(android.R.drawable.ic_delete, localized.getString(CommonR.string.action_cancel), cancel)
            .build()
    }

    internal fun completionNotification(progress: RepairProgress) {
        if (progress.phase == RepairPhase.IDLE || progress.phase == RepairPhase.FETCHING) return
        val localized = AppLocaleManager.localizedContext(this)
        val locale = AppLocaleManager.activeLocale(localized)
        val title =
            localized.getString(
                when (progress.phase) {
                    RepairPhase.COMPLETE -> CommonR.string.metadata_service_notif_complete
                    RepairPhase.FAILED -> CommonR.string.metadata_service_notif_stopped
                    RepairPhase.CANCELLED -> CommonR.string.metadata_service_notif_cancelled
                    else -> CommonR.string.metadata_service_notif_paused
                },
            )
        val text =
            if (progress.pending > 0) {
                localized.resources.getQuantityString(
                    CommonR.plurals.metadata_service_notif_pending_ready,
                    progress.pending,
                    LocaleFormatters.formatInteger(progress.pending, locale),
                )
            } else {
                val summaryText =
                    localized.resources.getQuantityString(
                        CommonR.plurals.metadata_service_notif_processed,
                        progress.total,
                        LocaleFormatters.formatInteger(progress.processed, locale),
                        LocaleFormatters.formatInteger(progress.total, locale),
                    )
                val detailText = progress.reason ?: localized.getString(CommonR.string.metadata_service_notif_open_review)
                localized.getString(CommonR.string.metadata_service_notif_processed_with_detail, summaryText, detailText)
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
                .apply {
                    if (progress.pending > 0) {
                        addAction(
                            android.R.drawable.ic_menu_edit,
                            localized.getString(CommonR.string.metadata_service_action_allow_changes),
                            openIntent(),
                        )
                    }
                }.build()
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
        val localized = AppLocaleManager.localizedContext(this)
        notifications.createNotificationChannel(
            NotificationChannel(
                PROGRESS_CHANNEL,
                localized.getString(CommonR.string.metadata_service_channel_fetch_progress),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        notifications.createNotificationChannel(
            NotificationChannel(
                COMPLETION_CHANNEL,
                localized.getString(CommonR.string.metadata_service_channel_fetch_results),
                NotificationManager.IMPORTANCE_HIGH,
            ),
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
