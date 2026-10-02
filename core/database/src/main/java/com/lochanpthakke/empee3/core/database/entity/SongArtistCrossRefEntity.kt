package com.lochanpthakke.empee3.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "song_artist_cross_ref",
    primaryKeys = ["song_id", "artist_id", "role"],
    indices = [
        Index("song_id"),
        Index("artist_id"),
    ],
)
data class SongArtistCrossRefEntity(
    @ColumnInfo(name = "song_id")
    val songId: Long,
    @ColumnInfo(name = "artist_id")
    val artistId: Long,
    val role: String, // ArtistRole.PRIMARY or ArtistRole.FEATURED
    val position: Int = 0,
)
