package com.lochanpthakke.empee3.core.database.scanner

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.room.withTransaction
import com.lochanpthakke.empee3.core.common.logging.Logger
import com.lochanpthakke.empee3.core.database.EmPee3Database
import com.lochanpthakke.empee3.core.database.dao.AlbumDao
import com.lochanpthakke.empee3.core.database.dao.ArtistDao
import com.lochanpthakke.empee3.core.database.dao.SongDao
import com.lochanpthakke.empee3.core.database.entity.AlbumEntity
import com.lochanpthakke.empee3.core.database.entity.ArtistEntity
import com.lochanpthakke.empee3.core.database.entity.SongEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
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
        private val database: EmPee3Database,
        private val songDao: SongDao,
        private val albumDao: AlbumDao,
        private val artistDao: ArtistDao,
        private val logger: Logger,
    ) {
        @Suppress("DEPRECATION", "LongMethod", "CyclomaticComplexMethod", "NestedBlockDepth", "MagicNumber", "MaxLineLength")
        fun scanLibrary(): Flow<ScanProgress> =
            flow {
                // Without read access MediaStore returns an empty result, which would
                // look like "every song was deleted" and wipe the library (and favorites).
                if (!hasAudioPermission()) {
                    logger.w(TAG, "Audio permission not granted — skipping scan, library left untouched")
                    emit(ScanProgress(isComplete = true))
                    return@flow
                }

                triggerMediaScannerForStorage()
                delay(SCAN_INDEXING_DELAY_MS)

                val projection =
                    arrayOf(
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

                val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 1000"
                val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

                val songs = mutableListOf<SongEntity>()
                val albumMap = mutableMapOf<Long, AlbumEntityAccumulator>()
                val artistMap = mutableMapOf<Long, ArtistEntityAccumulator>()

                val cursor =
                    context.contentResolver.query(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        projection,
                        selection,
                        null,
                        sortOrder,
                    )

                if (cursor == null) {
                    // Query failure is not the same as "no music": keep the existing library.
                    logger.w(TAG, "MediaStore query returned null — library left untouched")
                    emit(ScanProgress(isComplete = true))
                    return@flow
                }

                val totalCount = cursor.count
                emit(ScanProgress(scannedCount = 0, totalCount = totalCount, isComplete = false))

                cursor.use { c ->
                    val idColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val titleColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val artistColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val albumColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val artistIdColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID)
                    val albumIdColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                    val durationColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                    val dataColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                    val trackColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                    val yearColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
                    val dateModifiedColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)

                    var scannedCount = 0

                    while (c.moveToNext()) {
                        val id = c.getLong(idColumn)
                        val title = c.getString(titleColumn)?.takeIf { it.isNotBlank() } ?: "Unknown Title"
                        val artist =
                            c
                                .getString(artistColumn)
                                ?.takeIf { it.isNotBlank() && it != MediaStore.UNKNOWN_STRING } ?: "Unknown Artist"
                        val album = c.getString(albumColumn)?.takeIf { it.isNotBlank() } ?: "Unknown Album"
                        val artistId = c.getLong(artistIdColumn)
                        val albumId = c.getLong(albumIdColumn)
                        val durationMs = c.getLong(durationColumn)
                        val filePath = c.getString(dataColumn) ?: ""
                        // MediaStore encodes disc as the thousands digit (e.g. 2005 = disc 2, track 5).
                        val trackNumber = c.getInt(trackColumn) % 1000
                        val year = c.getInt(yearColumn)
                        val dateModified = c.getLong(dateModifiedColumn)

                        val contentUri =
                            ContentUris
                                .withAppendedId(
                                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                                    id,
                                ).toString()

                        val artworkUri = ContentUris.withAppendedId(ALBUM_ART_BASE_URI, albumId).toString()

                        val song =
                            SongEntity(
                                id = id,
                                title = title,
                                artist = artist,
                                album = album,
                                artistId = artistId,
                                albumId = albumId,
                                durationMs = durationMs,
                                uri = contentUri,
                                filePath = filePath,
                                trackNumber = trackNumber,
                                year = year,
                                dateModified = dateModified,
                                artworkUri = artworkUri,
                            )
                        songs.add(song)

                        val albumAccumulator =
                            albumMap.getOrPut(albumId) {
                                AlbumEntityAccumulator(
                                    id = albumId,
                                    title = album,
                                    artist = artist,
                                    artistId = artistId,
                                    year = year,
                                    artworkUri = artworkUri,
                                )
                            }
                        albumAccumulator.songCount++

                        val artistAccumulator =
                            artistMap.getOrPut(artistId) {
                                ArtistEntityAccumulator(
                                    id = artistId,
                                    name = artist,
                                )
                            }
                        artistAccumulator.songCount++
                        artistAccumulator.albumIds.add(albumId)

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

                val albumEntities =
                    albumMap.values.map {
                        AlbumEntity(
                            id = it.id,
                            title = it.title,
                            artist = it.artist,
                            artistId = it.artistId,
                            songCount = it.songCount,
                            year = it.year,
                            artworkUri = it.artworkUri,
                        )
                    }

                val artistEntities =
                    artistMap.values.map {
                        ArtistEntity(
                            id = it.id,
                            name = it.name,
                            songCount = it.songCount,
                            albumCount = it.albumIds.size,
                        )
                    }

                // One transaction: observers never see a half-applied scan, and an
                // interrupted scan leaves the previous library intact.
                database.withTransaction {
                    val plan = planLibrarySync(scanned = songs, existing = songDao.getAllSongsOnce())
                    plan.deletedIds.chunked(SQLITE_MAX_VARIABLES).forEach { songDao.deleteSongsByIds(it) }
                    plan.upserts.chunked(SQLITE_MAX_VARIABLES).forEach { songDao.upsertAll(it) }
                    // Albums and artists are derived entirely from songs; rebuild them.
                    albumDao.deleteAll()
                    artistDao.deleteAll()
                    albumEntities.chunked(SQLITE_MAX_VARIABLES).forEach { albumDao.upsertAll(it) }
                    artistEntities.chunked(SQLITE_MAX_VARIABLES).forEach { artistDao.upsertAll(it) }
                    logger.i(TAG, "Scan applied: ${plan.upserts.size} songs, ${plan.deletedIds.size} removed")
                }

                emit(ScanProgress(scannedCount = totalCount, totalCount = totalCount, isComplete = true))
            }.flowOn(Dispatchers.IO)

        private fun hasAudioPermission(): Boolean {
            val permission =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Manifest.permission.READ_MEDIA_AUDIO
                } else {
                    Manifest.permission.READ_EXTERNAL_STORAGE
                }
            return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }

        @Suppress("TooGenericExceptionCaught")
        private fun triggerMediaScannerForStorage() {
            try {
                val pathsToScan = mutableListOf<String>()
                val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)

                fun collectAudioFiles(dir: File) {
                    if (!dir.exists() || !dir.isDirectory) return
                    dir.listFiles()?.forEach { file ->
                        if (file.isDirectory) {
                            collectAudioFiles(file)
                        } else if (file.isFile && isAudioFile(file.name)) {
                            pathsToScan.add(file.absolutePath)
                        }
                    }
                }

                collectAudioFiles(downloadDir)
                collectAudioFiles(musicDir)

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

        private class AlbumEntityAccumulator
            @Suppress("LongParameterList")
            constructor(
                val id: Long,
                val title: String,
                val artist: String,
                val artistId: Long,
                val year: Int,
                val artworkUri: String,
                var songCount: Int = 0,
            )

        private class ArtistEntityAccumulator(
            val id: Long,
            val name: String,
            var songCount: Int = 0,
            val albumIds: MutableSet<Long> = mutableSetOf(),
        )

        companion object {
            private const val TAG = "MediaStoreScanner"
            private const val EMISSION_BATCH_SIZE = 50
            private const val SCAN_INDEXING_DELAY_MS = 800L

            /** Stay well below SQLite's 999 bound-variable limit on API < 30. */
            private const val SQLITE_MAX_VARIABLES = 500

            private val ALBUM_ART_BASE_URI: Uri = Uri.parse("content://media/external/audio/albumart")
        }
    }
