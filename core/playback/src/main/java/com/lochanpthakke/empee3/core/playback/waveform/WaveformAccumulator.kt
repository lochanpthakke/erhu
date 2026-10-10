package com.lochanpthakke.empee3.core.playback.waveform

import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Reduces decoded PCM to a fixed number of amplitude bins while it streams in, so no decoded audio
 * is kept: each frame is downmixed to mono (mean of its channels) and its energy is added to the bin
 * its timestamp falls into ([bins] bins evenly over [durationUs]). [finish] gives the RMS of every
 * bin, normalized so the loudest bin is 1. Memory: two arrays of [bins] values, whatever the length.
 */
internal class WaveformAccumulator(
    private val bins: Int,
    private val durationUs: Long,
) {
    private val sumSquares = DoubleArray(bins)
    private val counts = IntArray(bins)
    private var frames = 0L

    init {
        require(bins > 0) { "bins must be positive" }
        require(durationUs > 0) { "duration must be known" }
    }

    /** Interleaved 16-bit samples: [sampleCount] values, [channels] per frame, first frame at [ptsUs]. */
    fun addPcm16(
        samples: ShortArray,
        sampleCount: Int,
        channels: Int,
        sampleRate: Int,
        ptsUs: Long,
    ) = addFrames(sampleCount, channels, sampleRate, ptsUs) { samples[it] / PCM16_SCALE }

    /** Interleaved float samples (nominally -1..1). */
    fun addPcmFloat(
        samples: FloatArray,
        sampleCount: Int,
        channels: Int,
        sampleRate: Int,
        ptsUs: Long,
    ) = addFrames(sampleCount, channels, sampleRate, ptsUs) { samples[it] }

    private inline fun addFrames(
        sampleCount: Int,
        channels: Int,
        sampleRate: Int,
        ptsUs: Long,
        sample: (Int) -> Float,
    ) {
        if (channels <= 0 || sampleRate <= 0) return
        val frameCount = sampleCount / channels
        for (frame in 0 until frameCount) {
            var mono = 0f
            val base = frame * channels
            for (channel in 0 until channels) mono += sample(base + channel)
            mono /= channels
            if (!mono.isFinite()) continue
            val timeUs = ptsUs + frame * MICROS_PER_SECOND / sampleRate
            val bin = (timeUs.toDouble() * bins / durationUs).toInt().coerceIn(0, bins - 1)
            sumSquares[bin] += (mono * mono).toDouble()
            counts[bin]++
        }
        frames += frameCount
    }

    /** Levels 0..1 per bin, or null when no audio frame was seen at all (empty or undecodable audio). */
    fun finish(): FloatArray? {
        if (frames == 0L) return null
        val rms = FloatArray(bins) { if (counts[it] == 0) 0f else sqrt(sumSquares[it] / counts[it]).toFloat() }
        val max = rms.max()
        return if (max <= 0f) FloatArray(bins) else FloatArray(bins) { (rms[it] / max).coerceIn(0f, 1f) }
    }

    private companion object {
        const val PCM16_SCALE = 32_768f
        const val MICROS_PER_SECOND = 1_000_000L
    }
}

/** One byte per level (0..255): the compact form kept in the cache. */
internal fun FloatArray.toLevelBytes(): ByteArray = ByteArray(size) { (this[it].coerceIn(0f, 1f) * MAX_LEVEL_BYTE).roundToInt().toByte() }

internal fun ByteArray.toLevels(): FloatArray = FloatArray(size) { (this[it].toInt() and MAX_LEVEL_BYTE) / MAX_LEVEL_BYTE.toFloat() }

private const val MAX_LEVEL_BYTE = 0xFF
