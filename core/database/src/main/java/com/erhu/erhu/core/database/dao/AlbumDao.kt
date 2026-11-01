package com.erhu.erhu.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.erhu.erhu.core.database.entity.AlbumEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumDao {
    @Upsert
    suspend fun upsertAll(albums: List<AlbumEntity>)

    @Query("SELECT * FROM albums ORDER BY title COLLATE NOCASE ASC")
    fun getAllAlbums(): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums ORDER BY title COLLATE NOCASE ASC")
    fun getAlbumsPaged(): PagingSource<Int, AlbumEntity>

    @Query("SELECT * FROM albums WHERE id = :id LIMIT 1")
    suspend fun getAlbumById(id: Long): AlbumEntity?

    @Query("SELECT * FROM albums WHERE id = :id LIMIT 1")
    fun observeAlbumById(id: Long): Flow<AlbumEntity?>

    /** Albums listed under this artist (album artist, or a core artist of a collaborative album). */
    @Query(
        """
        SELECT a.* FROM albums a
        INNER JOIN album_artist_cross_ref l ON l.album_id = a.id
        WHERE l.artist_id = :artistId
        ORDER BY a.year DESC, a.title ASC
        """,
    )
    fun getAlbumsByArtistId(artistId: Long): Flow<List<AlbumEntity>>

    @Query("DELETE FROM albums")
    suspend fun deleteAll(): Int
}
