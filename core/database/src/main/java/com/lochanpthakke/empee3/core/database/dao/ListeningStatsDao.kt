package com.lochanpthakke.empee3.core.database.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import androidx.room.Transaction
import com.lochanpthakke.empee3.core.database.entity.AlbumEntity
import com.lochanpthakke.empee3.core.database.entity.ArtistEntity
import com.lochanpthakke.empee3.core.database.entity.SongEntity
import kotlinx.coroutines.flow.Flow

data class SongPlays(
    @Embedded val song: SongEntity,
    @ColumnInfo(name = "plays") val plays: Int,
)

data class AlbumPlays(
    @Embedded val album: AlbumEntity,
    @ColumnInfo(name = "plays") val plays: Int,
)

data class ArtistPlays(
    @Embedded val artist: ArtistEntity,
    @ColumnInfo(name = "plays") val plays: Int,
)

data class MonthTotals(
    @ColumnInfo(name = "plays") val plays: Int,
    @ColumnInfo(name = "listened_ms") val listenedMs: Long,
)

/**
 * Monthly listening statistics. Every ranking is ordered by plays, then by the most
 * recent play, then by name and id, so ties always come out in the same order.
 */
@Dao
interface ListeningStatsDao {
    /** Creates the month's row for [songId] if the song exists; no-op otherwise. */
    @Query(
        """
        INSERT OR IGNORE INTO listening_stats (month_key, song_id, play_count, listened_ms, last_played_at)
        SELECT :monthKey, id, 0, 0, :at FROM songs WHERE id = :songId
        """,
    )
    suspend fun ensureRow(
        monthKey: String,
        songId: Long,
        at: Long,
    )

    @Query(
        """
        UPDATE listening_stats SET play_count = play_count + 1, last_played_at = :at
        WHERE month_key = :monthKey AND song_id = :songId
        """,
    )
    suspend fun incrementPlay(
        monthKey: String,
        songId: Long,
        at: Long,
    ): Int

    @Query("UPDATE listening_stats SET listened_ms = listened_ms + :ms WHERE month_key = :monthKey AND song_id = :songId")
    suspend fun incrementListened(
        monthKey: String,
        songId: Long,
        ms: Long,
    ): Int

    @Query("INSERT OR IGNORE INTO listening_days (day, play_count) VALUES (:day, 0)")
    suspend fun ensureDay(day: String)

    @Query("UPDATE listening_days SET play_count = play_count + 1 WHERE day = :day")
    suspend fun incrementDay(day: String)

    /** One play of [songId]: one small transaction. Unknown songs record nothing. */
    @Transaction
    suspend fun recordPlay(
        monthKey: String,
        day: String,
        songId: Long,
        at: Long,
    ) {
        ensureRow(monthKey, songId, at)
        if (incrementPlay(monthKey, songId, at) > 0) {
            ensureDay(day)
            incrementDay(day)
        }
    }

    /** Adds [ms] of actual playing time of [songId] to [monthKey]. Unknown songs record nothing. */
    @Transaction
    suspend fun addListened(
        monthKey: String,
        songId: Long,
        ms: Long,
        at: Long,
    ) {
        ensureRow(monthKey, songId, at)
        incrementListened(monthKey, songId, ms)
    }

    @Query(
        """
        SELECT s.*, st.play_count AS plays FROM listening_stats st
        INNER JOIN songs s ON s.id = st.song_id
        WHERE st.month_key = :monthKey AND st.play_count > 0
        ORDER BY st.play_count DESC, st.last_played_at DESC, s.title COLLATE NOCASE ASC, s.id ASC
        LIMIT :limit
        """,
    )
    fun observeTopSongs(
        monthKey: String,
        limit: Int,
    ): Flow<List<SongPlays>>

    /** Each play of a song counts for the song's album (the existing song → album link). */
    @Query(
        """
        SELECT a.*, SUM(st.play_count) AS plays FROM listening_stats st
        INNER JOIN songs s ON s.id = st.song_id
        INNER JOIN albums a ON a.id = s.album_id
        WHERE st.month_key = :monthKey AND st.play_count > 0
        GROUP BY a.id
        ORDER BY plays DESC, MAX(st.last_played_at) DESC, a.title COLLATE NOCASE ASC, a.id ASC
        LIMIT :limit
        """,
    )
    fun observeTopAlbums(
        monthKey: String,
        limit: Int,
    ): Flow<List<AlbumPlays>>

    /**
     * Each play of a song counts once for every resolved artist credited on it (the
     * existing credits; an artist credited in two roles still counts once). Unresolved
     * credits and unresolved artist rows never count.
     */
    @Query(
        """
        SELECT ar.*, SUM(st.play_count) AS plays FROM listening_stats st
        INNER JOIN (
            SELECT DISTINCT song_id, artist_id FROM song_artist_cross_ref WHERE is_unresolved = 0
        ) credit ON credit.song_id = st.song_id
        INNER JOIN artists ar ON ar.id = credit.artist_id AND ar.is_unresolved = 0
        WHERE st.month_key = :monthKey AND st.play_count > 0
        GROUP BY ar.id
        ORDER BY plays DESC, MAX(st.last_played_at) DESC, ar.name COLLATE NOCASE ASC, ar.id ASC
        LIMIT :limit
        """,
    )
    fun observeTopArtists(
        monthKey: String,
        limit: Int,
    ): Flow<List<ArtistPlays>>

    @Query(
        """
        SELECT COALESCE(SUM(play_count), 0) AS plays, COALESCE(SUM(listened_ms), 0) AS listened_ms
        FROM listening_stats WHERE month_key = :monthKey
        """,
    )
    fun observeTotals(monthKey: String): Flow<MonthTotals>

    @Query("SELECT DISTINCT month_key FROM listening_stats WHERE play_count > 0 ORDER BY month_key DESC")
    fun observeMonthKeys(): Flow<List<String>>

    @Query("SELECT day FROM listening_days WHERE play_count > 0 ORDER BY day ASC")
    fun observeListeningDays(): Flow<List<String>>

    // ---- Smart playlists: all-time history across every month (songs no longer in the library drop out of the join).
    // [limit] -1 means all songs.

    /** Songs by total plays, most played first; ties by the latest play, then title and id. */
    @Query(
        """
        SELECT s.* FROM songs s
        INNER JOIN listening_stats st ON st.song_id = s.id
        GROUP BY s.id
        HAVING SUM(st.play_count) > 0
        ORDER BY SUM(st.play_count) DESC,
            MAX(CASE WHEN st.play_count > 0 THEN st.last_played_at END) DESC,
            s.title COLLATE NOCASE ASC, s.id ASC
        LIMIT :limit
        """,
    )
    fun observeMostPlayedSongs(limit: Int): Flow<List<SongEntity>>

    /** Songs by their latest play, most recent first; ties by title and id. */
    @Query(
        """
        SELECT s.* FROM songs s
        INNER JOIN listening_stats st ON st.song_id = s.id AND st.play_count > 0
        GROUP BY s.id
        ORDER BY MAX(st.last_played_at) DESC, s.title COLLATE NOCASE ASC, s.id ASC
        LIMIT :limit
        """,
    )
    fun observeRecentlyPlayedSongs(limit: Int): Flow<List<SongEntity>>

    /** Songs without a single counted play, by title. */
    @Query(
        """
        SELECT * FROM songs
        WHERE id NOT IN (SELECT song_id FROM listening_stats WHERE play_count > 0)
        ORDER BY title COLLATE NOCASE ASC, id ASC
        LIMIT :limit
        """,
    )
    fun observeNeverPlayedSongs(limit: Int): Flow<List<SongEntity>>
}
