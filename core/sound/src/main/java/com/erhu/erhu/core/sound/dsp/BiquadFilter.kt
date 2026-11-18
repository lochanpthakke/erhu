@file:Suppress("detekt:All")

package com.erhu.erhu.core.sound.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Saved by name: new types are only ever appended. */
enum class FilterType {
    PEAKING,
    LOW_SHELF,
    HIGH_SHELF,
    LOW_PASS,
    HIGH_PASS,
    NOTCH,
    ;

    /** Peak and shelves change the level by a gain; pass filters and the notch have none. */
    val hasGain: Boolean get() = this == PEAKING || this == LOW_SHELF || this == HIGH_SHELF

    val isPass: Boolean get() = this == LOW_PASS || this == HIGH_PASS
}

/**
 * Biquad IIR filter implementing Robert Bristow-Johnson's Audio EQ Cookbook.
 */
@Suppress("MagicNumber")
class BiquadFilter {
    private var b0 = 1.0f
    private var b1 = 0.0f
    private var b2 = 0.0f
    private var a1 = 0.0f
    private var a2 = 0.0f

    private var x1 = 0.0f
    private var x2 = 0.0f
    private var y1 = 0.0f
    private var y2 = 0.0f

    var frequency: Float = 1000.0f
        private set

    var gainDb: Float = 0.0f
        private set

    var qFactor: Float = 1.414f
        private set

    var filterType: FilterType = FilterType.PEAKING
        private set

    /** Passes audio unchanged (a 0 dB peak or shelf, or not configured). */
    var isIdentity: Boolean = true
        private set

    fun configure(
        centerFrequency: Float,
        sampleRate: Int,
        gainInDb: Float,
        q: Float = 1.414f,
        type: FilterType = FilterType.PEAKING,
    ) {
        frequency = centerFrequency
        gainDb = gainInDb
        qFactor = q
        filterType = type

        if (sampleRate <= 0 || (type.hasGain && gainInDb == 0.0f)) {
            setIdentity()
            return
        }
        isIdentity = false

        when (type) {
            FilterType.PEAKING -> configurePeakingEq(centerFrequency, sampleRate, gainInDb, q)
            FilterType.LOW_SHELF -> configureLowShelfEq(centerFrequency, sampleRate, gainInDb, q)
            FilterType.HIGH_SHELF -> configureHighShelfEq(centerFrequency, sampleRate, gainInDb, q)
            FilterType.LOW_PASS, FilterType.HIGH_PASS, FilterType.NOTCH -> configureCookbook(type, centerFrequency, sampleRate, q)
        }
    }

    fun setIdentity() {
        isIdentity = true
        b0 = 1.0f
        b1 = 0.0f
        b2 = 0.0f
        a1 = 0.0f
        a2 = 0.0f
    }

    /** RBJ cookbook low-pass, high-pass and notch (unity gain in the pass band). */
    private fun configureCookbook(
        type: FilterType,
        centerFrequency: Float,
        sampleRate: Int,
        q: Float,
    ) {
        val w0 = 2.0 * PI * centerFrequency / sampleRate
        val cosW0 = cos(w0)
        val alpha = sin(w0) / (2.0 * q.coerceAtLeast(0.1f))
        val (rawB0, rawB1, rawB2) =
            when (type) {
                FilterType.LOW_PASS -> Triple((1.0 - cosW0) / 2.0, 1.0 - cosW0, (1.0 - cosW0) / 2.0)
                FilterType.HIGH_PASS -> Triple((1.0 + cosW0) / 2.0, -(1.0 + cosW0), (1.0 + cosW0) / 2.0)
                else -> Triple(1.0, -2.0 * cosW0, 1.0)
            }
        val rawA0 = 1.0 + alpha
        b0 = (rawB0 / rawA0).toFloat()
        b1 = (rawB1 / rawA0).toFloat()
        b2 = (rawB2 / rawA0).toFloat()
        a1 = (-2.0 * cosW0 / rawA0).toFloat()
        a2 = ((1.0 - alpha) / rawA0).toFloat()
    }

    /** Copies another filter's history, so swapping coefficients does not restart the filter from silence. */
    fun copyStateFrom(other: BiquadFilter) {
        x1 = other.x1
        x2 = other.x2
        y1 = other.y1
        y2 = other.y2
    }

    fun configurePeakingEq(
        centerFrequency: Float,
        sampleRate: Int,
        gainInDb: Float,
        q: Float = 1.414f,
    ) {
        frequency = centerFrequency
        gainDb = gainInDb
        qFactor = q
        filterType = FilterType.PEAKING

        if (gainInDb == 0.0f || sampleRate <= 0) {
            setIdentity()
            return
        }
        isIdentity = false

        val a = 10.0.pow(gainInDb / 40.0)
        val w0 = 2.0 * PI * centerFrequency / sampleRate
        val alpha = sin(w0) / (2.0 * q.coerceAtLeast(0.1f))
        val cosW0 = cos(w0)

        val rawB0 = 1.0 + alpha * a
        val rawB1 = -2.0 * cosW0
        val rawB2 = 1.0 - alpha * a
        val rawA0 = 1.0 + alpha / a
        val rawA1 = -2.0 * cosW0
        val rawA2 = 1.0 - alpha / a

        b0 = (rawB0 / rawA0).toFloat()
        b1 = (rawB1 / rawA0).toFloat()
        b2 = (rawB2 / rawA0).toFloat()
        a1 = (rawA1 / rawA0).toFloat()
        a2 = (rawA2 / rawA0).toFloat()
    }

