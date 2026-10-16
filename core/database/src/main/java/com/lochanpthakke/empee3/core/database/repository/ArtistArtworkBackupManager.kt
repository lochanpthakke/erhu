package com.lochanpthakke.empee3.core.database.repository

import android.net.Uri
import com.lochanpthakke.empee3.core.common.result.AppError
import com.lochanpthakke.empee3.core.common.result.AppResult
import com.lochanpthakke.empee3.core.common.result.appResultOf
import com.lochanpthakke.empee3.core.database.dao.ArtistDao
import com.lochanpthakke.empee3.core.database.dao.LibraryModelDao
import com.lochanpthakke.empee3.core.database.entity.ArtistArtworkEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

data class ArtistArtworkBackupEntry(
    val artistKey: String,
    val filename: String,
    val updatedAt: Long,
)

data class ArtistArtworkBackupManifest(
    val version: Int = 1,
    val timestamp: Long,
    val entries: List<ArtistArtworkBackupEntry>,
)

@Singleton
class ArtistArtworkBackupManager
    @Inject
    constructor(
        private val libraryModelDao: LibraryModelDao,
        private val artistDao: ArtistDao,
        @param:Named("artistArtworkDir") private val artworkDir: File,
    ) {
        suspend fun exportBackup(outputStream: OutputStream): AppResult<Int> =
            withContext(Dispatchers.IO) {
                appResultOf(mapError = { AppError.IoError("Failed to export artwork backup", it) }) {
                    val customEntities = libraryModelDao.getArtworkByKey().filter { it.source == ArtistArtworkEntity.SOURCE_CUSTOM }
                    val entries = mutableListOf<ArtistArtworkBackupEntry>()

                    ZipOutputStream(outputStream).use { zip ->
                        for (entity in customEntities) {
                            val path = Uri.parse(entity.artworkUri).path ?: continue
                            val file = File(path)
                            if (file.exists()) {
                                val entryName = "images/${file.name}"
                                zip.putNextEntry(ZipEntry(entryName))
                                file.inputStream().use { input -> input.copyTo(zip) }
                                zip.closeEntry()

                                entries.add(
                                    ArtistArtworkBackupEntry(
                                        artistKey = entity.artistKey,
                                        filename = file.name,
                                        updatedAt = entity.updatedAt,
                                    ),
                                )
                            }
                        }

                        val manifestJson = buildManifestJson(entries)
                        zip.putNextEntry(ZipEntry("manifest.json"))
                        zip.write(manifestJson.toByteArray(Charsets.UTF_8))
                        zip.closeEntry()
                    }

                    entries.size
                }
            }

        suspend fun importBackup(inputStream: InputStream): AppResult<Int> =
            withContext(Dispatchers.IO) {
                appResultOf(mapError = { AppError.IoError("Failed to import artwork backup", it) }) {
                    var manifestJsonText: String? = null
                    val imagesMap = mutableMapOf<String, ByteArray>()

                    ZipInputStream(inputStream).use { zip ->
                        var entry: ZipEntry? = zip.nextEntry
                        while (entry != null) {
                            if (entry.name == "manifest.json") {
                                manifestJsonText = zip.readBytes().toString(Charsets.UTF_8)
                            } else if (entry.name.startsWith("images/")) {
                                val filename = entry.name.removePrefix("images/")
                                imagesMap[filename] = zip.readBytes()
                            }
                            zip.closeEntry()
                            entry = zip.nextEntry
                        }
                    }

                    requireNotNull(manifestJsonText) { "Backup ZIP archive is missing manifest.json" }
                    val manifest = parseManifestJson(manifestJsonText)

                    var restoredCount = 0
                    for (item in manifest.entries) {
                        val bytes = imagesMap[item.filename] ?: continue
                        artworkDir.mkdirs()
                        val targetFile = File(artworkDir, item.filename)
                        targetFile.writeBytes(bytes)
                        val uri = Uri.fromFile(targetFile).toString()

                        libraryModelDao.upsertArtwork(
                            ArtistArtworkEntity(
                                artistKey = item.artistKey,
                                artworkUri = uri,
                                updatedAt = item.updatedAt,
                                source = ArtistArtworkEntity.SOURCE_CUSTOM,
                                providerArtistId = null,
                                matcherVersion = 0,
                            ),
                        )
                        artistDao.syncArtwork(item.artistKey)
                        restoredCount++
                    }

                    restoredCount
                }
            }

        private fun buildManifestJson(entries: List<ArtistArtworkBackupEntry>): String {
            val root = JSONObject()
            root.put("version", 1)
            root.put("timestamp", System.currentTimeMillis())

            val array = JSONArray()
            for (item in entries) {
                val obj = JSONObject()
                obj.put("artistKey", item.artistKey)
                obj.put("filename", item.filename)
                obj.put("updatedAt", item.updatedAt)
                array.put(obj)
            }
            root.put("entries", array)
            return root.toString()
        }

        private fun parseManifestJson(jsonText: String): ArtistArtworkBackupManifest {
            val root = JSONObject(jsonText)
            val version = root.optInt("version", 1)
            val timestamp = root.optLong("timestamp", System.currentTimeMillis())
            val array = root.optJSONArray("entries") ?: JSONArray()

            val entries = mutableListOf<ArtistArtworkBackupEntry>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                entries.add(
                    ArtistArtworkBackupEntry(
                        artistKey = obj.getString("artistKey"),
                        filename = obj.getString("filename"),
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                    ),
                )
            }
            return ArtistArtworkBackupManifest(version = version, timestamp = timestamp, entries = entries)
        }
    }
