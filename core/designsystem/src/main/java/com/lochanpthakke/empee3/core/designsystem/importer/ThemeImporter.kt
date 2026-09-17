package com.lochanpthakke.empee3.core.designsystem.importer

import android.content.Context
import com.lochanpthakke.empee3.core.common.result.AppError
import com.lochanpthakke.empee3.core.common.result.AppResult
import com.lochanpthakke.empee3.core.common.result.appResultOf
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

data class ImportedThemeResult(
    val themeId: String,
    val name: String,
    val isSkin: Boolean,
    val extractedDirectory: File,
    val sha256Checksum: String,
    val validationResult: ValidationResult,
)

@Singleton
@Suppress("MagicNumber", "TooGenericExceptionCaught")
class ThemeImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val validator: ThemeValidator,
) {

    suspend fun importPackage(
        inputStream: InputStream,
        targetFileNameHint: String = "custom_package",
    ): AppResult<ImportedThemeResult> = withContext(Dispatchers.IO) {
        appResultOf {
            val tempDir = File(context.cacheDir, "theme_import_temp_${System.currentTimeMillis()}")
            if (tempDir.exists()) tempDir.deleteRecursively()
            tempDir.mkdirs()

            try {
                unzipStream(inputStream, tempDir)

                val validationRes = validator.validateExtractedDirectory(tempDir)
                if (validationRes is AppResult.Error) {
                    tempDir.deleteRecursively()
                    throw IllegalArgumentException(validationRes.error.message)
                }

                val validData = (validationRes as AppResult.Success).data
                val (id, name, isSkin) = when (validData) {
                    is ValidationResult.ValidTheme -> Triple(validData.manifest.id, validData.manifest.name, false)
                    is ValidationResult.ValidSkin -> Triple(validData.manifest.id, validData.manifest.name, true)
                    is ValidationResult.VersionWarning -> Triple("warn_${targetFileNameHint}", validData.manifestName, false)
                }

                val destinationParent = File(context.filesDir, if (isSkin) "skins" else "themes")
                if (!destinationParent.exists()) destinationParent.mkdirs()

                val finalDir = File(destinationParent, id)
                if (finalDir.exists()) finalDir.deleteRecursively()

                tempDir.renameTo(finalDir)

                val checksum = calculateChecksum(File(finalDir, "manifest.json"))

                ImportedThemeResult(
                    themeId = id,
                    name = name,
                    isSkin = isSkin,
                    extractedDirectory = finalDir,
                    sha256Checksum = checksum,
                    validationResult = validData,
                )
            } finally {
                if (tempDir.exists()) tempDir.deleteRecursively()
            }
        }
    }

    private fun unzipStream(inputStream: InputStream, targetDir: File) {
        ZipInputStream(inputStream).use { zip ->
            var entry = zip.nextEntry
            val buffer = ByteArray(8192)
            while (entry != null) {
                val newFile = File(targetDir, entry.name)
                if (!newFile.canonicalPath.startsWith(targetDir.canonicalPath)) {
                    throw SecurityException("Zip slip entry detected: ${entry.name}")
                }

                if (entry.isDirectory) {
                    newFile.mkdirs()
                } else {
                    newFile.parentFile?.mkdirs()
                    FileOutputStream(newFile).use { out ->
                        var len: Int
                        while (zip.read(buffer).also { len = it } > 0) {
                            out.write(buffer, 0, len)
                        }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    private fun calculateChecksum(file: File): String {
        if (!file.exists()) return ""
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (stream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
