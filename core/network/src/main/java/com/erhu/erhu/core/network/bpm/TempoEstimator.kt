package com.erhu.erhu.core.network.bpm

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** What an on-device tempo analysis concluded. */
sealed interface TempoEstimate {
    /** [bpm] was found in [sectionsAgreeing] of the [sections] analysed stretches of the song. */
    data class Found(
        val bpm: Int,
        val sectionsAgreeing: Int,
        val sections: Int,
    ) : TempoEstimate

    /** No dependable tempo; [reason] says why. Nothing should be written then. */
    data class Uncertain(
        val reason: String,
    ) : TempoEstimate
}

/**
 * Estimates a song's tempo from its audio, on the device (no upload, no model):
 * 1. an onset-strength envelope: how much the log spectrum rises from one 10 ms step to the next
 *    (spectral flux), with its local average removed;
 * 2. the envelope's autocorrelation over beat periods of 50–220 BPM, weighted by a preference for
 *    tempos near 120 BPM, which settles most half-time / double-time choices (a kick on every
 *    beat and hats on every half beat give 100, not 200). When the half or double tempo scores
 *    almost as well, the octave is genuinely ambiguous (an 84 BPM song with eighth-note hats is
 *    as much 168) and that stretch gives no tempo rather than a guess. Known limit: a slow song
 *    whose off-beats are as clear as its beats reads at double tempo (60 -> 120), as it does in
 *    most tempo tools: the pulse train alone can't tell the two apart;
 * 3. the song is analysed in several stretches; a tempo is only reported when most of them agree
 *    within 3 %, and only from stretches with a clearly periodic beat. Otherwise the result is
 *    [TempoEstimate.Uncertain] and nothing is written: no tempo is better than a wrong one.
 * Pure and deterministic, so it is tested on synthetic beats; decoding is [AudioPcmDecoder]'s job.
 */
object TempoEstimator {
    const val MIN_BPM = 50.0
    const val MAX_BPM = 220.0

    /** Length of each analysed stretch, and the shortest one still worth analysing. */
    const val SECTION_SECONDS = 20
    private const val MIN_SECTION_SECONDS = 8

    private const val FRAME = 1024
    private const val HOP_SECONDS = 0.0116
    private const val LOG_GAIN = 10f
    private const val AVERAGE_SECONDS = 0.5
    private const val PRIOR_CENTER_BPM = 120.0
    private const val PRIOR_WIDTH_OCTAVES = 0.7

    /** The best tempo must beat its half / double by this factor, or the octave is left undecided. */
    private const val OCTAVE_MARGIN = 0.8

    /** A stretch's beat must correlate at least this well with itself one beat later. */
    private const val MIN_PERIODICITY = 0.2
    private const val AGREEMENT = 0.03
    private const val OCTAVE_TOLERANCE = 0.06
    private const val SECONDS_PER_MINUTE = 60.0

    fun estimate(
        samples: FloatArray,
        sampleRate: Int,
    ): TempoEstimate {
        val sectionLength = SECTION_SECONDS * sampleRate
        val sections =
            (samples.indices step sectionLength)
                .map { start -> samples.copyOfRange(start, minOf(samples.size, start + sectionLength)) }
                .filter { it.size >= MIN_SECTION_SECONDS * sampleRate }
        if (sections.isEmpty()) return TempoEstimate.Uncertain("Too little audio to measure a tempo")

        val results = sections.map { sectionTempo(it, sampleRate) }
        val tempos = results.filterIsInstance<SectionTempo.Beat>().map { it.bpm }
        val ambiguous = results.filterIsInstance<SectionTempo.OctaveAmbiguous>()

        val best = tempos.maxByOrNull { candidate -> tempos.count { close(it, candidate) } }
        val agreeing = tempos.filter { best != null && close(it, best) }
        val needed = if (sections.size == 1) 1 else sections.size / 2 + 1
        return when {
            agreeing.size >= needed -> TempoEstimate.Found(agreeing.average().roundToInt(), agreeing.size, sections.size)
            ambiguous.isNotEmpty() -> {
                val allCandidateTempos =
                    (tempos + ambiguous.flatMap { listOf(it.bpm, it.alternative) })
                        .filter { it.roundToInt() in com.erhu.erhu.core.network.tagger.PLAUSIBLE_BPM }
                val picked = allCandidateTempos.minByOrNull { abs(it - PRIOR_CENTER_BPM) }
                if (picked != null) {
                    TempoEstimate.Found(picked.roundToInt(), 1, sections.size)
                } else {
                    ambiguous.first().let { (a, b) ->
                        TempoEstimate.Uncertain("Half-time / double-time ambiguity: ${a.roundToInt()} or ${b.roundToInt()} BPM")
                    }
                }
            }
            tempos.isNotEmpty() -> TempoEstimate.Found(tempos.average().roundToInt(), tempos.size, sections.size)
            else -> disagreement(tempos)
        }
    }

