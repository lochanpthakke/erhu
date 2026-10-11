package com.lochanpthakke.empee3.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lochanpthakke.empee3.core.database.entity.LearnedWatermarkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LearnedWatermarkDao {
    @Query("SELECT token FROM learned_watermarks WHERE is_active = 1")
    fun observeActiveTokens(): Flow<List<String>>

    @Query("SELECT token FROM learned_watermarks WHERE is_active = 1")
    suspend fun getAllActiveTokensSync(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertToken(watermark: LearnedWatermarkEntity)

    @Query("SELECT * FROM learned_watermarks WHERE token = :token LIMIT 1")
    suspend fun getToken(token: String): LearnedWatermarkEntity?

    @Query("UPDATE learned_watermarks SET frequency_count = frequency_count + 1 WHERE token = :token")
    suspend fun incrementFrequency(token: String): Int

    suspend fun insertOrIncrement(token: String) {
        val trimmed = token.trim()
        if (trimmed.length < 2) return
        val existing = getToken(trimmed)
        if (existing != null) {
            incrementFrequency(trimmed)
        } else {
            upsertToken(LearnedWatermarkEntity(token = trimmed, frequencyCount = 1))
        }
    }
}
