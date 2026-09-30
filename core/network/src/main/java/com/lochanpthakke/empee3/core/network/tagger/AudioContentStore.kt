package com.lochanpthakke.empee3.core.network.tagger

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import kotlin.coroutines.resume

/**
 * Byte-level access to an audio file identified by its content URI.
 * Abstracted so the write/rollback logic can be unit-tested off-device.
 */
interface AudioContentStore {
    fun openInput(contentUri: String): InputStream

    /** Opens the file for writing from offset 0, truncating it. */
    fun openOverwrite(contentUri: String): OutputStream
}

/** Scratch directory for the edit copies (app-private cache). */
fun interface TagEditWorkDir {
    fun get(): File
}

/** Asks MediaStore to re-read a file's tags after it was modified. */
fun interface MediaRescanner {
    suspend fun rescan(
        filePath: String,
        mimeType: String?,
    )
}

class ContentResolverAudioContentStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : AudioContentStore {
        override fun openInput(contentUri: String): InputStream =
            context.contentResolver.openInputStream(Uri.parse(contentUri))
                ?: throw FileNotFoundException("Cannot open $contentUri")

        // "rwt": "w" alone does not truncate on some Android 10+ providers, which
        // would leave trailing bytes when the edited file is shorter.
        override fun openOverwrite(contentUri: String): OutputStream =
            context.contentResolver.openOutputStream(Uri.parse(contentUri), "rwt")
                ?: throw FileNotFoundException("Cannot write $contentUri")
    }

class MediaScannerRescanner
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : MediaRescanner {
        override suspend fun rescan(
            filePath: String,
            mimeType: String?,
        ) {
            if (filePath.isBlank()) return
            withTimeoutOrNull(RESCAN_TIMEOUT_MS) {
                suspendCancellableCoroutine { continuation ->
                    MediaScannerConnection.scanFile(context, arrayOf(filePath), mimeType?.let { arrayOf(it) }) { _, _ ->
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                }
            }
        }

        private companion object {
            const val RESCAN_TIMEOUT_MS = 10_000L
        }
    }
