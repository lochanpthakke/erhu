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
import com.erhu.erhu.core.common.locale.AppLocaleManager
import com.erhu.erhu.core.common.logging.Logger
import com.erhu.erhu.core.database.dao.MetadataProcessingDao
import com.erhu.erhu.core.database.dao.SongDao
import com.erhu.erhu.core.database.entity.ProcessingStatus
import com.erhu.erhu.core.database.mapper.toDomainModel
import com.erhu.erhu.core.database.usecase.ProcessSingleTrackUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import com.erhu.erhu.core.common.R as CommonR

@HiltWorker
class MetadataBatchWorker
    @AssistedInject
    constructor(
        @Assisted private val appContext: Context,
        @Assisted workerParams: WorkerParameters,
        private val songDao: SongDao,
        private val metadataProcessingDao: MetadataProcessingDao,
        private val processSingleTrackUseCase: ProcessSingleTrackUseCase,
        private val logger: Logger,
    ) : CoroutineWorker(appContext, workerParams) {
        override suspend fun doWork(): Result {
            createNotificationChannel()

            val forceScan = inputData.getBoolean(KEY_FORCE_SCAN, false)
            val pendingRecords = metadataProcessingDao.getRecordsByStatus(ProcessingStatus.PENDING.name).first()
            val pendingPaths = pendingRecords.map { it.filePath }.toSet()

            val allSongEntities = songDao.getAllSongs().first()
            val songsToProcess =
                if (pendingPaths.isNotEmpty()) {
                    allSongEntities.filter { it.filePath in pendingPaths }.map { it.toDomainModel() }
                } else {
                    allSongEntities.map { it.toDomainModel() }
                }

            // Song.uri is a content:// URI; tag I/O needs the real file path.
            val filePathById = allSongEntities.associate { it.id to it.filePath }

            if (songsToProcess.isEmpty()) {
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
                        totalCount = songsToProcess.size,
                        title = "Starting metadata repair...",
                    ),
                )
            } catch (e: Exception) {
                logger.w(TAG, "Foreground notification start failed: ${e.message}", e)
            }

            val failedForRetry = mutableListOf<com.erhu.erhu.core.model.Song>()

            for (song in songsToProcess) {
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
                        setProgress(
                            androidx.work.workDataOf(
                                KEY_PROCESSED_COUNT to processedCount,
                                KEY_TOTAL_COUNT to songsToProcess.size,
                                KEY_CURRENT_TITLE to song.title,
                            ),
                        )
                        setForeground(
                            createForegroundInfo(
                                processedCount = processedCount,
                                totalCount = songsToProcess.size,
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

            try {
                showCompletionNotification(successCount = successCount, totalCount = songsToProcess.size)
            } catch (e: Exception) {
                logger.w(TAG, "Completion notification failed: ${e.message}", e)
            }

            return Result.success()
        }

        private fun showCompletionNotification(
            successCount: Int,
            totalCount: Int,
        ) {
            val intent =
                appContext.packageManager.getLaunchIntentForPackage(appContext.packageName)?.apply {
                    flags =
                        android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                        android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            val pendingIntent =
                intent?.let {
                    android.app.PendingIntent.getActivity(
                        appContext,
                        0,
                        it,
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
                    )
                }
            val localizedContext = AppLocaleManager.localizedContext(appContext)
            val text =
                localizedContext.getString(
                    CommonR.string.metadata_worker_complete_text,
                    successCount,
                    totalCount,
                )
            val notification =
                NotificationCompat
                    .Builder(appContext, CHANNEL_ID)
                    .setContentTitle(localizedContext.getString(CommonR.string.metadata_worker_complete_title))
                    .setContentText(text)
                    .setSmallIcon(android.R.drawable.ic_popup_sync)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setDefaults(NotificationCompat.DEFAULT_ALL)
                    .setAutoCancel(true)
                    .apply {
                        if (pendingIntent != null) setContentIntent(pendingIntent)
                    }.build()

            val notificationManager =
                appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(COMPLETION_NOTIFICATION_ID, notification)
        }

        private fun createForegroundInfo(
            processedCount: Int,
            totalCount: Int,
            title: String,
        ): ForegroundInfo {
            val localizedContext = AppLocaleManager.localizedContext(appContext)
            val cancelIntent =
                androidx.work.WorkManager
                    .getInstance(appContext)
                    .createCancelPendingIntent(id)
            val text =
                localizedContext.resources.getQuantityString(
                    CommonR.plurals.metadata_worker_progress_text,
                    totalCount,
                    processedCount,
                    totalCount,
                )
            val notification =
                NotificationCompat
                    .Builder(appContext, CHANNEL_ID)
                    .setContentTitle(localizedContext.getString(CommonR.string.metadata_worker_progress_title))
                    .setContentText(text)
                    .setSubText(title)
                    .setSmallIcon(android.R.drawable.ic_popup_sync)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setProgress(totalCount, processedCount, totalCount == 0)
                    .addAction(android.R.drawable.ic_delete, localizedContext.getString(CommonR.string.action_cancel), cancelIntent)
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
                        localizedContext.getString(CommonR.string.metadata_worker_channel_name),
                        NotificationManager.IMPORTANCE_LOW,
                    ).apply {
                        description = localizedContext.getString(CommonR.string.metadata_worker_channel_description)
                    }
                val notificationManager =
                    appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
            }
        }

        companion object {
            const val WORK_NAME = "MetadataBatchWorker"
            const val KEY_FORCE_SCAN = "key_force_scan"
            const val KEY_PROCESSED_COUNT = "key_processed_count"
            const val KEY_TOTAL_COUNT = "key_total_count"
            const val KEY_CURRENT_TITLE = "key_current_title"
            private const val TAG = "MetadataBatchWorker"
            private const val CHANNEL_ID = "metadata_batch_channel"

            @Suppress("MagicNumber")
            private const val NOTIFICATION_ID = 2002

            @Suppress("MagicNumber")
            private const val COMPLETION_NOTIFICATION_ID = 2003
        }
    }
