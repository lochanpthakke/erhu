package com.lochanpthakke.empee3.core.database.repository

import com.lochanpthakke.empee3.core.database.dao.WaveformCacheDao
import com.lochanpthakke.empee3.core.database.entity.WaveformCacheEntity
import javax.inject.Inject
import javax.inject.Singleton

/** One cached waveform as stored: the source fingerprint it was made from and its quantized levels. */
class CachedWaveform(
    val songId: Long,
    val sourceUri: String,
    val fileSize: Long,
    val dateModified: Long,
    val formatVersion: Int,
    val levels: ByteArray,
)

/** Persistence of generated waveforms (compact levels, never PCM). Validity against the file is decided by the caller. */
@Singleton
class WaveformCacheStore
    @Inject
    constructor(
        private val dao: WaveformCacheDao,
        private val timeSource: TimeSource,
    ) {
        suspend fun get(songId: Long): CachedWaveform? =
            dao.get(songId)?.let { CachedWaveform(it.songId, it.sourceUri, it.fileSize, it.dateModified, it.formatVersion, it.levels) }

        suspend fun put(entry: CachedWaveform) {
            dao.upsert(
                WaveformCacheEntity(
                    songId = entry.songId,
                    sourceUri = entry.sourceUri,
                    fileSize = entry.fileSize,
                    dateModified = entry.dateModified,
                    formatVersion = entry.formatVersion,
                    levels = entry.levels,
                    createdAt = timeSource.now(),
                ),
            )
        }

        suspend fun remove(songId: Long) = dao.delete(songId)
    }
