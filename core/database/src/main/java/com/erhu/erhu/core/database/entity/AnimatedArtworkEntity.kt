package com.erhu.erhu.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "animated_artwork",
    indices = [
        Index(value = ["normalized_artist", "normalized_album"], unique = true),
    ],
)
data class AnimatedArtworkEntity(
    @PrimaryKey
    @ColumnInfo(name = "album_id")
    val albumId: Long,
    @ColumnInfo(name = "normalized_artist")
    val normalizedArtist: String,
    @ColumnInfo(name = "normalized_album")
    val normalizedAlbum: String,
    @ColumnInfo(name = "catalog_album_id")
    val catalogAlbumId: String? = null,
    @ColumnInfo(name = "storefront")
    val storefront: String? = null,
    @ColumnInfo(name = "square_file_name")
    val squareFileName: String? = null,
    @ColumnInfo(name = "tall_file_name")
    val tallFileName: String? = null,
    @ColumnInfo(name = "has_square")
    val hasSquare: Boolean? = null,
    @ColumnInfo(name = "has_tall")
    val hasTall: Boolean? = null,
    @ColumnInfo(name = "status")
    val status: String,
    @ColumnInfo(name = "updated_at_epoch_ms")
    val updatedAtEpochMs: Long,
)
