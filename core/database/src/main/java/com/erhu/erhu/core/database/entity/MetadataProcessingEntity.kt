package com.erhu.erhu.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

const val CURRENT_METADATA_PROCESSING_VERSION = 1

@Entity(tableName = "metadata_processing")
data class MetadataProcessingEntity(
    @PrimaryKey
    @ColumnInfo(name = "file_path")
    val filePath: String,
    @ColumnInfo(name = "file_size")
    val fileSize: Long,
    @ColumnInfo(name = "date_modified")
    val dateModified: Long,
    @ColumnInfo(name = "processing_version")
    val processingVersion: Int = CURRENT_METADATA_PROCESSING_VERSION,
    @ColumnInfo(name = "status")
    val status: String,
    @ColumnInfo(name = "last_processed_timestamp")
    val lastProcessedTimestamp: Long,
    @ColumnInfo(name = "confidence_score")
    val confidenceScore: Float = 0.0f,
    @ColumnInfo(name = "matched_source")
    val matchedSource: String? = null,
    @ColumnInfo(name = "failure_reason")
    val failureReason: String? = null,
)
