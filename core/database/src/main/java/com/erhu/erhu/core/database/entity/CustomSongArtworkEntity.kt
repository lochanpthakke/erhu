package com.erhu.erhu.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "custom_song_artwork")
data class CustomSongArtworkEntity(
    @PrimaryKey
    @ColumnInfo(name = "song_id")
    val songId: Long,
    @ColumnInfo(name = "file_path")
    val filePath: String,
    @ColumnInfo(name = "mime_type")
    val mimeType: String,
    @ColumnInfo(name = "is_animated")
    val isAnimated: Boolean,
    @ColumnInfo(name = "updated_at_epoch_ms")
    val updatedAtEpochMs: Long,
)
