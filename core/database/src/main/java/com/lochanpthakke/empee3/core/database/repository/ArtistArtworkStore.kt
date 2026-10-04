package com.lochanpthakke.empee3.core.database.repository

import android.net.Uri
import java.io.File

/**
 * Files for user-chosen artist images, in app-private storage (not MediaStore, so library
 * scans never see or touch them). The same scheme as [PlaylistArtworkStore]: every save
 * writes a new file name, so an image cached for the previous picture is never shown.
 */
class ArtistArtworkStore(
    private val directory: File,
) {
    /** Writes [imageBytes] (an encoded image) for [artistId] and returns its `file://` uri. */
    fun save(
        artistId: Long,
        imageBytes: ByteArray,
        timestamp: Long,
    ): String {
        directory.mkdirs()
        val file = File(directory, "artist_${artistId}_$timestamp.jpg")
        file.writeBytes(imageBytes)
        return Uri.fromFile(file).toString()
    }

    /** Deletes the file behind [uri] if it is one of ours; anything else (e.g. a Deezer url) is left alone. */
    fun delete(uri: String?) {
        val path = uri?.let { Uri.parse(it).path } ?: return
        val file = File(path)
        if (file.parentFile?.canonicalPath == directory.canonicalPath) file.delete()
    }
}