    private fun configureLowShelfEq(
        centerFrequency: Float,
        sampleRate: Int,
        gainInDb: Float,
        q: Float = 1.414f,
    ) {
        val a = 10.0.pow(gainInDb / 40.0)
        val w0 = 2.0 * PI * centerFrequency / sampleRate
        val cosW0 = cos(w0)
        val insideSqrt = ((a + 1.0 / a) * (1.0 / q.coerceAtLeast(0.1f) - 1.0) + 2.0).coerceAtLeast(0.0)
        val alpha = sin(w0) / 2.0 * sqrt(insideSqrt)
        val beta = 2.0 * sqrt(a.coerceAtLeast(0.0001)) * alpha

        val rawB0 = a * ((a + 1.0) - (a - 1.0) * cosW0 + beta)
        val rawB1 = 2.0 * a * ((a - 1.0) - (a + 1.0) * cosW0)
        val rawB2 = a * ((a + 1.0) - (a - 1.0) * cosW0 - beta)
        val rawA0 = (a + 1.0) + (a - 1.0) * cosW0 + beta
        val rawA1 = -2.0 * ((a - 1.0) + (a + 1.0) * cosW0)
        val rawA2 = (a + 1.0) + (a - 1.0) * cosW0 - beta

        b0 = (rawB0 / rawA0).toFloat()
        b1 = (rawB1 / rawA0).toFloat()
        b2 = (rawB2 / rawA0).toFloat()
        a1 = (rawA1 / rawA0).toFloat()
        a2 = (rawA2 / rawA0).toFloat()
    }

    private fun configureHighShelfEq(
        centerFrequency: Float,
        sampleRate: Int,
        gainInDb: Float,
        q: Float = 1.414f,
    ) {
        val a = 10.0.pow(gainInDb / 40.0)
        val w0 = 2.0 * PI * centerFrequency / sampleRate
        val cosW0 = cos(w0)
        val insideSqrt = ((a + 1.0 / a) * (1.0 / q.coerceAtLeast(0.1f) - 1.0) + 2.0).coerceAtLeast(0.0)
        val alpha = sin(w0) / 2.0 * sqrt(insideSqrt)
        val beta = 2.0 * sqrt(a.coerceAtLeast(0.0001)) * alpha

        val rawB0 = a * ((a + 1.0) + (a - 1.0) * cosW0 + beta)
        val rawB1 = -2.0 * a * ((a - 1.0) + (a + 1.0) * cosW0)
        val rawB2 = a * ((a + 1.0) + (a - 1.0) * cosW0 - beta)
        val rawA0 = (a + 1.0) - (a - 1.0) * cosW0 + beta
        val rawA1 = 2.0 * ((a - 1.0) - (a + 1.0) * cosW0)
        val rawA2 = (a + 1.0) - (a - 1.0) * cosW0 - beta

        b0 = (rawB0 / rawA0).toFloat()
        b1 = (rawB1 / rawA0).toFloat()
        b2 = (rawB2 / rawA0).toFloat()
        a1 = (rawA1 / rawA0).toFloat()
        a2 = (rawA2 / rawA0).toFloat()
    }

    fun processSample(x: Float): Float {
        val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        if (y.isNaN() || y.isInfinite()) {
            resetState()
            return x
        }
        x2 = x1
        x1 = x
        y2 = y1
        y1 = y
        return y
    }

    fun resetState() {
        x1 = 0.0f
        x2 = 0.0f
        y1 = 0.0f
        y2 = 0.0f
    }

    fun getFrequencyResponse(
        targetFrequency: Float,
        sampleRate: Int,
    ): Float {
        if (isIdentity || sampleRate <= 0) return 0.0f

        val w = 2.0 * PI * targetFrequency / sampleRate
        val cosW = cos(w)
        val cos2W = cos(2.0 * w)
        val sinW = sin(w)
        val sin2W = sin(2.0 * w)

        val numReal = b0 + b1 * cosW + b2 * cos2W
        val numImag = -b1 * sinW - b2 * sin2W
        val denReal = 1.0 + a1 * cosW + a2 * cos2W
        val denImag = -a1 * sinW - a2 * sin2W

        val numMagSq = numReal * numReal + numImag * numImag
        val denMagSq = denReal * denReal + denImag * denImag

        if (denMagSq == 0.0) return 0.0f

        // A notch reaches zero at its centre: floor it at -120 dB instead of minus infinity.
        val magRatio = (numMagSq / denMagSq).coerceAtLeast(1e-12)
        return (10.0 * log10(magRatio)).toFloat()
    }
}
