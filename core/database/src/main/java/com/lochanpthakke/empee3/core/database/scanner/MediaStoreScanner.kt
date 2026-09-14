package com.lochanpthakke.empee3.core.database.scanner

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.lochanpthakke.empee3.core.database.dao.AlbumDao
import com.lochanpthakke.empee3.core.database.dao.ArtistDao
import com.lochanpthakke.empee3.core.database.dao.SongDao
import com.lochanpthakke.empee3.core.database.entity.AlbumEntity
import com.lochanpthakke.empee3.core.database.entity.ArtistEntity
import com.lochanpthakke.empee3.core.database.entity.SongEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaStoreScanner
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val songDao: SongDao,
        private val albumDao: AlbumDao,
        private val artistDao: ArtistDao,
    ) {
        @Suppress("DEPRECATION", "LongMethod", "CyclomaticComplexMethod", "MagicNumber")
        fun scanLibrary(): Flow<ScanProgress> =
            flow {
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

                val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 10000"
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

                val totalCount = cursor?.count ?: 0
                emit(ScanProgress(scannedCount = 0, totalCount = totalCount, isComplete = false))

                cursor?.use { c ->
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
                        val title = c.getString(titleColumn) ?: "Unknown Title"
                        val artist = c.getString(artistColumn) ?: "Unknown Artist"
                        val album = c.getString(albumColumn) ?: "Unknown Album"
                        val artistId = c.getLong(artistIdColumn)
                        val albumId = c.getLong(albumIdColumn)
                        val durationMs = c.getLong(durationColumn)
                        val filePath = c.getString(dataColumn) ?: ""
                        val trackNumber = c.getInt(trackColumn)
                        val year = c.getInt(yearColumn)
                        val dateModified = c.getLong(dateModifiedColumn)

                        val contentUri =
                            ContentUris
                                .withAppendedId(
                                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                                    id,
                                ).toString()

                        val artworkUri =
                            ContentUris
                                .withAppendedId(
                                    android.net.Uri.parse("content://media/external/audio/albumart"),
                                    albumId,
                                ).toString()

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

                        // Accumulate Album Entity
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

                        // Accumulate Artist Entity
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

                // Write to Room
                if (songs.isNotEmpty()) {
                    songDao.upsertAll(songs)
                    albumDao.upsertAll(albumEntities)
                    artistDao.upsertAll(artistEntities)

                    // Clean up deleted items
                    songDao.deleteSongsNotIn(songs.map { it.id })
                    albumDao.deleteAlbumsNotIn(albumEntities.map { it.id })
                    artistDao.deleteArtistsNotIn(artistEntities.map { it.id })
                }

                emit(ScanProgress(scannedCount = totalCount, totalCount = totalCount, isComplete = true))
            }.flowOn(Dispatchers.IO)

        @Suppress("LongParameterList")
        private class AlbumEntityAccumulator(
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
            private const val EMISSION_BATCH_SIZE = 50
        }
    }
