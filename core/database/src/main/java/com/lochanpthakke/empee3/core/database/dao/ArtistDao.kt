package com.lochanpthakke.empee3.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.lochanpthakke.empee3.core.database.entity.ArtistEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ArtistDao {
    @Upsert
    suspend fun upsertAll(artists: List<ArtistEntity>)

    @Query("SELECT * FROM artists ORDER BY name ASC")
    fun getAllArtists(): Flow<List<ArtistEntity>>

    @Query("SELECT * FROM artists ORDER BY name ASC")
    fun getArtistsPaged(): PagingSource<Int, ArtistEntity>

    @Query("SELECT * FROM artists WHERE id = :id LIMIT 1")
    suspend fun getArtistById(id: Long): ArtistEntity?

    @Query("DELETE FROM artists WHERE id NOT IN (:validIds)")
    suspend fun deleteArtistsNotIn(validIds: List<Long>): Int

    @Query("DELETE FROM artists")
    suspend fun deleteAll(): Int
}
