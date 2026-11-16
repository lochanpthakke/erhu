package com.erhu.erhu.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "listening_events",
    indices = [
        Index("timestamp"),
        Index("song_id"),
    ],
)
data class ListeningEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "song_id")
    val songId: Long,
    val timestamp: Long,
    @ColumnInfo(name = "duration_ms")
    val durationMs: Long,
    @ColumnInfo(name = "is_counted_play")
    val isCountedPlay: Boolean,
)
