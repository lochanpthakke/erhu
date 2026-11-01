package com.erhu.erhu.core.playback.waveform

import com.erhu.erhu.core.model.BandLevels
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sqrt

/** Receives decoded PCM as it streams out of the decoder. */
internal interface PcmSink {
    fun addPcm16(
        samples: ShortArray,
        sampleCount: Int,
        channels: Int,
        sampleRate: Int,
        ptsUs: Long,
    )

    fun addPcmFloat(
        samples: FloatArray,
        sampleCount: Int,
        channels: Int,
        sampleRate: Int,
        ptsUs: Long,
    )
}

/**
 * Three-band levels of the range [startUs, startUs + durationUs) of a track, while it streams in
 * (nothing decoded is kept). Each mono frame is split by two low-passes (two one-pole stages each)
 * into lows (below [LOW_HZ]), mids and highs (above [HIGH_HZ]); each band's energy goes into its time
 * bin. A display approximation: the bands overlap gently, each tone still lands mostly in its own band. [finish]
 * gives the RMS per band and bin, absolute (full scale = 1), so two tracks compare honestly.
 */
internal class BandWaveformAccumulator(
    private val bins: Int,
    private val durationUs: Long,
    private val startUs: Long,
) : PcmSink {
    private val sums = Array(BANDS) { DoubleArray(bins) }
    private val counts = IntArray(bins)
    private var low1 = 0f
    private var lowState = 0f
    private var mid1 = 0f
    private var midState = 0f
    private var rate = 0
    private var lowCoef = 0f
    private var midCoef = 0f

    init {
        require(bins > 0 && durationUs > 0) { "bins and duration must be positive" }
    }

    override fun addPcm16(
        samples: ShortArray,
        sampleCount: Int,
        channels: Int,
        sampleRate: Int,
        ptsUs: Long,
    ) = addFrames(sampleCount, channels, sampleRate, ptsUs) { samples[it] / PCM16_SCALE }

    override fun addPcmFloat(
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
        if (sampleRate != rate) {
            rate = sampleRate
            lowCoef = onePole(LOW_HZ, sampleRate)
            midCoef = onePole(HIGH_HZ, sampleRate)
        }
        for (frame in 0 until sampleCount / channels) {
            var mono = 0f
            for (channel in 0 until channels) mono += sample(frame * channels + channel)
            mono /= channels
            // Filters run on every frame (also outside the range) so they are settled when it starts.
            if (mono.isFinite()) {
                low1 += lowCoef * (mono - low1)
                lowState += lowCoef * (low1 - lowState)
                mid1 += midCoef * (mono - mid1)
                midState += midCoef * (mid1 - midState)
                val timeUs = ptsUs + frame * MICROS_PER_SECOND / sampleRate - startUs
                if (timeUs in 0 until durationUs) add((timeUs.toDouble() * bins / durationUs).toInt(), mono)
            }
        }
    }

    private fun add(
        bin: Int,
        mono: Float,
    ) {
        val low = lowState
        val mid = midState - lowState
        val high = mono - midState
        sums[0][bin] += (low * low).toDouble()
        sums[1][bin] += (mid * mid).toDouble()
        sums[2][bin] += (high * high).toDouble()
        counts[bin]++
    }

    /** Band levels, or null when no frame fell in the range. */
    fun finish(): BandLevels? {
        if (counts.all { it == 0 }) return null

        fun band(i: Int) = FloatArray(bins) { if (counts[it] == 0) 0f else sqrt(sums[i][it] / counts[it]).toFloat().coerceIn(0f, 1f) }
        return BandLevels(band(0), band(1), band(2))
    }

    private companion object {
        const val BANDS = 3
        const val TWO_PI = 2.0 * PI
        const val LOW_HZ = 250f
        const val HIGH_HZ = 4_000f
        const val PCM16_SCALE = 32_768f
        const val MICROS_PER_SECOND = 1_000_000L

        fun onePole(
            hz: Float,
            sampleRate: Int,
        ): Float = (1.0 - exp(-TWO_PI * hz / sampleRate)).toFloat()
    }
}
