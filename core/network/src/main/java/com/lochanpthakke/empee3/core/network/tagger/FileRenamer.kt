package com.lochanpthakke.empee3.core.network.tagger

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.lochanpthakke.empee3.core.common.result.AppError
import com.lochanpthakke.empee3.core.common.result.AppResult
import com.lochanpthakke.empee3.core.common.result.appResultOf
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class RenameResult(
    val newFilePath: String,
    val newFileName: String,
)

object FileNamingUtils {
    fun sanitizeFileName(rawName: String): String =
        rawName
            .replace(Regex("[\\\\/:*?\"<>|]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

    fun buildTargetFileName(
        title: String,
        extension: String,
    ): String {
        val cleanTitle = sanitizeFileName(title).ifBlank { "Track" }
        return "$cleanTitle.$extension"
    }

    fun buildTargetFileName(
        title: String,
        artist: String?,
        extension: String,
    ): String = buildTargetFileName(title, extension)
}

@Singleton
@Suppress("TooGenericExceptionCaught", "MagicNumber")
class FileRenamer
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        fun sanitizeFileName(rawName: String): String = FileNamingUtils.sanitizeFileName(rawName)

        fun buildTargetFileName(
            title: String,
            extension: String,
        ): String = FileNamingUtils.buildTargetFileName(title, extension)

        fun buildTargetFileName(
            title: String,
            artist: String?,
            extension: String,
        ): String = FileNamingUtils.buildTargetFileName(title, extension)

        suspend fun renameAudioFile(
            contentUri: String,
            currentPath: String,
            targetFileName: String,
        ): AppResult<RenameResult> =
            withContext(Dispatchers.IO) {
                appResultOf(mapError = { AppError.TagWriteError("Could not rename file: ${it.message}", it) }) {
                val currentFile = File(currentPath)
                val parentDir = currentFile.parentFile ?: throw IllegalArgumentException("Invalid file path: $currentPath")

                var finalTargetName = targetFileName
                var targetFile = File(parentDir, finalTargetName)
                var counter = 1
                val baseNameWithoutExt = targetFileName.substringBeforeLast('.')
                val ext = targetFileName.substringAfterLast('.', "")

                while (targetFile.exists() && targetFile.absolutePath != currentFile.absolutePath) {
                    finalTargetName = if (ext.isNotEmpty()) "$baseNameWithoutExt ($counter).$ext" else "$baseNameWithoutExt ($counter)"
                    targetFile = File(parentDir, finalTargetName)
                    counter++
                }

                if (targetFile.absolutePath == currentFile.absolutePath) {
                    return@appResultOf RenameResult(
                        newFilePath = currentPath,
                        newFileName = currentFile.name,
                    )
                }

                val uri = Uri.parse(contentUri)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && contentUri.startsWith("content://")) {
                    val values =
                        ContentValues().apply {
                            put(MediaStore.Audio.Media.DISPLAY_NAME, finalTargetName)
                        }
                    val updatedRows = context.contentResolver.update(uri, values, null, null)
                    if (updatedRows <= 0) {
                        if (!currentFile.renameTo(targetFile)) {
                            throw IllegalStateException("Failed to rename file via MediaStore or direct filesystem")
                        }
                    }
                } else {
                    if (!currentFile.renameTo(targetFile)) {
                        throw IllegalStateException("Failed to rename file on storage")
                    }
                }

                val newPath = targetFile.absolutePath
                MediaScannerConnection.scanFile(context, arrayOf(newPath), null, null)

                RenameResult(
                    newFilePath = newPath,
                    newFileName = finalTargetName,
                )
            }
        }
    }
