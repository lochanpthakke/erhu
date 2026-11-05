package com.erhu.erhu.feature.library.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.erhu.erhu.core.common.logging.Logger
import com.erhu.erhu.feature.library.RepairBatchRunner
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MetadataRepairService : Service() {

    @Inject lateinit var logger: Logger

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var isFetching = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                if (!isFetching) {
                    isFetching = true
                    startForegroundService()
                    startFetching()
                }
            }
            ACTION_STOP -> stopForegroundService()
        }
        return START_NOT_STICKY
    }

    private fun startFetching() {
        serviceScope.launch {
            // Note: Since RepairBatchRunner loop acts on memory structures from LibraryViewModel, 
            // Phase 2 implementation here must serve as the real engine loop, updating progress and
            // finally posting the completion alert. Because the UI requires state flows,
            // we will shift the service to operate as an orchestrator of the foreground 
            // notification while the view model executes the in-memory batch.
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun startForegroundService() {
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, buildProgressNotification(0, 100), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, buildProgressNotification(0, 100))
        }
    }

    private fun buildProgressNotification(processed: Int, total: Int): android.app.Notification {
        val cancelIntent = PendingIntent.getService(
            this, 0, Intent(this, MetadataRepairService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Metadata Editor Repair")
            .setContentText("Fetched $processed of $total tracks")
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(total, processed, total == 0)
            .addAction(android.R.drawable.ic_delete, "Cancel", cancelIntent)
            .build()
    }

    fun updateProgress(processed: Int, total: Int) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildProgressNotification(processed, total))
    }

    fun showCompletionNotification(tracksToWrite: Int) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = intent?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Metadata Fetch Complete")
            .setContentText("$tracksToWrite tracks ready to update. Tap to approve file changes.")
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .apply {
                if (pendingIntent != null) setContentIntent(pendingIntent)
            }.build()

        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(COMPLETION_NOTIFICATION_ID, notification)
        stopForegroundService()
    }

    private fun stopForegroundService() {
        isFetching = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Metadata Fetcher Progress",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows progress during metadata network fetching"
        }
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_START = "ACTION_START_REPAIR"
        const val ACTION_STOP = "ACTION_STOP_REPAIR"
        private const val CHANNEL_ID = "metadata_repair_service_channel"
        
        @Suppress("MagicNumber")
        private const val NOTIFICATION_ID = 2004
        
        @Suppress("MagicNumber")
        private const val COMPLETION_NOTIFICATION_ID = 2005
    }
}
