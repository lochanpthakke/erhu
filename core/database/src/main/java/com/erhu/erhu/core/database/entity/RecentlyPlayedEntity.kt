package com.erhu.erhu.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * Kept across scans: when an album or a playlist was last played. One row per
 * item; playing it again only moves [playedAt]. Rows whose album or playlist no
 * longer exists are simply not returned by the queries that join them.
 */
@Entity(
    tableName = "recently_played",
    primaryKeys = ["kind", "item_id"],
    indices = [Index("played_at")],
)
data class RecentlyPlayedEntity(
    val kind: String,
    @ColumnInfo(name = "item_id")
    val itemId: Long,
    @ColumnInfo(name = "played_at")
    val playedAt: Long,
) {
    companion object {
        const val KIND_ALBUM = "ALBUM"
        const val KIND_PLAYLIST = "PLAYLIST"
    }
}
