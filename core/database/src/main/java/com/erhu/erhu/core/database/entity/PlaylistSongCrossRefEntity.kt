package com.erhu.erhu.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * One entry of a playlist. Keyed by position (since v10), so the same song can appear more than
 * once, e.g. a saved queue with intentional duplicates. Positions are unique within a playlist.
 */
@Entity(
    tableName = "playlist_song_cross_ref",
    primaryKeys = ["playlist_id", "position"],
    indices = [
        Index(value = ["playlist_id"]),
        Index(value = ["song_id"]),
    ],
)
data class PlaylistSongCrossRefEntity(
    @ColumnInfo(name = "playlist_id")
    val playlistId: Long,
    @ColumnInfo(name = "song_id")
    val songId: Long,
    @ColumnInfo(name = "position")
    val position: Int,
)
