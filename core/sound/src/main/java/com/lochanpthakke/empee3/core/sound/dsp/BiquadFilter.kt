@file:Suppress("detekt:All")

package com.lochanpthakke.empee3.core.sound.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin

/**
 * Biquad IIR filter implementing Robert Bristow-Johnson's Audio EQ Cookbook Peaking EQ.
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

    fun configurePeakingEq(
        centerFrequency: Float,
        sampleRate: Int,
        gainInDb: Float,
        q: Float = 1.414f,
    ) {
        frequency = centerFrequency
        gainDb = gainInDb

        if (gainInDb == 0.0f || sampleRate <= 0) {
            b0 = 1.0f
            b1 = 0.0f
            b2 = 0.0f
            a1 = 0.0f
            a2 = 0.0f
            return
        }

        val a = 10.0.pow(gainInDb / 40.0)
        val w0 = 2.0 * PI * centerFrequency / sampleRate
        val alpha = sin(w0) / (2.0 * q)
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

    fun processSample(x: Float): Float {
        val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
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
        if (gainDb == 0.0f || sampleRate <= 0) return 0.0f

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

        val magRatio = numMagSq / denMagSq
        return (10.0 * log10(magRatio)).toFloat()
    }
}
