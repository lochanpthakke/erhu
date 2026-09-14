package com.lochanpthakke.empee3.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    @ColumnInfo(name = "artist_id")
    val artistId: Long,
    @ColumnInfo(name = "album_id")
    val albumId: Long,
    @ColumnInfo(name = "duration_ms")
    val durationMs: Long,
    val uri: String,
    @ColumnInfo(name = "file_path")
    val filePath: String,
    @ColumnInfo(name = "track_number")
    val trackNumber: Int,
    val year: Int,
    @ColumnInfo(name = "date_modified")
    val dateModified: Long,
    @ColumnInfo(name = "artwork_uri")
    val artworkUri: String? = null,
    @ColumnInfo(name = "is_enriched")
    val isEnriched: Boolean = false,
    val lyrics: String? = null,
    @ColumnInfo(name = "disc_number")
    val discNumber: Int = 1,
    val composer: String? = null,
    val genre: String? = null,
)
