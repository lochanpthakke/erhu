package com.erhu.erhu.core.sound.dsp

/**
 * One band exactly as the processor applies it. The audio path and the response curve the UI
 * draws are both built from these through [BandFilter], so what is shown is what is heard.
 */
data class BandSpec(
    val type: FilterType,
    val frequencyHz: Float,
    val gainDb: Float,
    val q: Float,
    /** dB per octave for low and high pass: 12, 24 or 48. Ignored by the other types. */
    val slope: Int = 12,
    val enabled: Boolean = true,
)

/**
 * A band as one to four cascaded biquads: a low or high pass of 24 or 48 dB/octave is a
 * Butterworth cascade (2 or 4 stages, its own fixed Qs); every other filter is one stage.
 */
class BandFilter {
    private val stages = Array(MAX_STAGES) { BiquadFilter() }
    private var stageCount = 0

    val isIdentity: Boolean get() = stageCount == 0

    fun configure(
        spec: BandSpec,
        sampleRate: Int,
    ) {
        stageCount = 0
        if (!spec.enabled || sampleRate <= 0) return
        if (spec.type.hasGain && spec.gainDb == 0f) return
        val nyquistSafe = sampleRate * MAX_FREQ_TO_SAMPLE_RATE
        // Peaks, shelves and notches above what the stream can carry do nothing; pass filters are
        // clamped instead (a high pass at 19 kHz on a 32 kHz stream still removes nearly everything).
        if (!spec.type.isPass && spec.frequencyHz >= nyquistSafe) return
        val frequency = spec.frequencyHz.coerceAtMost(nyquistSafe)
        val qs = if (spec.type.isPass) passQs(spec.slope, spec.q) else floatArrayOf(spec.q)
        qs.forEachIndexed { index, q ->
            stages[index].configure(frequency, sampleRate, spec.gainDb, q, spec.type)
        }
        stageCount = qs.size
    }

    fun process(sample: Float): Float {
        var x = sample
        for (i in 0 until stageCount) x = stages[i].processSample(x)
        return x
    }

    fun copyStateFrom(other: BandFilter) {
        for (i in 0 until MAX_STAGES) stages[i].copyStateFrom(other.stages[i])
    }

    fun resetState() = stages.forEach { it.resetState() }

    fun responseDb(
        frequencyHz: Float,
        sampleRate: Int,
    ): Float {
        var total = 0f
        for (i in 0 until stageCount) total += stages[i].getFrequencyResponse(frequencyHz, sampleRate)
        return total
    }

    companion object {
        const val MAX_STAGES = 4
        const val MAX_FREQ_TO_SAMPLE_RATE = 0.45f
        val SLOPES = listOf(12, 24, 48)

        /** 12 dB/octave keeps the band's own Q (its resonance); steeper slopes are Butterworth. */
        fun passQs(
            slope: Int,
            q: Float,
        ): FloatArray =
            when (slope) {
                24 -> floatArrayOf(0.5412f, 1.3066f)
                48 -> floatArrayOf(0.5098f, 0.6013f, 0.9000f, 2.5629f)
                else -> floatArrayOf(q)
            }
    }
}

/** The combined response of [bands] at [frequencies], in dB, as the processor would apply them at [sampleRate]. */
fun combinedResponseDb(
    bands: List<BandSpec>,
    frequencies: FloatArray,
    sampleRate: Int,
): FloatArray {
    val filters = bands.map { spec -> BandFilter().also { it.configure(spec, sampleRate) } }
    return FloatArray(frequencies.size) { i -> filters.sumOf { it.responseDb(frequencies[i], sampleRate).toDouble() }.toFloat() }
}
