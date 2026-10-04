package com.lochanpthakke.empee3.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
    /** User-chosen cover (a file in app storage); null = the default playlist cover. Never touched by scans. */
    @ColumnInfo(name = "artwork_uri")
    val artworkUri: String? = null,
)
