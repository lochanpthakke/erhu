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
import com.lochanpthakke.empee3.core.common.logging.Logger
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

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

            try {
                setForeground(createForegroundInfo(0, 0, "Initializing scan..."))
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
        private suspend fun runScan() {
            mediaStoreScanner.scanLibrary().collect { progress ->
                val title =
                    if (progress.currentSongTitle != null) {
                        "Scanning: ${progress.currentSongTitle}"
                    } else {
                        "Scanning music library..."
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

            tagEnricher.enrichUnenrichedSongs()
        }

        private fun createForegroundInfo(
            scanned: Int,
            total: Int,
            currentTitle: String,
        ): ForegroundInfo {
            val notificationText =
                if (total > 0) {
                    "Scanned $scanned of $total tracks"
                } else {
                    "Scanning local media..."
                }

            val notification =
                NotificationCompat
                    .Builder(appContext, CHANNEL_ID)
                    .setContentTitle("Updating Music Library")
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
                val channel =
                    NotificationChannel(
                        CHANNEL_ID,
                        "Library Scan Progress",
                        NotificationManager.IMPORTANCE_LOW,
                    ).apply {
                        description = "Shows real-time progress when scanning local music files"
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
            private const val NOTIFICATION_ID = 1001
        }
    }
