// Published Android/Glance/WorkManager identity retained for upgrade compatibility.
package com.lochanpthakke.empee3.core.database.scanner

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.erhu.erhu.core.common.locale.AppLocaleManager
import com.erhu.erhu.core.common.logging.Logger
import com.erhu.erhu.core.database.scanner.JAudioTaggerEnricher
import com.erhu.erhu.core.database.scanner.MediaStoreScanner
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import com.erhu.erhu.core.common.R as CommonR

@HiltWorker
class LibraryScanWorker
    @AssistedInject
    constructor(
        @Assisted private val appContext: Context,
        @Assisted workerParams: WorkerParameters,
        private val mediaStoreScanner: MediaStoreScanner,
        private val tagEnricher: JAudioTaggerEnricher,
        private val logger: Logger,
    ) : CoroutineWorker(appContext, workerParams) {
        @Suppress("TooGenericExceptionCaught")
        override suspend fun doWork(): Result {
            createNotificationChannel()

            val localizedContext = AppLocaleManager.localizedContext(appContext)
            try {
                setForeground(
                    createForegroundInfo(
                        0,
                        0,
                        localizedContext.getString(CommonR.string.scan_worker_initializing),
                    ),
                )
            } catch (e: Exception) {
                logger.w(TAG, "Failed to set foreground info: ${e.message}", e)
            }

            return try {
                runScan()
                Result.success()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // A failed scan leaves the previous library intact (scan writes are transactional).
                logger.e(TAG, "Library scan failed: ${e.message}", e)
                Result.failure()
            }
        }

        @Suppress("TooGenericExceptionCaught")
        @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
        private suspend fun runScan() {
            val localizedContext = AppLocaleManager.localizedContext(appContext)
            mediaStoreScanner.scanLibrary().collect { progress ->
                val title =
                    if (progress.currentSongTitle != null) {
                        localizedContext.getString(
                            CommonR.string.scan_worker_scanning_track,
                            progress.currentSongTitle,
                        )
                    } else {
                        localizedContext.getString(CommonR.string.scan_worker_scanning_library)
                    }
                try {
                    setForeground(
                        createForegroundInfo(
                            progress.scannedCount,
                            progress.totalCount,
                            title,
                        ),
                    )
                } catch (e: Exception) {
                    logger.d(TAG, "Foreground update skipped: ${e.message}", e)
                }
            }

            @OptIn(DelicateCoroutinesApi::class)
            GlobalScope.launch(Dispatchers.IO) {
                try {
                    tagEnricher.enrichUnenrichedSongs()
                } catch (e: Exception) {
                    logger.w(TAG, "Tag enrichment background task failed: ${e.message}", e)
                }
            }
        }

        private fun createForegroundInfo(
            scanned: Int,
            total: Int,
            currentTitle: String,
        ): ForegroundInfo {
            val localizedContext = AppLocaleManager.localizedContext(appContext)
            val notificationText =
                if (total > 0) {
                    localizedContext.resources.getQuantityString(
                        CommonR.plurals.scan_worker_progress,
                        total,
                        scanned,
                        total,
                    )
                } else {
                    localizedContext.getString(CommonR.string.scan_worker_indeterminate)
                }

            val notification =
                NotificationCompat
                    .Builder(appContext, CHANNEL_ID)
                    .setContentTitle(localizedContext.getString(CommonR.string.scan_worker_title))
                    .setContentText(notificationText)
                    .setSubText(currentTitle)
                    .setSmallIcon(android.R.drawable.ic_popup_sync)
                    .setOngoing(true)
                    .setProgress(total, scanned, total == 0)
                    .build()

            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ForegroundInfo(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                )
            } else {
                ForegroundInfo(NOTIFICATION_ID, notification)
            }
        }

        private fun createNotificationChannel() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val localizedContext = AppLocaleManager.localizedContext(appContext)
                val channel =
                    NotificationChannel(
                        CHANNEL_ID,
                        localizedContext.getString(CommonR.string.scan_worker_channel_name),
                        NotificationManager.IMPORTANCE_LOW,
                    ).apply {
                        description = localizedContext.getString(CommonR.string.scan_worker_channel_description)
                    }
                val notificationManager =
                    appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
            }
        }

        companion object {
            const val WORK_NAME = "LibraryScanWorker"
            private const val TAG = "LibraryScanWorker"
            private const val CHANNEL_ID = "library_scan_channel"

            @Suppress("MagicNumber")
            private const val NOTIFICATION_ID = 3001 // 1001 is Media3's playback notification
        }
    }
