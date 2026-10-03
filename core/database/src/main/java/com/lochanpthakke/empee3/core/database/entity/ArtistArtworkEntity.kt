package com.lochanpthakke.empee3.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Kept across scans: artist profile artwork by canonical artist key ("NONE…" = looked up, nothing found; see ArtistArtworkCache). */
@Entity(tableName = "artist_artwork")
data class ArtistArtworkEntity(
    @PrimaryKey
    @ColumnInfo(name = "artist_key")
    val artistKey: String,
    @ColumnInfo(name = "artwork_uri")
    val artworkUri: String,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
