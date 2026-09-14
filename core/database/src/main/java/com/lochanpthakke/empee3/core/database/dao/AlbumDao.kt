package com.lochanpthakke.empee3.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.lochanpthakke.empee3.core.database.entity.AlbumEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumDao {
    @Upsert
    suspend fun upsertAll(albums: List<AlbumEntity>)

    @Query("SELECT * FROM albums ORDER BY title ASC")
    fun getAllAlbums(): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums ORDER BY title ASC")
    fun getAlbumsPaged(): PagingSource<Int, AlbumEntity>

    @Query("SELECT * FROM albums WHERE id = :id LIMIT 1")
    suspend fun getAlbumById(id: Long): AlbumEntity?

    @Query("SELECT * FROM albums WHERE artist_id = :artistId ORDER BY year DESC, title ASC")
    fun getAlbumsByArtistId(artistId: Long): Flow<List<AlbumEntity>>

    @Query("DELETE FROM albums WHERE id NOT IN (:validIds)")
    suspend fun deleteAlbumsNotIn(validIds: List<Long>): Int

    @Query("DELETE FROM albums")
    suspend fun deleteAll(): Int
}
