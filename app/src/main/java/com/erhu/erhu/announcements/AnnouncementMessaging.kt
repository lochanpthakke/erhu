package com.erhu.erhu.announcements

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.erhu.erhu.BuildConfig
import com.erhu.erhu.core.network.announcements.validAnnouncementId
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TOPIC = "erhu-announcements"

object AnnouncementPush {
    fun initialize(context: Context) {
        if (listOf(
                BuildConfig.FIREBASE_APP_ID,
                BuildConfig.FIREBASE_API_KEY,
                BuildConfig.FIREBASE_PROJECT_ID,
                BuildConfig.FIREBASE_SENDER_ID,
            ).any(String::isBlank)
        ) {
            return
        }
        runCatching {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(
                    context,
                    FirebaseOptions
                        .Builder()
                        .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                        .setApiKey(BuildConfig.FIREBASE_API_KEY)
                        .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                        .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
                        .build(),
                )
            }
            FirebaseMessaging.getInstance().isAutoInitEnabled = true
            register(context)
        }
    }

    fun register(context: Context) {
        enqueue(context, "register", null)
    }

    fun enqueue(
        context: Context,
        event: String,
        id: String?,
    ) {
        val work =
            OneTimeWorkRequestBuilder<AnnouncementWorker>()
                .setInputData(
                    Data
                        .Builder()
                        .putString("event", event)
                        .putString("id", id)
                        .build(),
                ).setConstraints(Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "announcement-${id ?: event}",
            if (id ==
                null
            ) {
                ExistingWorkPolicy.KEEP
            } else {
                ExistingWorkPolicy.REPLACE
            },
            work,
        )
    }

    fun periodic(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "announcement-refresh",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<AnnouncementWorker>(12, TimeUnit.HOURS)
                .setInitialDelay(12, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build())
                .build(),
        )
    }
}

class AnnouncementMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        AnnouncementPush.register(this)
    } // SDK owns token storage; never log tokens.

    override fun onMessageReceived(message: RemoteMessage) {
        val id = message.data["announcement_id"] ?: return
        val event = message.data["event"] ?: return
        if (validAnnouncementId(id) && event in setOf("published", "invalidate")) AnnouncementPush.enqueue(this, event, id)
    }
}

@HiltWorker
class AnnouncementWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted parameters: WorkerParameters,
        private val controller: AnnouncementController,
        private val notifications: AnnouncementNotifications,
    ) : CoroutineWorker(context, parameters) {
        override suspend fun doWork(): Result {
            if (inputData.getString("event") == "register") {
                if (FirebaseApp.getApps(applicationContext).isEmpty()) return Result.success()
                return try {
                    suspendCancellableCoroutine<Unit> { c ->
                        FirebaseMessaging.getInstance().subscribeToTopic(TOPIC).addOnCompleteListener { task ->
                            if (c.isActive) {
                                if (task.isSuccessful) {
                                    c.resume(Unit)
                                } else {
                                    c.resumeWithException(
                                        task.exception ?: IllegalStateException("Registration unavailable"),
                                    )
                                }
                            }
                        }
                    }
                    Result.success()
                } catch (
                    cancelled: kotlinx.coroutines.CancellationException,
                ) {
                    throw cancelled
                } catch (_: Exception) {
                    if (runAttemptCount < 3) Result.retry() else Result.failure()
                }
            }
            if (!controller.configured) return Result.success()
            val id = inputData.getString("id")
            if (!controller.refresh(id, true)) return if (runAttemptCount < 3) Result.retry() else Result.failure()
            if (id != null) {
                if (inputData.getString("event") == "invalidate") notifications.cancel(id) else notifications.show(id)
            }
            return Result.success()
        }
    }
