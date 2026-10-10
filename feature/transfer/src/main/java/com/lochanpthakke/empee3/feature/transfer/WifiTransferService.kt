package com.lochanpthakke.empee3.feature.transfer

import android.app.Notification
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
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.lochanpthakke.empee3.feature.transfer.server.TransferStatus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Keeps a transfer session alive while the user switches apps or locks the screen, with an honest
 * ongoing notification (address while waiting, files while receiving) and a Stop action. It exists
 * only while a session is starting or running: it is started by the Start button and stops itself
 * as soon as the session ends for any reason. Not sticky: after process death it is not restarted
 * (the session and its credentials died with the process).
 */
@AndroidEntryPoint
class WifiTransferService : Service() {
    @Inject lateinit var coordinator: WifiTransferCoordinator

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var started = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (intent?.action == ACTION_STOP) {
            coordinator.stop(StopReason.USER)
            stopSelf()
            return START_NOT_STICKY
        }
        ensureChannel()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification("Starting Wi-Fi Transfer…", null),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
        if (!started) {
            started = true
            scope.launch { coordinator.startServer() }
            scope.launch {
                combine(coordinator.state, coordinator.items) { state, items -> state to items }.collect { (state, items) ->
                    when (state) {
                        is TransferServerState.Stopped -> {
                            ServiceCompat.stopForeground(this@WifiTransferService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                            stopSelf()
                        }
                        TransferServerState.Starting -> Unit
                        is TransferServerState.Running -> {
                            val receiving = items.count { it.status == TransferStatus.RECEIVING || it.status == TransferStatus.FINALIZING }
                            val saved = items.count { it.status == TransferStatus.COMPLETED }
                            val text =
                                when {
                                    receiving > 0 -> "Receiving $receiving file${if (receiving == 1) "" else "s"} · $saved saved"
                                    saved > 0 -> "$saved file${if (saved == 1) "" else "s"} saved · waiting at ${state.address}"
                                    else -> "Waiting for a computer at ${state.address}"
                                }
                            notify(notification("Wi-Fi Transfer is on", text))
                        }
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    /** Android 15+: a dataSync foreground service has a daily time limit; end the session when it is reached. */
    override fun onTimeout(
        startId: Int,
        fgsType: Int,
    ) {
        coordinator.stop(StopReason.SYSTEM_TIME_LIMIT)
        stopSelf()
    }

    override fun onDestroy() {
        coordinator.stop(StopReason.USER)
        scope.cancel()
        super.onDestroy()
    }

    private fun notify(notification: Notification) {
        getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, notification)
    }

    private fun notification(
        title: String,
        text: String?,
    ): Notification {
        val open =
            packageManager.getLaunchIntentForPackage(packageName)?.let {
                PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
            }
        val stop =
            PendingIntent.getService(
                this,
                1,
                Intent(this, WifiTransferService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE,
            )
        return NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Wi-Fi Transfer", NotificationManager.IMPORTANCE_LOW))
        }
    }

    companion object {
        private const val CHANNEL_ID = "wifi_transfer"
        private const val NOTIFICATION_ID = 4107
        private const val ACTION_STOP = "com.lochanpthakke.empee3.transfer.STOP"

        /** Starts a session (the caller has checked permissions and Wi-Fi). */
        fun start(context: Context) = ContextCompat.startForegroundService(context, Intent(context, WifiTransferService::class.java))

        fun stop(context: Context) {
            context.startService(Intent(context, WifiTransferService::class.java).setAction(ACTION_STOP))
        }
    }
}
