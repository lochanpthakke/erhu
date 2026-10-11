package com.lochanpthakke.empee3.core.sound.dsp

import com.lochanpthakke.empee3.core.model.PassType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A second-order Butterworth high- or low-pass (Audio EQ Cookbook, Q = 1/√2: no resonant peak), for
 * filter sweeps. [configure] may be called while audio runs: the filter state is kept, so a cutoff
 * that moves in small steps sweeps smoothly.
 */
class PassFilter {
    private var b0 = 1f
    private var b1 = 0f
    private var b2 = 0f
    private var a1 = 0f
    private var a2 = 0f
    private var x1 = 0f
    private var x2 = 0f
    private var y1 = 0f
    private var y2 = 0f

    fun configure(
        type: PassType,
        cutoffHz: Float,
        sampleRate: Int,
    ) {
        val nyquistSafe = sampleRate * MAX_CUTOFF_FRACTION
        val w0 = TWO * PI * cutoffHz.coerceIn(MIN_CUTOFF_HZ, nyquistSafe) / sampleRate
        val cosW = cos(w0)
        val alpha = sin(w0) / (TWO * BUTTERWORTH_Q)
        val a0 = 1.0 + alpha
        val (nb0, nb1) =
            when (type) {
                PassType.LOW_PASS -> (1.0 - cosW) / TWO to (1.0 - cosW)
                PassType.HIGH_PASS -> (1.0 + cosW) / TWO to -(1.0 + cosW)
            }
        b0 = (nb0 / a0).toFloat()
        b1 = (nb1 / a0).toFloat()
        b2 = b0
        a1 = (-TWO * cosW / a0).toFloat()
        a2 = ((1.0 - alpha) / a0).toFloat()
    }

    fun process(x: Float): Float {
        val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        if (!y.isFinite()) {
            reset()
            return x
        }
        x2 = x1
        x1 = x
        y2 = y1
        y1 = y
        return y
    }

    fun reset() {
        x1 = 0f
        x2 = 0f
        y1 = 0f
        y2 = 0f
    }

    private companion object {
        const val BUTTERWORTH_Q = 0.7071067811865476
        const val TWO = 2.0
        const val MIN_CUTOFF_HZ = 10f
        const val MAX_CUTOFF_FRACTION = 0.45f
    }
}
