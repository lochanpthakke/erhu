package com.erhu.erhu.feature.transfer

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.erhu.erhu.feature.transfer.server.PendingUpload
import com.erhu.erhu.feature.transfer.server.PendingUploadOrDuplicate
import com.erhu.erhu.feature.transfer.server.TransferFiles
import com.erhu.erhu.feature.transfer.server.UploadCheck
import com.erhu.erhu.feature.transfer.server.UploadSink
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Saves received files to `Music/erhu/` through the supported storage APIs, where the existing
 * MediaStore scanner finds them like any other music:
 *
 * - Android 10+: a MediaStore audio entry created as *pending* (invisible to every app and to the
 *   library) and published only when the upload commits; an abort deletes it.
 * - Android 8-9: a hidden `.part` file in the same folder, renamed when complete and then handed to
 *   the media scanner; an abort deletes it.
 *
 * Existing files are never replaced: the same name and size is a duplicate (skipped), the same name
 * with other content gets a numbered name.
 */
internal class MediaStoreUploadSink(
    private val context: Context,
) : UploadSink {
    private val resolver: ContentResolver = context.contentResolver
    private val reserved = mutableSetOf<String>()

    @Synchronized
    override fun check(
        name: String,
        sizeBytes: Long,
    ): UploadCheck {
        if (sizeBytes + FREE_SPACE_MARGIN > freeBytes()) return UploadCheck.InsufficientSpace
        val existing = existingFiles()
        return if (existing[name.lowercase()] == sizeBytes) UploadCheck.Duplicate else UploadCheck.Accept(freeName(name, existing.keys))
    }

    @Synchronized
    override fun open(
        name: String,
        sizeBytes: Long,
    ): PendingUploadOrDuplicate =
        when (val check = check(name, sizeBytes)) {
            UploadCheck.Duplicate -> PendingUploadOrDuplicate.Duplicate
            UploadCheck.InsufficientSpace -> PendingUploadOrDuplicate.InsufficientSpace
            is UploadCheck.Accept -> {
                reserved += check.savedName.lowercase()
                try {
                    PendingUploadOrDuplicate.Opened(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) openPending(check.savedName) else openLegacy(check.savedName),
                    )
                } catch (e: IOException) {
                    reserved -= check.savedName.lowercase()
                    throw e
                } catch (e: SecurityException) {
                    reserved -= check.savedName.lowercase()
                    throw IOException("No permission to write to Music/$FOLDER", e)
                }
            }
        }

    /** Removes partial files a previous session left behind (process death mid-upload). */
    fun cleanUpLeftovers() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                deleteOwnPendingEntries()
            } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                legacyDir().listFiles { file -> file.name.startsWith(".") && file.name.endsWith(PART_SUFFIX) }?.forEach { it.delete() }
            }
            // Android 10: pending entries cannot be queried here; MediaStore removes them itself after a week.
        }
    }

    @Synchronized
    private fun release(name: String) {
        reserved -= name.lowercase()
    }

    private fun freeName(
        name: String,
        taken: Set<String>,
    ): String {
        fun isFree(candidate: String) = candidate.lowercase() !in taken && candidate.lowercase() !in reserved
        if (isFree(name)) return name
        var n = 2
        while (!isFree(TransferFiles.numbered(name, n))) n++
        return TransferFiles.numbered(name, n)
    }

    private fun freeBytes(): Long =
        runCatching { StatFs(Environment.getExternalStorageDirectory().path).availableBytes }.getOrDefault(Long.MAX_VALUE)

    /** Lower-cased display name to size of the files already in the transfer folder. */
    private fun existingFiles(): Map<String, Long> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            mediaStoreFiles()
        } else {
            legacyDir().listFiles()?.filter { it.isFile }?.associate { it.name.lowercase() to it.length() } ?: emptyMap()
        }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun mediaStoreFiles(): Map<String, Long> {
        val result = HashMap<String, Long>()
        resolver
            .query(
                collection(),
                arrayOf(MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.SIZE),
                "${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
                arrayOf(RELATIVE_PATH),
                null,
            )?.use { cursor ->
                while (cursor.moveToNext()) cursor.getString(0)?.let { result[it.lowercase()] = cursor.getLong(1) }
            }
        return result
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun collection(): Uri = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    /** Each failing platform call is reported as its own IOException. */
    @Suppress("ThrowsCount")
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun openPending(name: String): PendingUpload {
        val values =
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, TransferFiles.mimeTypeOf(name))
                put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_PATH)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        val uri = resolver.insert(collection(), values) ?: throw IOException("The phone refused to create the file")
        val stream =
            try {
                resolver.openOutputStream(uri, "w") ?: throw IOException("Could not open the file for writing")
            } catch (e: IOException) {
                resolver.delete(uri, null, null)
                throw e
            }
        // MediaStore may have adjusted the name (a file not yet indexed with the same name).
        val actualName =
            resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            } ?: name
        return object : StreamUpload(actualName, stream) {
            override fun publish() {
                val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                if (resolver.update(uri, done, null, null) != 1) throw IOException("Could not publish the file")
            }

            override fun discard() {
                resolver.delete(uri, null, null)
            }

            override fun released() = release(name)
        }
    }

    private fun legacyDir(): File {
        @Suppress("DEPRECATION")
        return File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), FOLDER)
    }

    private fun openLegacy(name: String): PendingUpload {
        val dir = legacyDir()
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Could not create Music/$FOLDER")
        val part = File(dir, ".$name.${System.nanoTime()}$PART_SUFFIX")
        val stream = FileOutputStream(part)
        return object : StreamUpload(name, stream) {
            override fun publish() {
                val target = File(dir, name)
                if (target.exists() || !part.renameTo(target)) throw IOException("Could not finish the file")
                MediaScannerConnection.scanFile(context, arrayOf(target.path), arrayOf(TransferFiles.mimeTypeOf(name)), null)
            }

            override fun discard() {
                part.delete()
            }

            override fun released() = release(name)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun deleteOwnPendingEntries() {
        val args =
            android.os.Bundle().apply {
                putString(
                    android.content.ContentResolver.QUERY_ARG_SQL_SELECTION,
                    "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.IS_PENDING} = 1",
                )
                putStringArray(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, arrayOf(RELATIVE_PATH))
                putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_ONLY)
            }
        val stale = mutableListOf<Uri>()
        resolver.query(collection(), arrayOf(MediaStore.MediaColumns._ID), args, null)?.use { c ->
            while (c.moveToNext()) stale += ContentUris.withAppendedId(collection(), c.getLong(0))
        }
        stale.forEach { runCatching { resolver.delete(it, null, null) } }
    }

    /**
     * A pending file written through [stream]: [commit] flushes, syncs, closes and publishes it;
     * [abort] closes and deletes it. Whichever comes first wins; the other does nothing.
     */
    private abstract class StreamUpload(
        override val savedName: String,
        private val stream: OutputStream,
    ) : PendingUpload {
        private val finished = AtomicBoolean()

        abstract fun publish()

        abstract fun discard()

        abstract fun released()

        override fun write(
            buffer: ByteArray,
            offset: Int,
            length: Int,
        ) = stream.write(buffer, offset, length)

        override fun commit() {
            if (!finished.compareAndSet(false, true)) return
            try {
                stream.flush()
                (stream as? FileOutputStream)?.fd?.sync()
                stream.close()
                publish()
            } catch (e: IOException) {
                runCatching { stream.close() }
                runCatching { discard() }
                throw e
            } finally {
                released()
            }
        }

        override fun abort() {
            if (!finished.compareAndSet(false, true)) return
            runCatching { stream.close() }
            runCatching { discard() }
            released()
        }
    }

    companion object {
        const val FOLDER = "erhu"
        val RELATIVE_PATH = "${Environment.DIRECTORY_MUSIC}/$FOLDER/"
        private const val PART_SUFFIX = ".part"

        /** Keep this much free space beyond the file itself. */
        private const val FREE_SPACE_MARGIN = 50L * 1024 * 1024
    }
}
