package com.erhu.erhu.core.network.apple

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Same-filesystem atomic publication: a failed validation/move never deletes the previous cover. */
internal class VerifiedArtworkPublisher(
    private val replace: (File, File) -> Unit = { staged, destination ->
        Files.move(staged.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    },
) {
    suspend fun publish(
        staged: File,
        destination: File,
        validate: suspend (File) -> Unit,
    ) = publication.withLock {
        require(staged.canonicalFile != destination.canonicalFile) { "Artwork must be staged separately" }
        require(staged.parentFile.canonicalFile == destination.parentFile.canonicalFile) { "Artwork must use the same directory" }
        check(staged.isFile && staged.length() > 0) { "No completed artwork to publish" }
        currentCoroutineContext().ensureActive()
        validate(staged)
        currentCoroutineContext().ensureActive()
        FileOutputStream(staged, true).use { it.fd.sync() }
        currentCoroutineContext().ensureActive()
        replace(staged, destination)
        Unit
    }

    private companion object {
        // Decoder validation is bounded and serialized, including callers outside the album repository.
        val publication = Mutex()
    }
}
