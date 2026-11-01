package com.erhu.erhu.core.database.repository

import android.net.Uri
import java.io.File

/**
 * Files for user-chosen playlist covers, in app-private storage (not MediaStore,
 * so library scans never see or touch them). Every save writes a new file name,
 * so an image cached for the previous cover is never shown for the new one.
 */
class PlaylistArtworkStore(
    private val directory: File,
) {
    /** Writes [imageBytes] (an encoded image) for [playlistId] and returns its `file://` uri. */
    fun save(
        playlistId: Long,
        imageBytes: ByteArray,
        timestamp: Long,
    ): String {
        directory.mkdirs()
        val file = File(directory, "playlist_${playlistId}_$timestamp.jpg")
        file.writeBytes(imageBytes)
        return Uri.fromFile(file).toString()
    }

    /** Deletes the file behind [uri] if it is one of ours; anything else is left alone. */
    fun delete(uri: String?) {
        val path = uri?.let { Uri.parse(it).path } ?: return
        val file = File(path)
        if (file.parentFile?.canonicalPath == directory.canonicalPath) file.delete()
    }
}
