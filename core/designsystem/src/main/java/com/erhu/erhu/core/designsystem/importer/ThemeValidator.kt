@file:Suppress("detekt:All")

package com.erhu.erhu.core.designsystem.importer

import com.erhu.erhu.core.common.result.AppError
import com.erhu.erhu.core.common.result.AppResult
import com.erhu.erhu.core.designsystem.skin.SkinManifest
import com.erhu.erhu.core.designsystem.theme.ThemeManifest
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ValidationResult {
    data class ValidTheme(
        val manifest: ThemeManifest,
    ) : ValidationResult

    data class ValidSkin(
        val manifest: SkinManifest,
    ) : ValidationResult

    data class VersionWarning(
        val manifestName: String,
        val minAppVersion: String,
    ) : ValidationResult
}

@Singleton
@Suppress("MagicNumber", "TooGenericExceptionCaught", "LongMethod", "CyclomaticComplexMethod", "NestedBlockDepth")
class ThemeValidator
    @Inject
    constructor(
        private val json: Json,
    ) {
        fun validateExtractedDirectory(
            directory: File,
            currentAppVersion: String = "1.0.0",
        ): AppResult<ValidationResult> {
            val manifestFile = File(directory, "manifest.json")
            if (!manifestFile.exists()) {
                return AppResult.Error(AppError.ValidationError("Missing manifest.json in theme archive"))
            }

            return try {
                val content = manifestFile.readText()
                if (content.contains("\"colors\"")) {
                    val themeManifest = json.decodeFromString<ThemeManifest>(content)
                    val fontValidation = validateFontFiles(directory)
                    if (fontValidation is AppResult.Error) return fontValidation

                    val minVersion = themeManifest.minimumSupportedAppVersion
                    if (minVersion != null && isAppVersionOlder(currentAppVersion, minVersion)) {
                        AppResult.Success(ValidationResult.VersionWarning(themeManifest.name, minVersion))
                    } else {
                        AppResult.Success(ValidationResult.ValidTheme(themeManifest))
                    }
                } else {
                    val skinManifest = json.decodeFromString<SkinManifest>(content)
                    val fontValidation = validateFontFiles(directory)
                    if (fontValidation is AppResult.Error) return fontValidation

                    val minVersion = skinManifest.minimumSupportedAppVersion
                    if (minVersion != null && isAppVersionOlder(currentAppVersion, minVersion)) {
                        AppResult.Success(ValidationResult.VersionWarning(skinManifest.name, minVersion))
                    } else {
                        AppResult.Success(ValidationResult.ValidSkin(skinManifest))
                    }
                }
            } catch (e: Exception) {
                AppResult.Error(AppError.ParseError("Invalid theme manifest JSON: ${e.message}", e))
            }
        }

        private fun validateFontFiles(directory: File): AppResult<Unit> {
            val fontsDir = File(directory, "fonts")
            if (!fontsDir.exists() || !fontsDir.isDirectory) return AppResult.Success(Unit)

            val fontFiles = fontsDir.listFiles() ?: return AppResult.Success(Unit)
            for (file in fontFiles) {
                if (file.isFile && (file.extension.equals("ttf", true) || file.extension.equals("otf", true))) {
                    if (file.length() == 0L) {
                        return AppResult.Error(AppError.ValidationError("Corrupted font file: ${file.name}"))
                    }
                    val header = ByteArray(4)
                    FileInputStream(file).use { it.read(header) }
                    if (!isValidFontHeader(header)) {
                        return AppResult.Error(AppError.ValidationError("Malformed font file header: ${file.name}"))
                    }
                }
            }
            return AppResult.Success(Unit)
        }

        private fun isValidFontHeader(header: ByteArray): Boolean {
            if (header.size < 4) return false
            val b0 = header[0].toInt() and 0xFF
            val b1 = header[1].toInt() and 0xFF
            val b2 = header[2].toInt() and 0xFF
            val b3 = header[3].toInt() and 0xFF

            return (b0 == 0x00 && b1 == 0x01 && b2 == 0x00 && b3 == 0x00) ||
                (b0 == 0x74 && b1 == 0x72 && b2 == 0x75 && b3 == 0x65) ||
                (b0 == 0x4F && b1 == 0x54 && b2 == 0x54 && b3 == 0x4F)
        }

        private fun isAppVersionOlder(
            currentVersion: String,
            minVersion: String,
        ): Boolean {
            val currentParts = currentVersion.split(".").mapNotNull { it.toIntOrNull() }
            val minParts = minVersion.split(".").mapNotNull { it.toIntOrNull() }

            val maxLen = maxOf(currentParts.size, minParts.size)
            for (i in 0 until maxLen) {
                val c = currentParts.getOrElse(i) { 0 }
                val m = minParts.getOrElse(i) { 0 }
                if (c < m) return true
                if (c > m) return false
            }
            return false
        }
    }
