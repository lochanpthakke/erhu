package com.erhu.erhu.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "albums")
data class AlbumEntity(
    @PrimaryKey
    val id: Long,
    val title: String,
    val artist: String,
    @ColumnInfo(name = "artist_id")
    val artistId: Long,
    @ColumnInfo(name = "song_count")
    val songCount: Int,
    val year: Int,
    @ColumnInfo(name = "artwork_uri")
    val artworkUri: String? = null,
    @ColumnInfo(name = "is_compilation", defaultValue = "0")
    val isCompilation: Boolean = false,
    @ColumnInfo(name = "album_artist_inconsistent", defaultValue = "0")
    val albumArtistInconsistent: Boolean = false,
    @ColumnInfo(name = "grouping_key", defaultValue = "''")
    val groupingKey: String = "",
)