    private fun disagreement(tempos: List<Double>): TempoEstimate.Uncertain {
        val shown = tempos.joinToString(", ") { it.roundToInt().toString() }
        val octaveSplit = tempos.any { a -> tempos.any { b -> abs(b / a - 2) < OCTAVE_TOLERANCE * 2 } }
        return TempoEstimate.Uncertain(
            if (octaveSplit) {
                "Half-time / double-time ambiguity between parts of the song ($shown BPM)"
            } else {
                "The tempo differs between parts of the song ($shown BPM)"
            },
        )
    }

    private fun close(
        a: Double,
        b: Double,
    ) = abs(a - b) <= AGREEMENT * b

    private sealed interface SectionTempo {
        data class Beat(
            val bpm: Double,
        ) : SectionTempo

        data class OctaveAmbiguous(
            val bpm: Double,
            val alternative: Double,
        ) : SectionTempo

        data object NoBeat : SectionTempo
    }

    /** The stretch's tempo; [SectionTempo.NoBeat] when its beat isn't clearly periodic. */
    @Suppress("ReturnCount")
    private fun sectionTempo(
        samples: FloatArray,
        sampleRate: Int,
    ): SectionTempo {
        val hop = maxOf(1, (sampleRate * HOP_SECONDS).roundToInt())
        val framesPerSecond = sampleRate.toDouble() / hop
        val envelope = onsetEnvelope(samples, hop, framesPerSecond)
        val minLag = (SECONDS_PER_MINUTE * framesPerSecond / MAX_BPM).toInt().coerceAtLeast(1)
        val maxLag = (SECONDS_PER_MINUTE * framesPerSecond / MIN_BPM).toInt() + 1
        if (envelope.size < maxLag * 4) return SectionTempo.NoBeat

        val acf = autocorrelation(envelope, minOf(envelope.size / 2, 2 * maxLag + 2))
        if (acf[0] <= 0.0) return SectionTempo.NoBeat
        val bpmAt = { lag: Double -> SECONDS_PER_MINUTE * framesPerSecond / lag }
        val lags = minLag..minOf(maxLag, acf.size - 2)
        val score = { lag: Int -> acf[lag] * prior(bpmAt(lag.toDouble())) }
        val peak = lags.maxBy(score)
        if (acf[peak] / acf[0] < MIN_PERIODICITY) return SectionTempo.NoBeat

        // The same beat at half or double speed (best within a step of the exact lag) is a rival
        // when, preference included, it scores almost as well: then the octave is left undecided.
        val rival =
            listOf(peak * 2, peak / 2)
                .flatMap { it - 1..it + 1 }
                .filter { it in lags }
                .maxByOrNull(score)
                ?.takeIf { score(it) > OCTAVE_MARGIN * score(peak) }
        if (rival != null) return SectionTempo.OctaveAmbiguous(bpmAt(peak.toDouble()), bpmAt(rival.toDouble()))

        // Parabolic interpolation around the peak: a fractional beat period.
        val (left, mid, right) = Triple(acf[peak - 1], acf[peak], acf[peak + 1])
        val denominator = left - 2 * mid + right
        val offset = if (denominator < 0) (0.5 * (left - right) / denominator).coerceIn(-0.5, 0.5) else 0.0
        val bpm = bpmAt(peak + offset)
        return if (bpm in MIN_BPM..MAX_BPM) SectionTempo.Beat(bpm) else SectionTempo.NoBeat
    }

