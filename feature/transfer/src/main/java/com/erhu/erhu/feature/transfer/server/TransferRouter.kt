package com.erhu.erhu.feature.transfer.server

import java.io.IOException
import java.io.InputStream
import java.net.URLDecoder
import java.util.concurrent.Semaphore

/** Limits of one transfer session. */
data class TransferLimits(
    val maxFileBytes: Long = DEFAULT_MAX_FILE_BYTES,
    val maxConcurrentUploads: Int = DEFAULT_MAX_CONCURRENT_UPLOADS,
    val maxConnections: Int = DEFAULT_MAX_CONNECTIONS,
    val readTimeoutMs: Int = DEFAULT_READ_TIMEOUT_MS,
) {
    companion object {
        /** 2 GB: far above any ordinary audio file. */
        const val DEFAULT_MAX_FILE_BYTES = 2L * 1024 * 1024 * 1024
        const val DEFAULT_MAX_CONCURRENT_UPLOADS = 2
        const val DEFAULT_MAX_CONNECTIONS = 8

        /** A connection that sends nothing for this long is dropped (and its partial file removed). */
        const val DEFAULT_READ_TIMEOUT_MS = 30_000
    }
}

/** What the session coordinator wants to know about (activity for the idle timeout, saved files for the library). */
interface TransferEvents {
    fun onRequest() {}

    fun onUploadStarted() {}

    fun onUploadFinished() {}

    fun onFileSaved() {}

    companion object {
        val NONE = object : TransferEvents {}
    }
}

/**
 * The transfer page's routes. Only these exist:
 *
 * - `GET /` - the upload page (no secrets in it);
 * - `POST /api/pair` - trades the typed pairing code for the session token;
 * - `POST /api/check` - would this file be saved, skipped as a duplicate or refused? (no bytes yet);
 * - `POST /api/upload` - one file as the raw request body.
 *
 * Every `/api` request must come for this exact host and port (blocks DNS rebinding) and, from a
 * browser, from this page's own origin. The token travels in a custom header, which a page on any
 * other site cannot send without a CORS preflight this server never grants. State changes need the
 * token of the open session. Nothing here lists, reads or deletes files.
 */
