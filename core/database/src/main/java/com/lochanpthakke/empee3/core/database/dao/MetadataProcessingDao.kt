package com.lochanpthakke.empee3.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.lochanpthakke.empee3.core.database.entity.MetadataProcessingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MetadataProcessingDao {
    @Upsert
    suspend fun upsertProcessingRecord(record: MetadataProcessingEntity)

    @Upsert
    suspend fun upsertProcessingRecords(records: List<MetadataProcessingEntity>)

    @Query("SELECT * FROM metadata_processing WHERE file_path = :filePath LIMIT 1")
    suspend fun getProcessingRecord(filePath: String): MetadataProcessingEntity?

    @Query("SELECT * FROM metadata_processing WHERE file_path = :filePath LIMIT 1")
    fun observeProcessingRecord(filePath: String): Flow<MetadataProcessingEntity?>

    @Query("SELECT * FROM metadata_processing WHERE file_path IN (:filePaths)")
    suspend fun getProcessingRecords(filePaths: List<String>): List<MetadataProcessingEntity>

    @Query("SELECT * FROM metadata_processing WHERE status = :status")
    fun getRecordsByStatus(status: String): Flow<List<MetadataProcessingEntity>>

    @Query("DELETE FROM metadata_processing WHERE file_path NOT IN (:validFilePaths)")
    suspend fun deleteRecordsNotIn(validFilePaths: List<String>): Int

    @Query("DELETE FROM metadata_processing")
    suspend fun deleteAll(): Int
}
