@file:Suppress("detekt:All")

// Published Android/Glance/WorkManager identity retained for upgrade compatibility.

package com.lochanpthakke.empee3.core.database.worker

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
import com.erhu.erhu.core.common.logging.Logger
import com.erhu.erhu.core.database.dao.SongDao
import com.erhu.erhu.core.database.entity.ProcessingStatus
import com.erhu.erhu.core.database.mapper.toDomainModel
import com.erhu.erhu.core.database.usecase.ProcessSingleTrackUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class MetadataBatchWorker
    @AssistedInject
    constructor(
        @Assisted private val appContext: Context,
        @Assisted workerParams: WorkerParameters,
        private val songDao: SongDao,
        private val processSingleTrackUseCase: ProcessSingleTrackUseCase,
        private val logger: Logger,
    ) : CoroutineWorker(appContext, workerParams) {
        override suspend fun doWork(): Result {
            createNotificationChannel()

            val forceScan = inputData.getBoolean(KEY_FORCE_SCAN, false)
            val allSongEntities = songDao.getAllSongs().first()
            val allSongs = allSongEntities.map { it.toDomainModel() }
            // Song.uri is a content:// URI; tag I/O needs the real file path.
            val filePathById = allSongEntities.associate { it.id to it.filePath }

            if (allSongs.isEmpty()) {
                return Result.success()
            }

            var processedCount = 0
            var successCount = 0
            var failedCount = 0
            var needsReviewCount = 0

            try {
                setForeground(
                    createForegroundInfo(
                        processedCount = 0,
                        totalCount = allSongs.size,
                        title = "Starting metadata repair...",
                    ),
                )
            } catch (e: Exception) {
                logger.w(TAG, "Foreground notification start failed: ${e.message}", e)
            }

            val failedForRetry = mutableListOf<com.erhu.erhu.core.model.Song>()

            for (song in allSongs) {
                if (isStopped) break

                try {
                    val entityResult =
                        processSingleTrackUseCase(
                            song = song,
                            filePath = filePathById[song.id].orEmpty(),
                            forceScan = forceScan,
                        )

                    processedCount++

                    when (entityResult.status) {
                        ProcessingStatus.SUCCESS.name -> successCount++
                        ProcessingStatus.NEEDS_REVIEW.name -> needsReviewCount++
                        ProcessingStatus.FAILED.name -> {
                            failedCount++
                            failedForRetry.add(song)
                        }
                    }

                    try {
                        setForeground(
                            createForegroundInfo(
                                processedCount = processedCount,
                                totalCount = allSongs.size,
                                title = "Processing: ${song.title}",
                            ),
                        )
                    } catch (_: Exception) {
                        // Ignore progress notification updates
                    }
                } catch (e: Exception) {
                    logger.w(TAG, "Failed processing track ${song.title}: ${e.message}", e)
                    failedCount++
                    failedForRetry.add(song)
                }
            }

            if (!isStopped && failedForRetry.isNotEmpty()) {
                for (retrySong in failedForRetry) {
                    if (isStopped) break
                    try {
                        val retryResult =
                            processSingleTrackUseCase(
                                song = retrySong,
                                filePath = filePathById[retrySong.id].orEmpty(),
                                forceScan = true,
                            )
                        if (retryResult.status == ProcessingStatus.SUCCESS.name) {
                            failedCount--
                            successCount++
                        }
                    } catch (_: Exception) {
                        // Retry failed silently
                    }
                }
            }

            return Result.success()
        }

        private fun createForegroundInfo(
            processedCount: Int,
            totalCount: Int,
            title: String,
        ): ForegroundInfo {
            val text = "Processed $processedCount of $totalCount tracks"
            val notification =
                NotificationCompat
                    .Builder(appContext, CHANNEL_ID)
                    .setContentTitle("Metadata Editor Repair")
                    .setContentText(text)
                    .setSubText(title)
                    .setSmallIcon(android.R.drawable.ic_popup_sync)
                    .setOngoing(true)
                    .setProgress(totalCount, processedCount, totalCount == 0)
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
                        "Metadata Editor Progress",
                        NotificationManager.IMPORTANCE_LOW,
                    ).apply {
                        description = "Shows progress during batch metadata cleaning"
                    }
                val notificationManager =
                    appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
            }
        }

        companion object {
            const val WORK_NAME = "MetadataBatchWorker"
            const val KEY_FORCE_SCAN = "key_force_scan"
            private const val TAG = "MetadataBatchWorker"
            private const val CHANNEL_ID = "metadata_batch_channel"

            @Suppress("MagicNumber")
            private const val NOTIFICATION_ID = 2002
        }
    }
