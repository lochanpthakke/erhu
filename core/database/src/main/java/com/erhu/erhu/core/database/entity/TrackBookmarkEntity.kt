package com.erhu.erhu.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A user's saved position in a track, keyed by the song's library id (not its metadata), so two
 * tracks with the same title keep separate bookmarks. Kept across scans; rows of songs that no
 * longer exist are simply never shown.
 */
@Entity(
    tableName = "track_bookmarks",
    indices = [Index("song_id")],
)
data class TrackBookmarkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "song_id")
    val songId: Long,
    @ColumnInfo(name = "position_ms")
    val positionMs: Long,
    val label: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)
