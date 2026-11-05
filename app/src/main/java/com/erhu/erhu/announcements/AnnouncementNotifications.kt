package com.erhu.erhu.announcements

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.erhu.erhu.MainActivity
import com.erhu.erhu.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

const val ANNOUNCEMENT_EXTRA = "erhu.announcement.id"
private const val CHANNEL = "erhu_announcements"

@Singleton
class AnnouncementNotifications
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val controller: AnnouncementController,
    ) {
        fun cancel(id: String) {
            NotificationManagerCompat.from(context).cancel(id, 0)
        }

        suspend fun show(id: String) {
            val item = controller.current(id) ?: return cancel(id)
            if (!controller.unseen(id)) return
            if (controller.foreground.value) {
                controller.requested.value = id
                return
            }
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Announcements", NotificationManager.IMPORTANCE_LOW).apply {
                    description =
                        "erhu news and important notices"
                },
            )
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
            val intent =
                Intent(context, MainActivity::class.java).apply {
                    data = Uri.parse("erhu://announcement/$id")
                    putExtra(ANNOUNCEMENT_EXTRA, id)
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
            val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notification =
                NotificationCompat
                    .Builder(context, CHANNEL)
                    .setSmallIcon(R.drawable.ic_announcement)
                    .setContentTitle(item.title)
                    .setContentText(item.body.take(240))
                    .setStyle(NotificationCompat.BigTextStyle().bigText(item.body))
                    .setContentIntent(pending)
                    .setAutoCancel(true)
                    .setOnlyAlertOnce(true)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .build()
            try {
                NotificationManagerCompat.from(context).notify(id, 0, notification)
                controller.notified(id)
            } catch (
                _: SecurityException,
            ) {
                // In-app notices still work when notification permission is denied.
            }
        }
    }
