package com.erhu.erhu.feature.library.vybe

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.StatFs
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.erhu.erhu.core.common.locale.AppLocaleManager
import com.erhu.erhu.core.datastore.repository.ThemeRepository
import com.erhu.erhu.feature.library.R
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile

/** WorkManager owns retries/process death; a private range download survives interrupted jobs. */
@HiltWorker
class VybeModelWorker
    @AssistedInject
    constructor(
        @Assisted private val context: Context,
        @Assisted parameters: WorkerParameters,
        private val store: VybeModelStore,
        private val themeRepository: ThemeRepository,
    ) : CoroutineWorker(context, parameters) {
        @Suppress("LongMethod", "CyclomaticComplexMethod", "NestedBlockDepth", "TooGenericExceptionCaught")
        override suspend fun doWork(): Result =
            withContext(Dispatchers.IO) {
                // Requests scheduled by earlier versions never had explicit download consent.
                if (!inputData.getBoolean(VybeModelStore.USER_REQUESTED, false) ||
                    !themeRepository.vybeEnabled.filterNotNull().first()
                ) {
                    return@withContext Result.failure()
                }
                val staging = File(store.directory, "embedding_gemma.task.installing")
                try {
                    if (store.isInstalled()) return@withContext Result.success()
                    setForeground(foreground(0))
                    staging.delete()
                    val remaining = (VybeModelStore.ARCHIVE_BYTES - store.archive.length()).coerceAtLeast(0)
                    if (StatFs(store.directory.path).availableBytes < remaining + VybeModelStore.MODEL_BYTES + STORAGE_MARGIN) {
                        return@withContext failure(R.string.vybe_download_storage_error)
                    }
                    download()
                    setProgress(workDataOf("bytes" to VybeModelStore.ARCHIVE_BYTES, "installing" to true))
                    if (VybeModelStore.sha256(store.archive) != VybeModelStore.ARCHIVE_SHA256) {
                        store.archive.delete()
                        return@withContext failure(R.string.vybe_download_invalid_error)
                    }
                    ZipFile(store.archive).use { zip ->
                        val model = zip.getEntry("embedding_gemma.task")
                        if (model == null ||
                            model.size != VybeModelStore.MODEL_BYTES
                        ) {
                            return@withContext failure(R.string.vybe_download_invalid_error)
                        }
                        zip.getInputStream(model).use { input ->
                            FileOutputStream(staging).use { output ->
                                val buffer = ByteArray(DOWNLOAD_BUFFER_BYTES)
                                var copied = 0L
                                while (true) {
                                    ensureActive()
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    copied += count
                                    if (copied >
                                        VybeModelStore.MODEL_BYTES
                                    ) {
                                        return@withContext failure(R.string.vybe_download_invalid_error)
                                    }
                                    output.write(buffer, 0, count)
                                }
                                output.fd.sync()
                            }
                        }
                        // Retain the upstream licence material alongside the installed model.
                        listOf("README.md", "NOTICE.txt", "GEMMA_TERMS.html").forEach { name ->
                            val entry = zip.getEntry(name) ?: throw IOException("Missing model licence")
                            zip.getInputStream(entry).use { input -> File(store.directory, name).outputStream().use { input.copyTo(it) } }
                        }
                    }
                    if (staging.length() != VybeModelStore.MODEL_BYTES || VybeModelStore.sha256(staging) != VybeModelStore.MODEL_SHA256) {
                        return@withContext failure(R.string.vybe_download_invalid_error)
                    }
                    ensureActive()
                    Files.move(staging.toPath(), store.model.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                    if (!store.isInstalled()) return@withContext failure(R.string.vybe_download_invalid_error)
                    store.archive.delete()
                    Result.success()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (missing: MissingAssetException) {
                    android.util.Log.w("VybeModel", "Published model unavailable", missing)
                    failure(R.string.vybe_download_missing_error)
                } catch (error: IOException) {
                    android.util.Log.w("VybeModel", "Model transfer interrupted", error)
                    if (StatFs(store.directory.path).availableBytes < STORAGE_MARGIN) {
                        failure(R.string.vybe_download_storage_error)
                    } else if (runAttemptCount < MAX_RETRIES) {
                        Result.retry()
                    } else {
                        failure(R.string.vybe_download_network_error)
                    }
                } catch (error: Exception) {
                    android.util.Log.w("VybeModel", "Model installation failed", error)
                    failure(R.string.vybe_download_install_error)
                } finally {
                    staging.delete()
                }
            }

        @Suppress("ThrowsCount", "CyclomaticComplexMethod", "LongMethod", "NestedBlockDepth")
        private suspend fun download() {
            if (store.archive.length() == VybeModelStore.ARCHIVE_BYTES) return
            if (store.archive.length() > VybeModelStore.ARCHIVE_BYTES) store.archive.delete()
            val offset = store.archive.length()
            val connection = URL(VybeModelStore.DOWNLOAD_URL).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.setRequestProperty("Accept-Encoding", "identity")
                connection.setRequestProperty("User-Agent", "Erhu-VYBE")
                if (offset > 0) connection.setRequestProperty("Range", "bytes=$offset-")
                val code = connection.responseCode
                if (code == HttpURLConnection.HTTP_NOT_FOUND) throw MissingAssetException()
                if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_PARTIAL) throw IOException("Download HTTP $code")
                val resume = code == HttpURLConnection.HTTP_PARTIAL && offset > 0
                if (code == HttpURLConnection.HTTP_PARTIAL &&
                    !connection.getHeaderField("Content-Range").orEmpty().startsWith("bytes $offset-")
                ) {
                    store.archive.delete()
                    throw IOException("Invalid range response")
                }
                val length = connection.contentLengthLong
                if (length >= 0 && length + (if (resume) offset else 0) != VybeModelStore.ARCHIVE_BYTES) {
                    throw MissingAssetException()
                }
                connection.inputStream.use { input ->
                    FileOutputStream(store.archive, resume).use { output ->
                        val buffer = ByteArray(DOWNLOAD_BUFFER_BYTES)
                        var bytes = if (resume) offset else 0L
                        var lastUpdate = 0L
                        while (true) {
                            kotlinx.coroutines.currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            bytes += count
                            if (bytes > VybeModelStore.ARCHIVE_BYTES) {
                                store.archive.delete()
                                throw MissingAssetException()
                            }
                            output.write(buffer, 0, count)
                            val now = android.os.SystemClock.elapsedRealtime()
                            if (now - lastUpdate >= PROGRESS_INTERVAL_MS) {
                                setProgress(workDataOf("bytes" to bytes))
                                setForeground(foreground((bytes * PERCENT_MAX / VybeModelStore.ARCHIVE_BYTES).toInt()))
                                lastUpdate = now
                            }
                        }
                        output.fd.sync()
                        if (bytes != VybeModelStore.ARCHIVE_BYTES) throw IOException("Interrupted download")
                    }
                }
            } finally {
                connection.disconnect()
            }
        }

        private fun failure(message: Int) = Result.failure(workDataOf("error" to message))

        private fun foreground(percent: Int): ForegroundInfo {
            val localized = AppLocaleManager.localizedContext(context)
            val title = localized.getString(R.string.vybe_download_title)
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL, title, NotificationManager.IMPORTANCE_LOW))
            val notification =
                NotificationCompat
                    .Builder(context, CHANNEL)
                    .setSmallIcon(android.R.drawable.stat_sys_download)
                    .setContentTitle(title)
                    .setContentText("$percent%")
                    .setProgress(PERCENT_MAX, percent, false)
                    .setOngoing(true)
                    .setSilent(true)
                    .addAction(
                        0,
                        localized.getString(com.erhu.erhu.core.common.R.string.action_cancel),
                        androidx.work.WorkManager
                            .getInstance(context)
                            .createCancelPendingIntent(id),
                    ).build()
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ForegroundInfo(NOTIFICATION, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                ForegroundInfo(NOTIFICATION, notification)
            }
        }

        private class MissingAssetException : IOException()

        private companion object {
            const val CHANNEL = "vybe_model_download"
            const val NOTIFICATION = 7304
            const val STORAGE_MARGIN = 32L * 1024 * 1024
            const val MAX_RETRIES = 3
        }
    }

private const val DOWNLOAD_BUFFER_BYTES = 64 * 1024
private const val CONNECT_TIMEOUT_MS = 20_000
private const val READ_TIMEOUT_MS = 30_000
private const val PROGRESS_INTERVAL_MS = 1000L
private const val PERCENT_MAX = 100
