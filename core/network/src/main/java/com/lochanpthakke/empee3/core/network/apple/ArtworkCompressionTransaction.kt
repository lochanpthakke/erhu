package com.lochanpthakke.empee3.core.network.apple

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest

internal data class CompressedArtwork(
    val originalBytes: Long,
    val compressedBytes: Long,
)

/** Owns staging only. No database work follows the validated atomic commit. */
internal class ArtworkCompressionTransaction(
    private val freeBytes: (File) -> Long = { it.usableSpace },
    private val publisher: VerifiedArtworkPublisher = VerifiedArtworkPublisher(),
) {
    suspend fun run(
        source: File,
        encode: suspend (File) -> Unit,
        validate: suspend (File) -> Unit,
        committed: (CompressedArtwork) -> Unit = {},
    ): CompressedArtwork {
        currentCoroutineContext().ensureActive()
        require(source.isFile && !Files.isSymbolicLink(source.toPath())) { "Artwork must be an existing regular file" }
        val originalBytes = source.length()
        requireArtwork(
            ArtworkCompressionPolicy.enoughSpace(originalBytes, freeBytes(source)),
            "Not enough free space to safely compress this cover. Original kept.",
        )
        val originalFingerprint = fingerprint(source)
        val staged = File.createTempFile(".artwork-compress-", ".mp4.tmp", source.parentFile)
        try {
            encode(staged)
            currentCoroutineContext().ensureActive()
            requireArtwork(
                ArtworkCompressionPolicy.worthwhile(originalBytes, staged.length()),
                "Original kept: compression must save at least 10% and 1 MB.",
            )
            val result = CompressedArtwork(originalBytes, staged.length())
            publisher.publish(staged, source) { candidate ->
                requireArtwork(fingerprint(source) == originalFingerprint, "Artwork changed during compression. Original kept.")
                validate(candidate)
                requireArtwork(fingerprint(source) == originalFingerprint, "Artwork changed during validation. Original kept.")
            }
            committed(result)
            return result
        } finally {
            staged.delete()
        }
    }
}

private fun requireArtwork(
    condition: Boolean,
    message: String,
) {
    if (!condition) throw IOException(message)
}

private suspend fun fingerprint(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
