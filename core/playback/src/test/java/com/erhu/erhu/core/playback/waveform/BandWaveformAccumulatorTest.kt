package com.erhu.erhu.core.playback.waveform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class BandWaveformAccumulatorTest {
    private val rate = 44_100

    /** One second of a sine of [hz] (float PCM, mono) fed in chunks, as the decoder does. */
    private fun feed(
        acc: BandWaveformAccumulator,
        hz: Double,
        startUs: Long = 0L,
        seconds: Int = 1,
    ) {
        val frames = rate * seconds
        val chunk = 4_096
        var done = 0
        while (done < frames) {
            val n = minOf(chunk, frames - done)
            val samples = FloatArray(n) { (0.5 * sin(2 * PI * hz * (done + it) / rate)).toFloat() }
            acc.addPcmFloat(samples, n, 1, rate, startUs + done * 1_000_000L / rate)
            done += n
        }
    }

    @Test
    fun `a low tone lands in the low band, a high tone in the high band`() {
        // A display approximation: each tone dominates its own band by at least 2x.
        val low = BandWaveformAccumulator(10, 1_000_000, 0).also { feed(it, 60.0) }.finish()!!
        assertTrue(low.low[5] > 2 * low.mid[5] && low.low[5] > 2 * low.high[5])
        val mid = BandWaveformAccumulator(10, 1_000_000, 0).also { feed(it, 1_000.0) }.finish()!!
        assertTrue(mid.mid[5] > 2 * mid.low[5] && mid.mid[5] > 2 * mid.high[5])
        val high = BandWaveformAccumulator(10, 1_000_000, 0).also { feed(it, 9_000.0) }.finish()!!
        assertTrue(high.high[5] > 2 * high.low[5] && high.high[5] > 2 * high.mid[5])
        // Absolute level: a 0.5-amplitude sine has an RMS of about 0.35, mostly in its own band.
        assertEquals(0.34f, low.low[5], 0.03f)
    }

    @Test
    fun `only the requested range is measured and empty ranges give nothing`() {
        // Audio from 0..1 s; range 0.5..1.0 s: all bins filled, nothing from before the range.
        val ranged = BandWaveformAccumulator(5, 500_000, 500_000).also { feed(it, 1_000.0) }.finish()!!
        assertEquals(5, ranged.size)
        assertTrue(ranged.mid.all { it > 0.1f })
        assertNull(BandWaveformAccumulator(5, 500_000, 5_000_000).also { feed(it, 1_000.0) }.finish())
    }
}
