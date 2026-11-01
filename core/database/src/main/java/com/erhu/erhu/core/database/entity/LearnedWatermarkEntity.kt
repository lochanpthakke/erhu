package com.erhu.erhu.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "learned_watermarks")
data class LearnedWatermarkEntity(
    @PrimaryKey
    val token: String,
    @ColumnInfo(name = "frequency_count")
    val frequencyCount: Int = 1,
    @ColumnInfo(name = "date_discovered")
    val dateDiscovered: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,
)
