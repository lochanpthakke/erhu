package com.lochanpthakke.empee3.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.lochanpthakke.empee3.core.database.entity.SongEntity
import kotlinx.coroutines.flow.Flow

@Dao
@Suppress("TooManyFunctions")
interface SongDao {
    @Upsert
    suspend fun upsertAll(songs: List<SongEntity>)

    @Query("SELECT * FROM songs ORDER BY title COLLATE NOCASE ASC")
    fun getAllSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs")
    suspend fun getAllSongsOnce(): List<SongEntity>

    @Query("UPDATE songs SET file_path = :newPath WHERE id = :songId")
    suspend fun updateFilePath(songId: Long, newPath: String): Int

    @Query("UPDATE songs SET title = :title, artist = :artist, album = :album WHERE id = :id")
    suspend fun updateSongTags(
        id: Long,
        title: String,
        artist: String,
        album: String,
    ): Int

    @Query("SELECT id FROM songs")
    suspend fun getAllSongIds(): List<Long>

    @Query("SELECT * FROM songs ORDER BY title COLLATE NOCASE ASC")
    fun getSongsPaged(): PagingSource<Int, SongEntity>

    @Query("SELECT * FROM songs WHERE is_favorite = 1 ORDER BY title COLLATE NOCASE ASC")
    fun getFavoriteSongsPaged(): PagingSource<Int, SongEntity>

    @Query("SELECT * FROM songs WHERE is_favorite = 1 ORDER BY title COLLATE NOCASE ASC")
    fun getFavoriteSongs(): Flow<List<SongEntity>>

    /** Newest in the library first: the file's MediaStore modification time (when it was added or last changed). */
    @Query("SELECT * FROM songs ORDER BY date_modified DESC, id DESC LIMIT :limit")
    fun observeRecentlyAddedSongs(limit: Int): Flow<List<SongEntity>>

    /** Favorite songs by title, at most [limit] (-1 = all). */
    @Query("SELECT * FROM songs WHERE is_favorite = 1 ORDER BY title COLLATE NOCASE ASC, id ASC LIMIT :limit")
    fun observeFavoriteSongs(limit: Int): Flow<List<SongEntity>>

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
        SELECT * FROM songs
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