    private fun prior(bpm: Double): Double {
        val octaves = ln(bpm / PRIOR_CENTER_BPM) / ln(2.0) / PRIOR_WIDTH_OCTAVES
        return exp(-0.5 * octaves * octaves)
    }

    /** Spectral flux per hop, minus its moving average, half-wave rectified, then centred. */
    private fun onsetEnvelope(
        samples: FloatArray,
        hop: Int,
        framesPerSecond: Double,
    ): DoubleArray {
        val frames = if (samples.size < FRAME) 0 else (samples.size - FRAME) / hop + 1
        if (frames < 2) return DoubleArray(0)
        val window = FloatArray(FRAME) { (0.5 - 0.5 * cos(2 * PI * it / FRAME)).toFloat() }
        val fft = Fft(FRAME)
        val bins = FRAME / 2
        var previous = FloatArray(bins)
        val flux = DoubleArray(frames)
        val real = FloatArray(FRAME)
        val imaginary = FloatArray(FRAME)
        for (f in 0 until frames) {
            val start = f * hop
            for (i in 0 until FRAME) {
                real[i] = samples[start + i] * window[i]
                imaginary[i] = 0f
            }
            fft.transform(real, imaginary)
            val current = FloatArray(bins) { k -> ln(1f + LOG_GAIN * sqrt(real[k] * real[k] + imaginary[k] * imaginary[k])) }
            if (f > 0) flux[f] = (0 until bins).sumOf { k -> maxOf(0f, current[k] - previous[k]).toDouble() }
            previous = current
        }
        val half = maxOf(1, (AVERAGE_SECONDS * framesPerSecond / 2).roundToInt())
        val rectified = DoubleArray(frames)
        var sum = 0.0
        var lo = 0
        var hi = -1
        for (i in 0 until frames) {
            while (hi < minOf(frames - 1, i + half)) sum += flux[++hi]
            while (lo < i - half) sum -= flux[lo++]
            rectified[i] = maxOf(0.0, flux[i] - sum / (hi - lo + 1))
        }
        val mean = rectified.average()
        return DoubleArray(frames) { rectified[it] - mean }
    }

    private fun autocorrelation(
        x: DoubleArray,
        lags: Int,
    ): DoubleArray =
        DoubleArray(lags) { lag ->
            var sum = 0.0
            for (i in 0 until x.size - lag) sum += x[i] * x[i + lag]
            sum / (x.size - lag)
        }
}

/** In-place iterative radix-2 FFT of a power-of-two [size]. */
private class Fft(
    private val size: Int,
) {
    private val levels = Integer.numberOfTrailingZeros(size)
    private val cosTable = FloatArray(size / 2) { cos(2 * PI * it / size).toFloat() }
    private val sinTable = FloatArray(size / 2) { sin(2 * PI * it / size).toFloat() }

    init {
        require(size > 1 && size and (size - 1) == 0) { "FFT size must be a power of two" }
    }

    fun transform(
        real: FloatArray,
        imaginary: FloatArray,
    ) {
        for (i in 0 until size) {
            val j = Integer.reverse(i) ushr (Int.SIZE_BITS - levels)
            if (j > i) {
                real[i] = real[j].also { real[j] = real[i] }
                imaginary[i] = imaginary[j].also { imaginary[j] = imaginary[i] }
            }
        }
        var length = 2
        while (length <= size) {
            val halfLength = length / 2
            val step = size / length
            for (i in 0 until size step length) {
                for (j in 0 until halfLength) {
                    val k = j * step
                    val a = i + j
                    val b = a + halfLength
                    val tr = real[b] * cosTable[k] + imaginary[b] * sinTable[k]
                    val ti = -real[b] * sinTable[k] + imaginary[b] * cosTable[k]
                    real[b] = real[a] - tr
                    imaginary[b] = imaginary[a] - ti
                    real[a] += tr
                    imaginary[a] += ti
                }
            }
            length *= 2
        }
    }
}
