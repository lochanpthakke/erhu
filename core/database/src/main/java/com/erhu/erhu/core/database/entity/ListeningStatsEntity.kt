package com.erhu.erhu.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Listening of one song in one calendar month (Sound Capsule). Kept across scans and
 * across months: a new month simply starts new rows. Linked to the library only by
 * [songId]; albums and artists are derived from the song's current relationships
 * when the month is read, so no identity is duplicated here.
 */
@Entity(
    tableName = "listening_stats",
    primaryKeys = ["month_key", "song_id"],
    indices = [Index("song_id")],
)
data class ListeningStatsEntity(
    /** Local calendar month, `YYYY-MM`. */
    @ColumnInfo(name = "month_key")
    val monthKey: String,
    @ColumnInfo(name = "song_id")
    val songId: Long,
    /** Times the song started playing as a listening event this month. */
    @ColumnInfo(name = "play_count")
    val playCount: Int,
    /** Time the song was actually playing this month. */
    @ColumnInfo(name = "listened_ms")
    val listenedMs: Long,
    @ColumnInfo(name = "last_played_at")
    val lastPlayedAt: Long,
)

/** A local calendar day (`YYYY-MM-DD`) with at least one play: the listening streak. */
@Entity(tableName = "listening_days")
data class ListeningDayEntity(
    @PrimaryKey
    val day: String,
    @ColumnInfo(name = "play_count")
    val playCount: Int,
)
