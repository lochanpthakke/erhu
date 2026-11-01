package com.erhu.erhu.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cached waveform of one song: [levels] holds one byte (0..255) per envelope bin. The source
 * fingerprint ([sourceUri], [fileSize], [dateModified]) and [formatVersion] decide whether the
 * cache still describes the file; anything else is regenerated. A cache: safe to lose.
 */
@Entity(tableName = "waveform_cache")
class WaveformCacheEntity(
    @PrimaryKey
    @ColumnInfo(name = "song_id")
    val songId: Long,
    @ColumnInfo(name = "source_uri")
    val sourceUri: String,
    @ColumnInfo(name = "file_size")
    val fileSize: Long,
    @ColumnInfo(name = "date_modified")
    val dateModified: Long,
    @ColumnInfo(name = "format_version")
    val formatVersion: Int,
    @ColumnInfo(name = "levels", typeAffinity = ColumnInfo.BLOB)
    val levels: ByteArray,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)
