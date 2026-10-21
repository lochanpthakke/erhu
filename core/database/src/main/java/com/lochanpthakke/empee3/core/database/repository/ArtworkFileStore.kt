package com.lochanpthakke.empee3.core.database.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArtworkFileStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val baseDir: File
        get() = File(context.noBackupFilesDir, "artwork/animated")

    fun getSquareFile(albumId: Long, albumTitle: String? = null, artist: String? = null): File {
        val fileName = buildFileName(albumId, albumTitle, artist, "square")
        return File(baseDir, fileName)
    }

    fun getTallFile(albumId: Long, albumTitle: String? = null, artist: String? = null): File {
        val fileName = buildFileName(albumId, albumTitle, artist, "tall")
        return File(baseDir, fileName)
    }

    private fun buildFileName(albumId: Long, albumTitle: String?, artist: String?, orientation: String): String {
        return if (!albumTitle.isNullOrBlank() && !artist.isNullOrBlank()) {
            val cleanTitle = sanitize(albumTitle)
            val cleanArtist = sanitize(artist)
            "${cleanTitle}_${cleanArtist}_AnimatedArtwork_${orientation}.mp4"
        } else {
            "${albumId}_${orientation}.mp4"
        }
    }

    private fun sanitize(input: String): String {
        return input.replace("[^a-zA-Z0-9]".toRegex(), "_")
            .replace("_+".toRegex(), "_")
            .trim('_')
    }

    fun totalBytes(): Long {
        if (!baseDir.exists()) return 0L
        return baseDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
    }

    fun deleteAll() {
        if (baseDir.exists()) {
            baseDir.deleteRecursively()
        }
    }

    fun cleanTempFiles() {
        if (baseDir.exists()) {
            baseDir.listFiles()?.forEach { file ->
                if (file.name.endsWith(".tmp")) {
                    file.delete()
                }
            }
        }
    }
}
