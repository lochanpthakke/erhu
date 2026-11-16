package com.erhu.erhu.core.database.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.erhu.erhu.core.database.entity.ListeningEventEntity
import kotlinx.coroutines.flow.Flow

data class PeriodTotals(
    @ColumnInfo(name = "plays") val plays: Int,
    @ColumnInfo(name = "listened_ms") val listenedMs: Long,
    @ColumnInfo(name = "unique_tracks") val uniqueTracks: Int,
)

@Dao
interface ListeningEventDao {
    @Insert
    suspend fun insert(event: ListeningEventEntity): Long

    @Query("SELECT COUNT(*) FROM listening_events")
    suspend fun getCount(): Int

    @Query(
        """
        INSERT INTO listening_events (song_id, timestamp, duration_ms, is_counted_play)
        SELECT song_id, last_played_at, listened_ms, 1
        FROM listening_stats
        WHERE last_played_at > 0
        """,
    )
    suspend fun backfillFromListeningStats()

    @Query(
        """
        SELECT s.*, COUNT(e.id) AS plays FROM listening_events e
        INNER JOIN songs s ON s.id = e.song_id
        WHERE e.timestamp >= :startTime AND e.timestamp <= :endTime AND e.is_counted_play = 1
        GROUP BY s.id
        ORDER BY plays DESC, MAX(e.timestamp) DESC, s.title COLLATE NOCASE ASC
        LIMIT :limit
        """,
    )
    fun observeTopSongsBetween(
        startTime: Long,
        endTime: Long,
        limit: Int,
    ): Flow<List<SongPlays>>

    @Query(
        """
        SELECT a.*, COUNT(e.id) AS plays FROM listening_events e
        INNER JOIN songs s ON s.id = e.song_id
        INNER JOIN albums a ON a.id = s.album_id
        WHERE e.timestamp >= :startTime AND e.timestamp <= :endTime AND e.is_counted_play = 1
        GROUP BY a.id
        ORDER BY plays DESC, MAX(e.timestamp) DESC, a.title COLLATE NOCASE ASC
        LIMIT :limit
        """,
    )
    fun observeTopAlbumsBetween(
        startTime: Long,
        endTime: Long,
        limit: Int,
    ): Flow<List<AlbumPlays>>

    @Query(
        """
        SELECT ar.*, COUNT(e.id) AS plays FROM listening_events e
        INNER JOIN songs s ON s.id = e.song_id
        INNER JOIN song_artist_cross_ref ref ON ref.song_id = s.id
        INNER JOIN artists ar ON ar.id = ref.artist_id
        WHERE e.timestamp >= :startTime AND e.timestamp <= :endTime AND e.is_counted_play = 1
          AND ref.is_unresolved = 0 AND ar.is_unresolved = 0
        GROUP BY ar.id
        ORDER BY plays DESC, MAX(e.timestamp) DESC, ar.name COLLATE NOCASE ASC
        LIMIT :limit
        """,
    )
    fun observeTopArtistsBetween(
        startTime: Long,
        endTime: Long,
        limit: Int,
    ): Flow<List<ArtistPlays>>

    @Query(
        """
        SELECT COUNT(CASE WHEN is_counted_play = 1 THEN 1 END) AS plays,
               COALESCE(SUM(duration_ms), 0) AS listened_ms,
               COUNT(DISTINCT song_id) AS unique_tracks
        FROM listening_events
        WHERE timestamp >= :startTime AND timestamp <= :endTime
        """,
    )
    fun observePeriodTotals(
        startTime: Long,
        endTime: Long,
    ): Flow<PeriodTotals>
}
