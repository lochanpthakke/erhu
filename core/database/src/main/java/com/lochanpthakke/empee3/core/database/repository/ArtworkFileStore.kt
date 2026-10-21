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

    fun getSquareFile(albumId: Long): File {
        return File(baseDir, "${albumId}_square.mp4")
    }

    fun getTallFile(albumId: Long): File {
        return File(baseDir, "${albumId}_tall.mp4")
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
