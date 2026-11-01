package com.erhu.erhu.core.playback.waveform

import com.erhu.erhu.core.database.repository.CachedWaveform
import com.erhu.erhu.core.model.Waveform

/** What a waveform was made from. A different URI, size or modification time means different audio. */
internal data class SourceFingerprint(
    val uri: String,
    val sizeBytes: Long,
    val dateModified: Long,
)

/** Bump when the extraction changes (bins, envelope), so older caches are regenerated. */
internal const val WAVEFORM_FORMAT_VERSION = 1

/** Envelope bins per track: enough detail for a phone-width waveform, a few hundred bytes cached. */
internal const val WAVEFORM_BINS = 480

/**
 * The cached waveform if it still describes the file behind [source]; null when it was made from
 * another file version or format, or when its data is damaged (wrong length).
 */
internal fun CachedWaveform.toWaveformIfValid(source: SourceFingerprint): Waveform? =
    takeIf {
        sourceUri == source.uri &&
            fileSize == source.sizeBytes &&
            dateModified == source.dateModified &&
            formatVersion == WAVEFORM_FORMAT_VERSION &&
            levels.size == WAVEFORM_BINS
    }?.let { Waveform(levels.toLevels()) }

internal fun Waveform.toCache(
    songId: Long,
    source: SourceFingerprint,
): CachedWaveform =
    CachedWaveform(
        songId = songId,
        sourceUri = source.uri,
        fileSize = source.sizeBytes,
        dateModified = source.dateModified,
        formatVersion = WAVEFORM_FORMAT_VERSION,
        levels = levels.toLevelBytes(),
    )
