package com.lochanpthakke.empee3.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.lochanpthakke.empee3.core.database.entity.WaveformCacheEntity

@Dao
interface WaveformCacheDao {
    @Query("SELECT * FROM waveform_cache WHERE song_id = :songId LIMIT 1")
    suspend fun get(songId: Long): WaveformCacheEntity?

    @Upsert
    suspend fun upsert(entry: WaveformCacheEntity)

    @Query("DELETE FROM waveform_cache WHERE song_id = :songId")
    suspend fun delete(songId: Long)
}
