package com.lochanpthakke.empee3.feature.transfer.server

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/** In-memory destination: [existing] are the files already "on the phone" (name to size). */
class FakeSink(
    val existing: MutableMap<String, Long> = mutableMapOf(),
    var freeBytes: Long = Long.MAX_VALUE,
    /** Throw on write once this many bytes have been written (simulates a full disk). */
    var failWriteAfter: Long = Long.MAX_VALUE,
) : UploadSink {
    val committed = mutableMapOf<String, ByteArray>()
    val pending = mutableListOf<Pending>()
    private val reserved = mutableSetOf<String>()

    @Synchronized
    override fun check(
        name: String,
        sizeBytes: Long,
    ): UploadCheck =
        when {
            existing[name] == sizeBytes -> UploadCheck.Duplicate
            sizeBytes > freeBytes -> UploadCheck.InsufficientSpace
            else -> UploadCheck.Accept(freeName(name))
        }

    @Synchronized
    override fun open(
        name: String,
        sizeBytes: Long,
    ): PendingUploadOrDuplicate =
        when (val c = check(name, sizeBytes)) {
            UploadCheck.Duplicate -> PendingUploadOrDuplicate.Duplicate
            UploadCheck.InsufficientSpace -> PendingUploadOrDuplicate.InsufficientSpace
            is UploadCheck.Accept -> {
                reserved += c.savedName
                PendingUploadOrDuplicate.Opened(Pending(c.savedName).also { pending += it })
            }
        }

    private fun freeName(name: String): String {
        if (name !in existing && name !in reserved && name !in committed) return name
        var n = 2
        while (true) {
            val candidate = TransferFiles.numbered(name, n++)
            if (candidate !in existing && candidate !in reserved && candidate !in committed) return candidate
        }
    }

    inner class Pending(
        override val savedName: String,
    ) : PendingUpload {
        val bytes = ByteArrayOutputStream()
        private val finished = AtomicBoolean()
        var commits = 0
        var aborts = 0

        override fun write(
            buffer: ByteArray,
            offset: Int,
            length: Int,
        ) {
            if (bytes.size() + length > failWriteAfter) throw IOException("No space left on device")
            bytes.write(buffer, offset, length)
        }

        override fun commit() {
            if (!finished.compareAndSet(false, true)) return
            commits++
            synchronized(this@FakeSink) {
                committed[savedName] = bytes.toByteArray()
                existing[savedName] = bytes.size().toLong()
                reserved -= savedName
            }
        }

        override fun abort() {
            if (!finished.compareAndSet(false, true)) return
            aborts++
            synchronized(this@FakeSink) { reserved -= savedName }
        }
    }
}

/** A minimal valid MP3 body (ID3 header + padding) of [size] bytes. */
fun mp3Bytes(size: Int): ByteArray = ByteArray(size).also { "ID3".toByteArray().copyInto(it) }
