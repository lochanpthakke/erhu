package com.lochanpthakke.empee3.core.database.scanner

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import com.lochanpthakke.empee3.core.common.logging.Logger
import com.lochanpthakke.empee3.core.database.dao.LibraryModelDao
import com.lochanpthakke.empee3.core.database.entity.SongEntity
import com.lochanpthakke.empee3.core.database.library.LibraryModelBuilder
import com.lochanpthakke.empee3.core.database.library.RawTrack
import com.lochanpthakke.empee3.core.datastore.proto.PlaybackStateProto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaStoreScanner
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val libraryModelDao: LibraryModelDao,
        private val libraryWriter: LibraryWriter,
        private val dataStore: DataStore<PlaybackStateProto>,
        private val logger: Logger,
    ) {
        @Suppress("DEPRECATION", "LongMethod", "CyclomaticComplexMethod", "NestedBlockDepth", "MagicNumber", "MaxLineLength", "TooGenericExceptionCaught")
        fun scanLibrary(): Flow<ScanProgress> =
            flow {
                // Without read access MediaStore returns an empty result, which would
                // look like "every song was deleted" and wipe the library (and favorites).
                if (!hasAudioPermission()) {
                    logger.w(TAG, "Audio permission not granted — skipping scan, library left untouched")
                    emit(ScanProgress(isComplete = true))
                    return@flow
                }

                val proto = try {
                    dataStore.data.first()
                } catch (e: Exception) {
                    logger.d(TAG, "Failed to read scan location settings: ${e.message}")
                    null
                }
                val isCustomEnabled = proto?.isCustomScanLocationEnabled == true
                val customPath = proto?.customScanLocationPath.orEmpty().trim()
                val customUri = proto?.customScanLocationUri.orEmpty().trim()

                triggerMediaScannerForStorage()
                delay(SCAN_INDEXING_DELAY_MS)

                val projection = scanProjection(Build.VERSION.SDK_INT)
                val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 1000"
                val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

                val targetUris =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        try {
                            MediaStore
                                .getExternalVolumeNames(context)
                                .map { volumeName -> MediaStore.Audio.Media.getContentUri(volumeName) }
                                .toSet()
                                .ifEmpty { setOf(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI) }
                        } catch (e: Exception) {
                            logger.d(TAG, "Failed to resolve external volume names: ${e.message}")
                            setOf(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
                        }
                    } else {
                        setOf(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
                    }

                val songs = mutableListOf<SongEntity>()
                val rawTracks = mutableListOf<RawTrack>()
                val seenSongIds = mutableSetOf<Long>()

                var totalCount = 0
                for (targetUri in targetUris) {
                    val countCursor =
                        context.contentResolver.query(
                            targetUri,
                            arrayOf(MediaStore.Audio.Media._ID),
                            selection,
                            null,
                            null,
                        )
                    countCursor?.use { totalCount += it.count }
                }

                emit(ScanProgress(scannedCount = 0, totalCount = totalCount, isComplete = false))

                var scannedCount = 0

                for (targetUri in targetUris) {
                    val cursor =
                        context.contentResolver.query(
                            targetUri,
                            projection,
                            selection,
                            null,
                            sortOrder,
                        ) ?: continue

                    cursor.use { c ->
                        val idColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                        val titleColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                        val artistColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                        val albumColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                        val rawAlbumIdColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                        val durationColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                        val dataColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                        val trackColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                        val yearColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
                        val dateModifiedColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
                        val albumArtistColumn = c.getColumnIndex("album_artist")
                        val compilationColumn = c.getColumnIndex(MediaStore.Audio.Media.COMPILATION)

                        while (c.moveToNext()) {
                            val id = c.getLong(idColumn)
                            if (!seenSongIds.add(id)) {
                                continue
                            }

                            val title = c.getString(titleColumn)?.takeIf { it.isNotBlank() } ?: "Unknown Title"
                            val rawArtist =
                                c
                                    .getString(artistColumn)
                                    ?.takeIf { it.isNotBlank() && it != MediaStore.UNKNOWN_STRING } ?: "Unknown Artist"
                            val album = c.getString(albumColumn)?.takeIf { it.isNotBlank() } ?: "Unknown Album"
                            val rawAlbumArtist =
                                if (albumArtistColumn >= 0) {
                                    c.getString(albumArtistColumn)?.takeIf { it.isNotBlank() && it != MediaStore.UNKNOWN_STRING }
                                } else {
                                    null
                                }
                            val rawAlbumId = c.getLong(rawAlbumIdColumn)
                            val durationMs = c.getLong(durationColumn)
                            val filePath = c.getString(dataColumn) ?: ""
                            if (isExcludedFromLibrary(filePath) || !matchesCustomLocation(filePath, isCustomEnabled, customPath, customUri)) {
                                scannedCount++
                                continue
                            }
                            val rawTrackValue = c.getInt(trackColumn)
                            val trackNumber = rawTrackValue % 1000
                            val year = c.getInt(yearColumn)
                            val dateModified = c.getLong(dateModifiedColumn)

                            val contentUri =
                                ContentUris
                                    .withAppendedId(
                                        targetUri,
                                        id,
                                    ).toString()

                            val artworkUri = ContentUris.withAppendedId(ALBUM_ART_BASE_URI, rawAlbumId).toString()

                            // Raw values only: credits, identity and album grouping are decided after the
                            // whole library has been read (LibraryModelBuilder).
                            rawTracks +=
                                RawTrack(
                                    songId = id,
                                    title = title,
                                    rawArtist = c.getString(artistColumn)?.takeUnless { it == MediaStore.UNKNOWN_STRING }.orEmpty(),
                                    rawAlbumArtist = rawAlbumArtist,
                                    album = c.getString(albumColumn).orEmpty(),
                                    trackNumber = trackNumber,
                                    discNumber = rawTrackValue / 1000,
                                    folder = filePath.substringBeforeLast('/', missingDelimiterValue = ""),
                                    isCompilation = compilationColumn >= 0 && c.getString(compilationColumn) == "1",
                                )
                            songs +=
                                SongEntity(
                                    id = id,
                                    title = title,
                                    artist = rawArtist,
                                    album = album,
                                    artistId = 0L,
                                    albumId = 0L,
                                    durationMs = durationMs,
                                    uri = contentUri,
                                    filePath = filePath,
                                    trackNumber = trackNumber,
                                    year = year,
                                    dateModified = dateModified,
                                    artworkUri = artworkUri,
                                )

                            scannedCount++
                            if (scannedCount % EMISSION_BATCH_SIZE == 0 || scannedCount == totalCount) {
                                emit(
                                    ScanProgress(
                                        scannedCount = scannedCount,
                                        totalCount = totalCount,
                                        currentSongTitle = title,
                                        isComplete = false,
                                    ),
                                )
                            }
                        }
                    }
                }

                val aliases = libraryModelDao.getAliases().associate { it.aliasKey to it.canonicalKey }
                val artwork = libraryModelDao.getArtworkByKey().associate { it.artistKey to it.artworkUri }
                val model = LibraryModelBuilder.build(rawTracks, aliases)
                val summary = libraryWriter.write(model.toEntities(songs, artwork))
                logger.i(TAG, "Scan applied: ${summary.upserted} songs, ${summary.removed} removed, ${model.albums.size} albums")

                emit(ScanProgress(scannedCount = totalCount, totalCount = totalCount, isComplete = true))
            }.flowOn(Dispatchers.IO)

        private fun matchesCustomLocation(
            filePath: String,
            isCustomEnabled: Boolean,
            customPath: String,
            customUri: String,
        ): Boolean {
            if (!isCustomEnabled || (customPath.isBlank() && customUri.isBlank())) return true
            val lowerFilePath = filePath.lowercase().replace('\\', '/')
            val lowerCustomPath = customPath.lowercase().replace('\\', '/').trimEnd('/')

            if (lowerCustomPath.isNotBlank()) {
                if (lowerCustomPath.startsWith("/")) {
                    if (lowerFilePath.startsWith(lowerCustomPath)) return true
                } else {
                    if (lowerFilePath.contains(lowerCustomPath)) return true
                }
            }

            if (customUri.isNotBlank()) {
                val treeId = Uri.decode(customUri).substringAfter("/tree/", "").substringBefore("/document/")
                val relativePath = treeId.substringAfter(':').lowercase().trimEnd('/')
                if (relativePath.isNotBlank() && lowerFilePath.contains(relativePath)) {
                    return true
                }
            }

            return false
        }

        private fun hasAudioPermission(): Boolean {
            val permission =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Manifest.permission.READ_MEDIA_AUDIO
                } else {
                    Manifest.permission.READ_EXTERNAL_STORAGE
                }
            return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }

        @Suppress("DEPRECATION", "TooGenericExceptionCaught", "NestedBlockDepth")
        private fun triggerMediaScannerForStorage() {
            try {
                val pathsToScan = mutableListOf<String>()
                val storageRoots = mutableSetOf<File>()

                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)?.let { storageRoots.add(it) }
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)?.let { storageRoots.add(it) }

                val externalFilesDirs = ContextCompat.getExternalFilesDirs(context, null)
                for (dir in externalFilesDirs) {
                    if (dir != null) {
                        var root: File? = dir
                        while (root != null && root.parentFile != null &&
                            root.parentFile?.name != "storage" && root.parentFile?.name != "mnt"
                        ) {
                            root = root.parentFile
                        }
                        root?.takeIf { it.exists() && it.isDirectory }?.let { storageRoots.add(it) }
                    }
                }

                val storageDir = File("/storage")
                if (storageDir.exists() && storageDir.isDirectory) {
                    storageDir.listFiles()?.forEach { volume ->
                        if (volume.isDirectory && volume.name != "self" && volume.name != "emulated") {
                            storageRoots.add(volume)
                        }
                    }
                }

                fun collectAudioFiles(dir: File, depth: Int = 0) {
                    if (depth > 10 || !dir.exists() || !dir.isDirectory) return
                    dir.listFiles()?.forEach { file ->
                        if (file.isDirectory) {
                            val name = file.name.lowercase()
                            if (name != "android" && !name.startsWith(".")) {
                                collectAudioFiles(file, depth + 1)
                            }
                        } else if (file.isFile && isAudioFile(file.name)) {
                            pathsToScan.add(file.absolutePath)
                        }
                    }
                }

                for (root in storageRoots) {
                    collectAudioFiles(root)
                }

                if (pathsToScan.isNotEmpty()) {
                    MediaScannerConnection.scanFile(
                        context,
                        pathsToScan.toTypedArray(),
                        null,
                        null,
                    )
                }
            } catch (e: Exception) {
                logger.d(TAG, "Fallback media scan skipped: ${e.message}", e)
            }
        }

        private fun isAudioFile(name: String): Boolean {
            val lower = name.lowercase()
            return lower.endsWith(".mp3") ||
                lower.endsWith(".flac") ||
                lower.endsWith(".m4a") ||
                lower.endsWith(".wav") ||
                lower.endsWith(".aac") ||
                lower.endsWith(".ogg")
        }

        companion object {
            private const val TAG = "MediaStoreScanner"
            private const val EMISSION_BATCH_SIZE = 50
            private const val SCAN_INDEXING_DELAY_MS = 800L

            private val ALBUM_ART_BASE_URI: Uri = Uri.parse("content://media/external/audio/albumart")
        }
    }

/** MediaStore columns read by a scan; Album Artist and the compilation flag exist from API 30. */
@SuppressLint("InlinedApi")
internal fun scanProjection(sdkInt: Int): Array<String> {
    val columns =
        mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ARTIST_ID,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DATE_MODIFIED,
        )
    if (sdkInt >= Build.VERSION_CODES.R) {
        columns += MediaStore.Audio.Media.ALBUM_ARTIST
        columns += MediaStore.Audio.Media.COMPILATION
    }
    return columns.toTypedArray()
}
