package com.lochanpthakke.empee3.core.sound.visualizer

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * In-place radix-2 FFT of a fixed [size] (a power of two). Twiddles and bit-reversal are
 * computed once; [transform] allocates nothing.
 */
class Fft(
    val size: Int,
) {
    private val cosTable: FloatArray
    private val sinTable: FloatArray
    private val reversed: IntArray

    init {
        require(size >= 2 && size and (size - 1) == 0) { "FFT size must be a power of two" }
        cosTable = FloatArray(size / 2) { cos(2 * PI * it / size).toFloat() }
        sinTable = FloatArray(size / 2) { -sin(2 * PI * it / size).toFloat() }
        val bits = Integer.numberOfTrailingZeros(size)
        reversed = IntArray(size) { Integer.reverse(it) ushr (Int.SIZE_BITS - bits) }
    }

    fun transform(
        re: FloatArray,
        im: FloatArray,
    ) {
        for (i in 0 until size) {
            val j = reversed[i]
            if (j > i) {
                val tr = re[i]
                re[i] = re[j]
                re[j] = tr
                val ti = im[i]
                im[i] = im[j]
                im[j] = ti
            }
        }
        var half = 1
        while (half < size) {
            val step = size / (half * 2)
            var start = 0
            while (start < size) {
                for (k in 0 until half) {
                    val wr = cosTable[k * step]
                    val wi = sinTable[k * step]
                    val a = start + k
                    val b = a + half
                    val tr = re[b] * wr - im[b] * wi
                    val ti = re[b] * wi + im[b] * wr
                    re[b] = re[a] - tr
                    im[b] = im[a] - ti
                    re[a] += tr
                    im[a] += ti
                }
                start += half * 2
            }
            half *= 2
        }
    }
}

/**
 * Turns a window of mono samples into [bandCount] levels (0..1), log-spaced from [minHz] to
 * [maxHz] (capped below Nyquist). Band edges are FFT bins of the real [sampleRate]; a band too
 * narrow for the resolution uses its nearest bin. Each level is the band's mean power in dB,
 * mapped over a [dynamicRangeDb] window under an adaptive reference that follows the loudest
 * recent band and decays slowly, so a single transient cannot flatten what follows. Below
 * [silenceDb] (RMS) the frame counts as silence and every level is 0: nothing is invented.
 * Reuses all of its buffers.
 */
