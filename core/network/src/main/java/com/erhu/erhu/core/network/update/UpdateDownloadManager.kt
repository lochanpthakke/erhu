package com.erhu.erhu.core.network.update

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed interface UpdateDownloadState {
    data object Idle : UpdateDownloadState

    data class Downloading(
        val bytes: Long,
        val total: Long?,
    ) : UpdateDownloadState

    data object Verifying : UpdateDownloadState

    data class Ready(
        val file: File,
        val update: UpdateCheck.Available,
    ) : UpdateDownloadState

    data class Failed(
        val message: String,
    ) : UpdateDownloadState

    data object Cancelled : UpdateDownloadState
}

/** One app-wide transfer; leaving/recreating the settings page does not duplicate downloads. */
@Singleton
class UpdateDownloadManager internal constructor(
    private val directory: File,
    private val client: OkHttpClient,
    private val validate: (File, UpdateCheck.Available) -> Unit,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        client: OkHttpClient,
        validator: ApkUpdateValidator,
    ) : this(
        File(context.cacheDir, "updates"),
        client
            .newBuilder()
            .callTimeout(0, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followSslRedirects(false)
            .build(),
        { file, update ->
            validator.validate(file, update)
            Unit
        },
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val _state = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    val state = _state.asStateFlow()

    @Synchronized fun start(update: UpdateCheck.Available) {
        if (job?.isCompleted == false) return
        job =
            scope.launch {
                val partial = File(directory, "update.partial.apk")
                val apk = File(directory, "update.apk")
                try {
                    require(trustedReleaseAsset(update.asset)) { "The APK is not an official release asset." }
                    require(directory.isDirectory || directory.mkdirs()) { "Could not create update download storage." }
                    partial.delete()
                    apk.delete()
                    _state.value = UpdateDownloadState.Downloading(0, update.asset.size.takeIf { it > 0 })
                    transfer(update, partial)
                    _state.value = UpdateDownloadState.Verifying
                    validate(partial, update)
                    currentCoroutineContext().ensureActive()
                    require(partial.renameTo(apk)) { "Could not finalize the downloaded APK." }
                    _state.value = UpdateDownloadState.Ready(apk, update)
                } catch (e: CancellationException) {
                    _state.value = UpdateDownloadState.Cancelled
                    throw e
                } catch (e: Exception) {
                    if (!currentCoroutineContext().isActive) {
                        _state.value = UpdateDownloadState.Cancelled
                        throw CancellationException("Update download cancelled").apply { initCause(e) }
                    }
                    _state.value = UpdateDownloadState.Failed(e.message ?: "Update download failed. Please retry.")
                } finally {
                    partial.delete()
                }
            }
    }

    @Synchronized fun cancel() {
        job?.cancel()
    }

    @Synchronized fun reset() {
        if (job?.isCompleted == false) return
        (_state.value as? UpdateDownloadState.Ready)?.file?.delete()
        _state.value = UpdateDownloadState.Idle
    }

    private suspend fun transfer(
        update: UpdateCheck.Available,
        target: File,
    ) = coroutineScope {
        val call =
            client.newCall(
                Request
                    .Builder()
                    .url(update.asset.browserDownloadUrl)
                    .header("User-Agent", "erhu-update-checker")
                    .build(),
            )
        // Close the socket immediately when cancellation happens during a blocking stream read.
        val cancellation =
            launch {
                try {
                    awaitCancellation()
                } finally {
                    call.cancel()
                }
            }
        try {
            call.await().use { response ->
                if (!response.isSuccessful) throw IOException("APK download failed (HTTP ${response.code}). Please retry.")
                val url = response.request.url
                require(url.isHttps && (url.host == "github.com" || url.host.endsWith(".githubusercontent.com"))) {
                    "The APK download redirected to an unexpected server."
                }
                val body = response.body ?: throw IOException("The APK response was empty.")
                val expected = update.asset.size.takeIf { it > 0 } ?: body.contentLength().takeIf { it > 0 }
                require(expected == null || expected <= MAX_BYTES) { "The release APK exceeds the supported download size." }
                val digest = MessageDigest.getInstance("SHA-256")
                var bytes = 0L
                var lastProgress = 0L
                target.outputStream().use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            bytes += count
                            require(bytes <= MAX_BYTES) { "The APK download is unexpectedly large." }
                            output.write(buffer, 0, count)
                            digest.update(buffer, 0, count)
                            val now = System.nanoTime()
                            if (now - lastProgress >= 200_000_000) {
                                _state.value = UpdateDownloadState.Downloading(bytes, expected)
                                lastProgress = now
                            }
                        }
                    }
                }
                require(bytes > 0 && (expected == null || bytes == expected)) { "The APK download was incomplete. Please retry." }
                val expectedHash =
                    update.asset.digest
                        ?.takeIf { it.startsWith("sha256:") }
                        ?.substringAfter(':')
                val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
                require(expectedHash == null || actualHash.equals(expectedHash, true)) {
                    "The downloaded APK checksum did not match GitHub's release asset."
                }
                _state.value = UpdateDownloadState.Downloading(bytes, expected)
            }
        } finally {
            cancellation.cancel()
            call.cancel()
        }
    }

    companion object {
        private const val MAX_BYTES = 512L * 1024 * 1024
    }
}

private suspend fun Call.await(): Response =
    suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(
            object : Callback {
                override fun onFailure(
                    call: Call,
                    e: IOException,
                ) {
                    if (!continuation.isCancelled) continuation.resumeWithException(e)
                }

                override fun onResponse(
                    call: Call,
                    response: Response,
                ) {
                    continuation.resume(response) { _, value, _ -> value.close() }
                }
            },
        )
    }
