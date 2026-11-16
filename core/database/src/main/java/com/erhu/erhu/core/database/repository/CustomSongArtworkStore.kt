package com.erhu.erhu.core.database.repository

import java.io.File
import java.io.InputStream

data class SavedArtworkResult(
    val file: File,
    val mimeType: String,
    val isAnimated: Boolean,
)

/**
 * Storage for user-assigned custom song artwork in app-private storage.
 * Completely separate from MediaStore, preserving files across library scans.
 */
class CustomSongArtworkStore(
    private val directory: File,
) {
    init {
        directory.mkdirs()
    }

    /**
     * Inspects [stream], detects format/animation, writes to a versioned file for [songId],
     * and deletes any previous custom artwork for this song.
     */
    fun save(
        songId: Long,
        stream: InputStream,
        timestamp: Long,
    ): SavedArtworkResult {
        directory.mkdirs()
        val bytes = stream.readBytes()
        val (mimeType, ext, isAnimated) = detectFormat(bytes)

        // Delete any existing files for this song in our directory
        deleteExistingForSong(songId)

        val targetFile = File(directory, "song_${songId}_$timestamp.$ext")
        targetFile.writeBytes(bytes)

        return SavedArtworkResult(
            file = targetFile,
            mimeType = mimeType,
            isAnimated = isAnimated,
        )
    }

    fun delete(filePath: String?) {
        if (filePath.isNullOrBlank()) return
        val file = File(filePath)
        if (file.parentFile?.canonicalPath == directory.canonicalPath && file.exists()) {
            file.delete()
        }
    }

    fun deleteExistingForSong(songId: Long) {
        val prefix = "song_${songId}_"
        directory.listFiles()?.filter { it.name.startsWith(prefix) }?.forEach { it.delete() }
    }

    private fun detectFormat(bytes: ByteArray): Triple<String, String, Boolean> {
        if (bytes.size >= 6) {
            val header = String(bytes, 0, 6, Charsets.US_ASCII)
            if (header.startsWith("GIF87a") || header.startsWith("GIF89a")) {
                return Triple("image/gif", "gif", true)
            }
        }

        // WebP format check: "RIFF" .... "WEBP"
        if (bytes.size >= 16) {
            val riff = String(bytes, 0, 4, Charsets.US_ASCII)
            val webp = String(bytes, 8, 4, Charsets.US_ASCII)
            if (riff == "RIFF" && webp == "WEBP") {
                val isAnimated = isAnimatedWebp(bytes)
                return Triple("image/webp", "webp", isAnimated)
            }
        }

        // PNG check: 89 50 4E 47 0D 0A 1A 0A
        if (bytes.size >= 8 &&
            bytes[0] == 0x89.toByte() &&
            bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() &&
            bytes[3] == 0x47.toByte()
        ) {
            return Triple("image/png", "png", false)
        }

        // Default to JPEG
        return Triple("image/jpeg", "jpg", false)
    }

    private fun isAnimatedWebp(bytes: ByteArray): Boolean {
        // Look for VP8X chunk and animation flag
        if (bytes.size >= 21) {
            val chunk = String(bytes, 12, 4, Charsets.US_ASCII)
            if (chunk == "VP8X") {
                val flags = bytes[20].toInt()
                // Bit 1 (0x02) indicates animation in VP8X chunk
                return (flags and 0x02) != 0
            }
        }
        return false
    }
}
