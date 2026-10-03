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

    @Query("SELECT id FROM songs")
    suspend fun getAllSongIds(): List<Long>

    @Query("SELECT * FROM songs ORDER BY title COLLATE NOCASE ASC")
    fun getSongsPaged(): PagingSource<Int, SongEntity>

    @Query("SELECT * FROM songs WHERE is_favorite = 1 ORDER BY title COLLATE NOCASE ASC")
    fun getFavoriteSongsPaged(): PagingSource<Int, SongEntity>

    @Query("SELECT * FROM songs WHERE is_favorite = 1 ORDER BY title COLLATE NOCASE ASC")
    fun getFavoriteSongs(): Flow<List<SongEntity>>

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
        SET lyrics = :lyrics, disc_number = :discNumber, composer = :composer, genre = :genre, is_enriched = 1
        WHERE id = :id
        """,
    )
    suspend fun updateEnrichedData(
        id: Long,
        lyrics: String?,
        discNumber: Int,
        composer: String?,
        genre: String?,
    ): Int

    /** Callers must chunk [ids]: SQLite limits bound variables (999 before API 30). */
    @Query("DELETE FROM songs WHERE id IN (:ids)")
    suspend fun deleteSongsByIds(ids: List<Long>): Int

    @Query("DELETE FROM songs")
    suspend fun deleteAll(): Int
}