class TransferRouter(
    private val session: TransferSession,
    private val hostPort: String,
    private val sink: UploadSink,
    private val registry: TransferRegistry,
    private val limits: TransferLimits = TransferLimits(),
    private val events: TransferEvents = TransferEvents.NONE,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val uploadSlots = Semaphore(limits.maxConcurrentUploads)

    /** The response to send, or null when the connection is already gone (nothing can be sent). */
    fun handle(
        request: HttpRequest,
        body: InputStream,
    ): HttpResponse? {
        events.onRequest()
        if (!request
                .header(
                    "host",
                ).equals(hostPort, ignoreCase = true)
        ) {
            return HttpResponse.error(HttpStatus.MISDIRECTED_REQUEST, "Wrong host")
        }
        return when (request.path) {
            "/" -> if (request.method == "GET" || request.method == "HEAD") page() else methodNotAllowed()
            "/api/pair", "/api/check", "/api/upload" -> api(request, body)
            else -> HttpResponse.error(HttpStatus.NOT_FOUND, "Not found")
        }
    }

    private fun page(): HttpResponse = HttpResponse(HttpStatus.OK, "text/html; charset=utf-8", UploadPage.HTML.toByteArray(Charsets.UTF_8))

    private fun methodNotAllowed() = HttpResponse.error(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed")

    private fun api(
        request: HttpRequest,
        body: InputStream,
    ): HttpResponse? {
        val origin = request.header("origin")
        return when {
            request.method != "POST" -> methodNotAllowed()
            origin != null && origin != "http://$hostPort" -> HttpResponse.error(HttpStatus.FORBIDDEN, "Cross-origin request refused")
            request.header(CLIENT_HEADER) != CLIENT_VALUE -> HttpResponse.error(HttpStatus.FORBIDDEN, "Use the transfer page")
            request.path == "/api/pair" -> pair(request)
            !session.isAuthorized(
                request.header(TOKEN_HEADER),
                clock(),
            ) -> HttpResponse.error(HttpStatus.UNAUTHORIZED, "This transfer session has ended or the link is wrong")
            request.path == "/api/check" -> check(request)
            else -> upload(request, body)
        }
    }

    private fun pair(request: HttpRequest): HttpResponse =
        when (val result = session.pair(request.header(CODE_HEADER), clock())) {
            is PairResult.Paired -> HttpResponse.json(HttpStatus.OK, "status" to "ok", "token" to result.token)
            PairResult.WrongCode -> HttpResponse.error(HttpStatus.FORBIDDEN, "Wrong code. Check the code shown on the phone.")
            PairResult.Locked ->
                HttpResponse.error(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Too many wrong codes. Scan the QR code, or stop and start the transfer on the phone.",
                )
            PairResult.Closed -> HttpResponse.error(HttpStatus.UNAUTHORIZED, "This transfer session has ended")
        }

    @Suppress("ReturnCount")
    private fun check(request: HttpRequest): HttpResponse {
        val name = TransferFiles.sanitize(decodedName(request)) ?: return unsupported()
        val size =
            request.header(SIZE_HEADER)?.toLongOrNull()?.takeIf { it > 0 }
                ?: return HttpResponse.error(HttpStatus.BAD_REQUEST, "Missing file size")
        if (size > limits.maxFileBytes) return tooLarge()
        return when (val result = sink.check(name, size)) {
            is UploadCheck.Accept -> HttpResponse.json(HttpStatus.OK, "status" to "ok", "savedName" to result.savedName)
            UploadCheck.Duplicate -> HttpResponse.json(HttpStatus.OK, "status" to "duplicate", "message" to DUPLICATE_MESSAGE)
            UploadCheck.InsufficientSpace -> noSpace()
        }
    }

    @Suppress("ReturnCount")
    private fun upload(
        request: HttpRequest,
        body: InputStream,
    ): HttpResponse? {
        val length = request.contentLength ?: return HttpResponse.error(HttpStatus.LENGTH_REQUIRED, "Content-Length required")
        if (length > limits.maxFileBytes) return tooLarge()
        if (length == 0L) return HttpResponse.error(HttpStatus.BAD_REQUEST, "Empty file")
        val name = TransferFiles.sanitize(decodedName(request)) ?: return unsupported()
        if (!uploadSlots.tryAcquire()) {
            return HttpResponse.error(
                HttpStatus.TOO_MANY_REQUESTS,
                "Busy: the phone is already receiving files, this one will be retried",
            )
        }
        events.onUploadStarted()
        try {
            return when (val opened = sink.open(name, length)) {
                is PendingUploadOrDuplicate.Opened -> receive(opened.upload, length, body)
                PendingUploadOrDuplicate.Duplicate -> {
                    registry.duplicate(registry.begin(name, length))
                    // Read and drop the bytes, so the browser receives the answer instead of a broken connection.
                    if (skip(
                            body,
                            length,
                        )
                    ) {
                        HttpResponse.json(HttpStatus.OK, "status" to "duplicate", "message" to DUPLICATE_MESSAGE)
                    } else {
                        null
                    }
                }
                PendingUploadOrDuplicate.InsufficientSpace -> {
                    registry.failed(registry.begin(name, length), NO_SPACE_MESSAGE)
                    noSpace()
                }
            }
        } catch (
            @Suppress("SwallowedException") e: IOException,
        ) {
            registry.failed(registry.begin(name, length), "Could not create the file on the phone: ${e.message}")
            return HttpResponse.error(HttpStatus.INTERNAL_ERROR, "Could not create the file on the phone")
        } finally {
            uploadSlots.release()
            events.onUploadFinished()
        }
    }

    /** Streams [length] bytes of [body] into [upload]; visible only after a successful commit. */
    @Suppress("ReturnCount", "LongMethod")
    private fun receive(
        upload: PendingUpload,
        length: Long,
        body: InputStream,
    ): HttpResponse? {
        val id = registry.begin(upload.savedName, length)
        val buffer = ByteArray(BUFFER_BYTES)
        var received = 0L
        var lastReported = 0L
        try {
            // The first bytes decide whether this really is the audio format its name claims.
            val headerLength = readAtMost(body, buffer, minOf(length, TransferFiles.SIGNATURE_BYTES.toLong()).toInt())
            if (headerLength < 0) return cancelled(upload, id)
            if (!TransferFiles.looksLikeAudio(upload.savedName, buffer, headerLength)) {
                upload.abort()
                registry.failed(id, "Not a valid ${TransferFiles.extensionOf(upload.savedName).uppercase()} audio file")
                return if (skip(body, length - headerLength)) unsupported() else null
            }
            if (!writeOrFail(upload, id, buffer, headerLength)) return writeFailed(body, length - headerLength)
            received = headerLength.toLong()
            while (received < length) {
                val n = body.read(buffer, 0, minOf(buffer.size.toLong(), length - received).toInt())
                if (n < 0) return cancelled(upload, id)
                if (!writeOrFail(upload, id, buffer, n)) return writeFailed(body, length - received - n)
                received += n
                if (received - lastReported >= PROGRESS_STEP_BYTES) {
                    registry.progress(id, received)
                    lastReported = received
                }
            }
        } catch (
            @Suppress("SwallowedException") e: IOException,
        ) {
            // The browser cancelled, the connection dropped or stalled past the read timeout.
            return cancelled(upload, id)
        }
        registry.finalizing(id)
        return try {
            upload.commit()
            registry.completed(id, upload.savedName)
            events.onFileSaved()
            HttpResponse.json(HttpStatus.OK, "status" to "saved", "savedName" to upload.savedName)
        } catch (e: IOException) {
            upload.abort()
            registry.failed(id, "Received, but the phone could not finish saving it: ${e.message}")
            HttpResponse.error(HttpStatus.INTERNAL_ERROR, "The phone could not finish saving the file")
        }
    }

    private fun writeOrFail(
        upload: PendingUpload,
        id: Long,
        buffer: ByteArray,
        length: Int,
    ): Boolean =
        try {
            upload.write(buffer, 0, length)
            true
        } catch (e: IOException) {
            upload.abort()
            registry.failed(id, "Could not write to the phone's storage (full or unavailable): ${e.message}")
            false
        }

    private fun writeFailed(
        body: InputStream,
        remaining: Long,
    ): HttpResponse? =
        if (skip(body, remaining)) HttpResponse.error(HttpStatus.INSUFFICIENT_STORAGE, "The phone could not store the file") else null

    private fun cancelled(
        upload: PendingUpload,
        id: Long,
    ): HttpResponse? {
        upload.abort()
        registry.cancelled(id)
        return null
    }

    @Suppress("SwallowedException") // an undecodable name is simply no name (refused as unsupported)
    private fun decodedName(request: HttpRequest): String? =
        request.header(NAME_HEADER)?.let {
            try {
                URLDecoder.decode(it, "UTF-8")
            } catch (e: IllegalArgumentException) {
                null
            }
        }

    private fun unsupported() =
        HttpResponse.error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Not a supported audio file (MP3, FLAC, M4A, AAC, OGG, Opus or WAV)")

    private fun tooLarge() =
        HttpResponse.error(HttpStatus.PAYLOAD_TOO_LARGE, "File is larger than ${limits.maxFileBytes / BYTES_PER_MB} MB")

    private fun noSpace() = HttpResponse.error(HttpStatus.INSUFFICIENT_STORAGE, NO_SPACE_MESSAGE)

    companion object {
        const val TOKEN_HEADER = "X-Transfer-Token"
        const val CODE_HEADER = "X-Pairing-Code"
        const val NAME_HEADER = "X-File-Name"
        const val SIZE_HEADER = "X-File-Size"

        /** Sent by the transfer page on every API call; another site cannot send it without a preflight. */
        const val CLIENT_HEADER = "X-Empee3-Transfer"
        const val CLIENT_VALUE = "1"

        private const val BUFFER_BYTES = 64 * 1024
        private const val PROGRESS_STEP_BYTES = 256L * 1024
        private const val BYTES_PER_MB = 1024L * 1024
        private const val DUPLICATE_MESSAGE = "Already on the phone: skipped, nothing was overwritten"
        private const val NO_SPACE_MESSAGE = "Not enough free space on the phone"

        /** Reads up to [count] bytes into [buffer]; returns how many, or -1 if the stream ended first. */
        internal fun readAtMost(
            input: InputStream,
            buffer: ByteArray,
            count: Int,
        ): Int {
            var total = 0
            while (total < count) {
                val n = input.read(buffer, total, count - total)
                if (n < 0) return if (total == count) total else -1
                total += n
            }
            return total
        }

        /** Discards [count] bytes; false when the connection ended or failed first. */
        @Suppress("SwallowedException")
        internal fun skip(
            input: InputStream,
            count: Long,
        ): Boolean {
            val scratch = ByteArray(BUFFER_BYTES)
            var left = count
            return try {
                while (left > 0) {
                    val n = input.read(scratch, 0, minOf(scratch.size.toLong(), left).toInt())
                    if (n < 0) return false
                    left -= n
                }
                true
            } catch (e: IOException) {
                false
            }
        }
    }
}