class SpectrumAnalyzer(
    val sampleRate: Int,
    val bandCount: Int,
    fftSize: Int = DEFAULT_FFT_SIZE,
    minHz: Float = DEFAULT_MIN_HZ,
    maxHz: Float = DEFAULT_MAX_HZ,
) {
    private val fft = Fft(fftSize)
    val windowSize: Int = fftSize
    private val window = FloatArray(fftSize) { (HANN_HALF - HANN_HALF * cos(2 * PI * it / (fftSize - 1))).toFloat() }
    private val windowPowerGain = window.sumOf { (it * it).toDouble() }.toFloat()
    private val re = FloatArray(fftSize)
    private val im = FloatArray(fftSize)
    private val bandDb = FloatArray(bandCount)
    private var referenceDb = INITIAL_REFERENCE_DB

    /** First bin of each band, and one past its last bin. */
    private val bandStart = IntArray(bandCount)
    private val bandEnd = IntArray(bandCount)

    /** Centre frequency (Hz) of each band, for labels; consistent with the bins actually used. */
    val bandCenterHz = FloatArray(bandCount)

    var dynamicRangeDb: Float = DEFAULT_DYNAMIC_RANGE_DB
    var silenceDb: Float = DEFAULT_SILENCE_DB

    /** Fall rate of the adaptive reference, dB per second. */
    var referenceDecayDbPerSecond: Float = DEFAULT_REFERENCE_DECAY

    init {
        require(sampleRate > 0 && bandCount > 0)
        val binHz = sampleRate.toFloat() / fftSize
        val nyquistBin = fftSize / 2
        val top = minOf(maxHz, sampleRate / 2f * NYQUIST_MARGIN)
        val logMin = ln(minHz)
        val logMax = ln(max(top, minHz * 2))
        for (b in 0 until bandCount) {
            val loHz = exp(logMin + (logMax - logMin) * b / bandCount)
            val hiHz = exp(logMin + (logMax - logMin) * (b + 1) / bandCount)
            val lo = (loHz / binHz).roundToInt().coerceIn(1, nyquistBin - 1)
            val hi = (hiHz / binHz).roundToInt().coerceIn(lo + 1, nyquistBin)
            bandStart[b] = lo
            bandEnd[b] = hi
            bandCenterHz[b] = (lo + hi - 1) / 2f * binHz
        }
    }

    /**
     * Analyses the first [windowSize] values of [samples] (mono, nominally -1..1) into [out]
     * (size [bandCount], 0..1). [elapsedSeconds] since the previous call drives the reference decay.
     * Returns false for silence (all levels 0).
     */
    fun analyze(
        samples: FloatArray,
        out: FloatArray,
        elapsedSeconds: Float,
    ): Boolean {
        var sumSquares = 0.0
        for (i in 0 until windowSize) {
            val s = samples[i]
            sumSquares += s * s
            re[i] = s * window[i]
            im[i] = 0f
        }
        val rmsDb = POWER_DB * log10(sumSquares / windowSize + TINY).toFloat()
        decayReference(elapsedSeconds)
        if (rmsDb < silenceDb) {
            out.fill(0f, 0, bandCount)
            return false
        }
        fft.transform(re, im)
        var loudest = MIN_DB
        for (b in 0 until bandCount) {
            var power = 0f
            for (k in bandStart[b] until bandEnd[b]) power += re[k] * re[k] + im[k] * im[k]
            // Mean power per bin, normalized by window energy and size; levels are relative to the reference below.
            val meanPower = power / (bandEnd[b] - bandStart[b]) * 2 / windowPowerGain / windowSize
            val db = (POWER_DB * log10(meanPower.toDouble() + TINY)).toFloat()
            bandDb[b] = db
            if (db > loudest) loudest = db
        }
        if (loudest > referenceDb) referenceDb = loudest
        val floor = referenceDb - dynamicRangeDb
        for (b in 0 until bandCount) out[b] = ((bandDb[b] - floor) / dynamicRangeDb).coerceIn(0f, 1f)
        return true
    }

    /** The adaptive reference falls back over time, never below a quiet-music level. */
    private fun decayReference(elapsedSeconds: Float) {
        referenceDb = max(MIN_REFERENCE_DB, referenceDb - referenceDecayDbPerSecond * elapsedSeconds.coerceIn(0f, 1f))
    }

    /** Forgets the loudness history (new track, seek). */
    fun reset() {
        referenceDb = INITIAL_REFERENCE_DB
    }

    /** RMS of a window in dBFS (for tests and diagnostics). */
    fun rmsDb(
        samples: FloatArray,
        count: Int,
    ): Float {
        var sum = 0.0
        for (i in 0 until count) sum += samples[i] * samples[i]
        return POWER_DB * log10(sum / count + TINY).toFloat()
    }

    companion object {
        /** 4096 points: about 11.7 Hz per bin at 48 kHz, fine enough to separate bass notes. */
        const val DEFAULT_FFT_SIZE = 4096
        private const val HANN_HALF = 0.5
        private const val POWER_DB = 10
        const val DEFAULT_MIN_HZ = 40f
        const val DEFAULT_MAX_HZ = 16_000f
        const val DEFAULT_DYNAMIC_RANGE_DB = 50f
        const val DEFAULT_SILENCE_DB = -70f
        const val DEFAULT_REFERENCE_DECAY = 6f
        private const val NYQUIST_MARGIN = 0.95f
        private const val INITIAL_REFERENCE_DB = -20f
        private const val MIN_REFERENCE_DB = -40f
        private const val MIN_DB = -200f
        private const val TINY = 1e-12
    }
}

/**
 * Time-based attack/release smoothing of displayed levels: rises quickly, falls slowly, the same at
 * any frame rate. Values stay within 0..1.
 */
class LevelSmoother(
    size: Int,
    var attackSeconds: Float = DEFAULT_ATTACK,
    var releaseSeconds: Float = DEFAULT_RELEASE,
) {
    val values = FloatArray(size)

    fun update(
        target: FloatArray,
        elapsedSeconds: Float,
    ) {
        val dt = elapsedSeconds.coerceIn(0f, MAX_STEP)
        val attack = 1f - exp(-dt / attackSeconds)
        val release = 1f - exp(-dt / releaseSeconds)
        for (i in values.indices) {
            val t = target[i].coerceIn(0f, 1f)
            val v = values[i]
            values[i] = (v + (t - v) * if (t > v) attack else release).coerceIn(0f, 1f)
        }
    }

    fun clear() = values.fill(0f)

    companion object {
        const val DEFAULT_ATTACK = 0.04f
        const val DEFAULT_RELEASE = 0.25f
        private const val MAX_STEP = 0.25f
    }
}

/** Peak-following gain for the oscilloscope: quiet music still shows a readable wave, bounded to -1..1. */
class WaveformGain(
    var decayPerSecond: Float = DEFAULT_DECAY,
) {
    private var peak = MIN_PEAK

    fun normalize(
        samples: FloatArray,
        count: Int,
        out: FloatArray,
        elapsedSeconds: Float,
    ) {
        var framePeak = 0f
        for (i in 0 until count) framePeak = max(framePeak, kotlin.math.abs(samples[i]))
        peak = max(max(framePeak, MIN_PEAK), peak * exp(-decayPerSecond * elapsedSeconds.coerceIn(0f, 1f)))
        val gain = 1f / peak
        for (i in 0 until count) out[i] = (samples[i] * gain).coerceIn(-1f, 1f)
    }

    fun reset() {
        peak = MIN_PEAK
    }

    companion object {
        const val DEFAULT_DECAY = 1.5f

        /** Below this the wave is drawn at its true (small) size: silence stays flat. */
        private const val MIN_PEAK = 0.05f
    }
}
