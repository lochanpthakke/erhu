package com.erhu.erhu.feature.library.vybe

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.erhu.erhu.core.common.locale.AppLocaleManager
import com.erhu.erhu.feature.library.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** One exact published bundle, shared by setup and the existing inference engine. */
@Singleton
class VybeModelStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        val directory: File get() = File(context.filesDir, "vybe").apply { mkdirs() }
        val model: File get() = File(directory, "embedding_gemma.task")
        val archive: File get() = File(directory, "model.zip.part")

        /** A private marker avoids hashing 175 MiB on every visit, while noticing file replacement. */
        @Suppress("ReturnCount")
        fun isInstalled(): Boolean {
            if (!model.isFile || model.length() != MODEL_BYTES) return false
            val marker = File(directory, "verified-model")
            val identity = "$MODEL_SHA256|${model.length()}|${model.lastModified()}"
            if (marker.isFile && marker.readText() == identity) return true
            if (sha256(model) != MODEL_SHA256) return false
            try {
                marker.writeText(identity)
            } catch (error: java.io.IOException) {
                // Verification succeeded; a full disk must not reject an already correct model.
                android.util.Log.w("VybeModel", "Could not cache model verification", error)
            }
            return true
        }

        @Suppress("ReturnCount")
        suspend fun ensure(onStatus: (VybeStatus) -> Unit): Boolean {
            if (withContext(Dispatchers.IO) { isInstalled() }) return true
            val manager = WorkManager.getInstance(context)
            val request =
                OneTimeWorkRequestBuilder<VybeModelWorker>()
                    .setInputData(workDataOf(USER_REQUESTED to true))
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setBackoffCriteria(BackoffPolicy.LINEAR, MODEL_RETRY_SECONDS, TimeUnit.SECONDS)
                    .build()
            val operation = manager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
            withContext(Dispatchers.IO) { operation.result.get() }
            val existing = manager.getWorkInfosForUniqueWorkFlow(WORK_NAME).first()
            val activeId =
                existing.firstOrNull { it.id == request.id }?.id
                    ?: existing.firstOrNull { !it.state.isFinished }?.id
                    ?: existing.firstOrNull()?.id ?: return false
            val result =
                manager
                    .getWorkInfoByIdFlow(activeId)
                    .onEach { info ->
                        if (info != null && !info.state.isFinished) {
                            onStatus(
                                VybeStatus.Downloading(
                                    info.progress.getLong("bytes", 0),
                                    ARCHIVE_BYTES,
                                    info.progress.getBoolean("installing", false),
                                    info.state == WorkInfo.State.ENQUEUED,
                                ),
                            )
                        }
                    }.first { it?.state?.isFinished == true } ?: return false
            val installed = withContext(Dispatchers.IO) { isInstalled() }
            if (result.state == WorkInfo.State.SUCCEEDED && installed) return true
            val error = result.outputData.getInt("error", R.string.vybe_download_network_error)
            onStatus(
                if (result.state == WorkInfo.State.CANCELLED) {
                    VybeStatus.NoModel
                } else {
                    VybeStatus.Failed(AppLocaleManager.localizedContext(context).getString(error))
                },
            )
            return false
        }

        fun cancel() {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }

        companion object {
            const val WORK_NAME = "vybe-exact-model-manual"
            const val USER_REQUESTED = "user_requested"
            const val ASSET = "vybe-embeddinggemma-300m-int4int8.zip"

            // Updated to the release being prepared before publication; never an expiring CDN URL.
            const val RELEASE_TAG = "erhu-v1.0.20-beta.40"
            const val DOWNLOAD_URL = "https://github.com/lochanpthakke/erhu/releases/download/$RELEASE_TAG/$ASSET"
            const val ARCHIVE_BYTES = 151298779L
            const val ARCHIVE_SHA256 = "c6d522f014c00910726afbb43b6756026ca35f4000741953d7800b2ff5e45f1f"
            const val MODEL_BYTES = 183816181L
            const val MODEL_SHA256 = "913b7a1edc7c7c3d1da3979ec1d0648ed9e0a370f181bb59ab177ca4b97707ad"

            fun sha256(file: File): String {
                val digest = MessageDigest.getInstance("SHA-256")
                file.inputStream().buffered().use { input ->
                    val buffer = ByteArray(MODEL_BUFFER_BYTES)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        digest.update(buffer, 0, read)
                    }
                }
                return digest.digest().joinToString("") { "%02x".format(it) }
            }
        }
    }

private const val MODEL_RETRY_SECONDS = 30L
private const val MODEL_BUFFER_BYTES = 64 * 1024
