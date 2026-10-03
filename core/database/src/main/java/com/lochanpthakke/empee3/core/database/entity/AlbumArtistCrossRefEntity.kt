package com.lochanpthakke.empee3.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/** Derived: artists an album is listed under (rebuilt every scan). */
@Entity(
    tableName = "album_artist_cross_ref",
    primaryKeys = ["album_id", "artist_id"],
    indices = [Index("artist_id")],
)
data class AlbumArtistCrossRefEntity(
    @ColumnInfo(name = "album_id")
    val albumId: Long,
    @ColumnInfo(name = "artist_id")
    val artistId: Long,
    val position: Int,
)
