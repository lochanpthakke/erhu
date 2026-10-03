package com.lochanpthakke.empee3.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/** Derived: a standalone artist's name found inside an unresolved credit (presentation only). */
@Entity(
    tableName = "unverified_credit_segment",
    primaryKeys = ["song_id", "artist_id", "segment_index"],
    indices = [Index("segment_key")],
)
data class UnverifiedCreditSegmentEntity(
    @ColumnInfo(name = "song_id")
    val songId: Long,
    @ColumnInfo(name = "artist_id")
    val artistId: Long,
    @ColumnInfo(name = "segment_key")
    val segmentKey: String,
    @ColumnInfo(name = "segment_index")
    val segmentIndex: Int,
)
