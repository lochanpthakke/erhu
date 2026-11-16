package com.erhu.erhu.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.erhu.erhu.core.database.entity.CustomSongArtworkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomSongArtworkDao {
    @Query("SELECT * FROM custom_song_artwork WHERE song_id = :songId LIMIT 1")
    fun observeCustomArtwork(songId: Long): Flow<CustomSongArtworkEntity?>

    @Query("SELECT * FROM custom_song_artwork WHERE song_id = :songId LIMIT 1")
    suspend fun getCustomArtwork(songId: Long): CustomSongArtworkEntity?

    @Query("SELECT * FROM custom_song_artwork")
    fun observeAllCustomArtwork(): Flow<List<CustomSongArtworkEntity>>

    @Query("SELECT * FROM custom_song_artwork")
    suspend fun getAllCustomArtwork(): List<CustomSongArtworkEntity>

    @Upsert
    suspend fun upsert(entity: CustomSongArtworkEntity)

    @Query("DELETE FROM custom_song_artwork WHERE song_id = :songId")
    suspend fun delete(songId: Long): Int
}
