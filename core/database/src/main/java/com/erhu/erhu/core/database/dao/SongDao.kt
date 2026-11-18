package com.erhu.erhu.core.database.dao

import androidx.paging.PagingSource
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.erhu.erhu.core.database.entity.SongEntity
import kotlinx.coroutines.flow.Flow

@Dao
@Suppress("TooManyFunctions")
interface SongDao {
    @Query(
        """
        SELECT s.*, COALESCE(h.plays, 0) AS browser_plays,
            COALESCE(h.last_played, 0) AS browser_last_played
        FROM songs s LEFT JOIN (
            SELECT song_id, SUM(play_count) AS plays,
                MAX(CASE WHEN play_count > 0 THEN last_played_at ELSE 0 END) AS last_played
            FROM listening_stats GROUP BY song_id
        ) h ON s.id = h.song_id
    """,
    )
    fun observeBrowserSongs(): Flow<List<SongBrowserRow>>

    @Query("UPDATE songs SET date_added_ms = :added WHERE id = :id AND date_added_ms = 0")
    suspend fun backfillAddedDate(
        id: Long,
        added: Long,
    )

    @Upsert
    suspend fun upsertAll(songs: List<SongEntity>)

    @Query("SELECT $SONG_LIST_COLUMNS FROM songs ORDER BY title COLLATE NOCASE ASC")
    fun getAllSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs")
    suspend fun getAllSongsOnce(): List<SongEntity>

    /** Path, modification time and year of every stored song: lets a scan skip re-reading unchanged files. */
    @Query("SELECT id, file_path, date_modified, year FROM songs")
    suspend fun getScanStamps(): List<SongScanStamp>

    @Query("UPDATE songs SET file_path = :newPath WHERE id = :songId")
    suspend fun updateFilePath(
        songId: Long,
        newPath: String,
    ): Int

    @Query("UPDATE songs SET title = :title, artist = :artist, album = :album WHERE id = :id")
    suspend fun updateSongTags(
        id: Long,
        title: String,
        artist: String,
        album: String,
    ): Int

    @Query("SELECT id FROM songs")
    suspend fun getAllSongIds(): List<Long>

    @Query("SELECT title FROM songs ORDER BY title COLLATE NOCASE ASC, id ASC")
    fun observeSongTitles(): Flow<List<String>>

    @Query("SELECT $SONG_LIST_COLUMNS FROM songs ORDER BY title COLLATE NOCASE ASC, id ASC")
    fun getSongsPaged(): PagingSource<Int, SongEntity>

    @Query("SELECT $SONG_LIST_COLUMNS FROM songs WHERE is_favorite = 1 ORDER BY title COLLATE NOCASE ASC")
    fun getFavoriteSongsPaged(): PagingSource<Int, SongEntity>

    @Query("SELECT $SONG_LIST_COLUMNS FROM songs WHERE is_favorite = 1 ORDER BY title COLLATE NOCASE ASC")
    fun getFavoriteSongs(): Flow<List<SongEntity>>

    /** Newest in the library first: the file's MediaStore modification time (when it was added or last changed). */
    @Query("SELECT $SONG_LIST_COLUMNS FROM songs ORDER BY date_modified DESC, id DESC LIMIT :limit")
    fun observeRecentlyAddedSongs(limit: Int): Flow<List<SongEntity>>

    /** Favorite songs by title, at most [limit] (-1 = all). */
    @Query("SELECT $SONG_LIST_COLUMNS FROM songs WHERE is_favorite = 1 ORDER BY title COLLATE NOCASE ASC, id ASC LIMIT :limit")
    fun observeFavoriteSongs(limit: Int): Flow<List<SongEntity>>

    @Query("UPDATE remote_tracks SET is_favorite = :isFavorite WHERE id = :id")
    suspend fun setRemoteFavorite(
        id: Long,
        isFavorite: Boolean,
    )

    @Query("UPDATE songs SET is_favorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(
        id: Long,
        isFavorite: Boolean,
    ): Int

    @Query("SELECT * FROM songs WHERE id = :id LIMIT 1")
    suspend fun getSongById(id: Long): SongEntity?

    @Query("SELECT * FROM songs WHERE id = :id LIMIT 1")
    fun observeSongById(id: Long): Flow<SongEntity?>

    @Query("SELECT * FROM songs WHERE id IN (:ids)")
    suspend fun getSongsByIds(ids: List<Long>): List<SongEntity>

    @Query("SELECT * FROM songs WHERE id IN (:ids)")
    fun observeSongsByIds(ids: List<Long>): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE album_id = :albumId ORDER BY disc_number ASC, track_number ASC, title COLLATE NOCASE ASC")
    fun getSongsByAlbumId(albumId: Long): Flow<List<SongEntity>>

    @Query(
        """
        SELECT DISTINCT s.* FROM songs s
        INNER JOIN song_artist_cross_ref ref ON s.id = ref.song_id
        WHERE ref.artist_id = :artistId
        ORDER BY s.disc_number ASC, s.track_number ASC, s.title COLLATE NOCASE ASC
        """,
    )
    fun getSongsByArtistId(artistId: Long): Flow<List<SongEntity>>

