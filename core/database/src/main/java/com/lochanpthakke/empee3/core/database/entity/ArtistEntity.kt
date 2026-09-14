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
)
