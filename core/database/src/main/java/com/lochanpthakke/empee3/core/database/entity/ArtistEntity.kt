package com.lochanpthakke.empee3.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "artists")
data class ArtistEntity(
    @PrimaryKey
    val id: Long,
    val name: String,
    @ColumnInfo(name = "song_count")
    val songCount: Int,
    @ColumnInfo(name = "album_count")
    val albumCount: Int,
    @ColumnInfo(name = "artwork_uri")
    val artworkUri: String? = null,
    @ColumnInfo(name = "artist_key", defaultValue = "''")
    val artistKey: String = "",
    @ColumnInfo(name = "is_unresolved", defaultValue = "0")
    val isUnresolved: Boolean = false,
)
