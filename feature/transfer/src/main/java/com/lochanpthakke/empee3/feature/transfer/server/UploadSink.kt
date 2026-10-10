package com.lochanpthakke.empee3.feature.transfer.server

import java.io.IOException

/** What would happen to a file of this name and size before any byte is sent. */
sealed interface UploadCheck {
    /** It will be saved as [savedName] (the name itself, or a numbered one if another file has that name). */
    data class Accept(
        val savedName: String,
    ) : UploadCheck

    /** A file with this name and size is already there: skipped, never overwritten. */
    data object Duplicate : UploadCheck

    data object InsufficientSpace : UploadCheck
}

/**
 * A file being written. Nothing is visible to the library until [commit] succeeds; [abort] removes
 * every trace. Both are safe to call more than once and after each other: only the first of them
 * takes effect, so a file is finalized at most once.
 */
interface PendingUpload {
    val savedName: String

    @Throws(IOException::class)
    fun write(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    )

    /** Makes the file visible as a finished track. */
    @Throws(IOException::class)
    fun commit()

    /** Deletes the partial file. Never throws. */
    fun abort()
}

/** Where received files go. The destination is the sink's own; clients only suggest a name. */
interface UploadSink {
    fun check(
        name: String,
        sizeBytes: Long,
    ): UploadCheck

    /** Reserves [UploadCheck.Accept.savedName] (re-checked here) and opens the pending file. */
    @Throws(IOException::class)
    fun open(
        name: String,
        sizeBytes: Long,
    ): PendingUploadOrDuplicate
}

sealed interface PendingUploadOrDuplicate {
    data class Opened(
        val upload: PendingUpload,
    ) : PendingUploadOrDuplicate

    data object Duplicate : PendingUploadOrDuplicate

    data object InsufficientSpace : PendingUploadOrDuplicate
}