    @Query(
        """
        SELECT DISTINCT s.* FROM songs s
        INNER JOIN song_artist_cross_ref ref ON s.id = ref.song_id
        WHERE ref.artist_id = :artistId AND ref.role = 'PRIMARY'
        ORDER BY s.disc_number ASC, s.track_number ASC, s.title COLLATE NOCASE ASC
        """,
    )
    fun getPrimarySongsByArtistId(artistId: Long): Flow<List<SongEntity>>

    @Query(
        """
        SELECT DISTINCT s.* FROM songs s
        INNER JOIN song_artist_cross_ref ref ON s.id = ref.song_id
        WHERE ref.artist_id = :artistId AND ref.role = 'FEATURED'
        ORDER BY s.disc_number ASC, s.track_number ASC, s.title COLLATE NOCASE ASC
        """,
    )
    fun getFeaturedSongsByArtistId(artistId: Long): Flow<List<SongEntity>>

    /** Songs whose unresolved credit contains this artist's exact standalone name (presentation only). */
    @Query(
        """
        SELECT DISTINCT s.* FROM songs s
        INNER JOIN unverified_credit_segment u ON u.song_id = s.id
        WHERE u.segment_key = :artistKey
        ORDER BY s.title COLLATE NOCASE ASC
        """,
    )
    fun getUnverifiedSongsByArtistKey(artistKey: String): Flow<List<SongEntity>>

    @Query(
        """
        SELECT $SONG_LIST_COLUMNS FROM songs
        WHERE title LIKE '%' || :query || '%'
           OR artist LIKE '%' || :query || '%'
           OR album LIKE '%' || :query || '%'
        ORDER BY title COLLATE NOCASE ASC
        """,
    )
    fun searchSongs(query: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE is_enriched = 0")
    suspend fun getUnenrichedSongs(): List<SongEntity>

    @Suppress("LongParameterList")
    @Query(
        """
        UPDATE songs
        SET lyrics = :lyrics, disc_number = :discNumber, composer = :composer, genre = :genre, bpm = :bpm, is_enriched = 1
        WHERE id = :id
        """,
    )
    suspend fun updateEnrichedData(
        id: Long,
        lyrics: String?,
        discNumber: Int,
        composer: String?,
        genre: String?,
        bpm: Int? = null,
    ): Int

    /** Every song's facts for the smart mixes, by id. Emits again on any change to songs. */
    @Query(
        """
        SELECT id, title, artist, artist_id, album, album_id, genre, bpm, year, duration_ms, is_favorite, date_modified
        FROM songs ORDER BY id
        """,
    )
    fun observeTrackFacts(): Flow<List<TrackFactsRow>>

    /** Every song's discovery text (VYBE): tags only, lyrics cut to an excerpt. Emits again on any change. */
    @Query(
        """
        SELECT id, title, artist, album, genre, year, bpm, substr(lyrics, 1, $LYRICS_EXCERPT_CHARS) AS lyricsExcerpt
        FROM songs ORDER BY id
        """,
    )
    fun observeSongTags(): Flow<List<SongTagsRow>>

    /** The library's copy of a BPM just written to the file (the rescan reads it again). */
    @Query("UPDATE songs SET bpm = :bpm WHERE id = :id")
    suspend fun updateBpm(
        id: Long,
        bpm: Int?,
    ): Int

    /** Callers must chunk [ids]: SQLite limits bound variables (999 before API 30). */
    @Query("DELETE FROM songs WHERE id IN (:ids)")
    suspend fun deleteSongsByIds(ids: List<Long>): Int

    @Query("DELETE FROM songs")
    suspend fun deleteAll(): Int
}

/** Lyrics characters read for discovery: an excerpt is all VYBE uses. */
const val LYRICS_EXCERPT_CHARS = 600

/** What the smart mixes read of each song: identity, grouping and stored tags, no lyrics. */
data class TrackFactsRow(
    val id: Long,
    val title: String,
    val artist: String,
    @ColumnInfo(name = "artist_id") val artistId: Long,
    val album: String,
    @ColumnInfo(name = "album_id") val albumId: Long,
    val genre: String?,
    val bpm: Int?,
    /** The year tag as stored (0 or a placeholder such as 1970 when the file has none). */
    val year: Int?,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean,
    @ColumnInfo(name = "date_modified") val dateModified: Long,
)

/** A row of [SongDao.observeSongTags]. */
data class SongTagsRow(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val genre: String?,
    val year: Int,
    val bpm: Int?,
    val lyricsExcerpt: String?,
)

/**
 * Every `songs` column except the lyrics text, which can be several KB a song and is read only by the
 * lyrics views (by id). Lists of thousands of songs, re-read on each library change, skip it.
 */
const val SONG_LIST_COLUMNS =
    "id, title, artist, album, artist_id, album_id, duration_ms, uri, file_path, track_number, year, date_modified, " +
        "artwork_uri, is_enriched, NULL AS lyrics, disc_number, composer, genre, is_favorite, bpm, date_added_ms"

data class SongScanStamp(
    val id: Long,
    @androidx.room.ColumnInfo(name = "file_path") val filePath: String,
    @androidx.room.ColumnInfo(name = "date_modified") val dateModified: Long,
    val year: Int,
)
