package com.lochanpthakke.empee3.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.lochanpthakke.empee3.core.database.entity.SongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Upsert
    suspend fun upsertAll(songs: List<SongEntity>)

    @Query("SELECT * FROM songs ORDER BY title ASC")
    fun getAllSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs ORDER BY title ASC")
    fun getSongsPaged(): PagingSource<Int, SongEntity>

    @Query("SELECT * FROM songs WHERE is_favorite = 1 ORDER BY title ASC")
    fun getFavoriteSongsPaged(): PagingSource<Int, SongEntity>

    @Query("SELECT * FROM songs WHERE is_favorite = 1 ORDER BY title ASC")
    fun getFavoriteSongs(): Flow<List<SongEntity>>

    @Query("UPDATE songs SET is_favorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(
        id: Long,
        isFavorite: Boolean,
    ): Int

    @Query("SELECT * FROM songs WHERE id = :id LIMIT 1")
    suspend fun getSongById(id: Long): SongEntity?

    @Query("SELECT * FROM songs WHERE id IN (:ids)")
    suspend fun getSongsByIds(ids: List<Long>): List<SongEntity>

    @Query("SELECT * FROM songs WHERE album_id = :albumId ORDER BY track_number ASC, title ASC")
    fun getSongsByAlbumId(albumId: Long): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE artist_id = :artistId ORDER BY title ASC")
    fun getSongsByArtistId(artistId: Long): Flow<List<SongEntity>>

    @Query(
        """
        SELECT * FROM songs 
        WHERE title LIKE '%' || :query || '%' 
           OR artist LIKE '%' || :query || '%' 
           OR album LIKE '%' || :query || '%' 
        ORDER BY title ASC
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

    @Query("DELETE FROM songs WHERE id NOT IN (:validIds)")
    suspend fun deleteSongsNotIn(validIds: List<Long>): Int

    @Query("DELETE FROM songs")
    suspend fun deleteAll(): Int
}
