package com.erhu.erhu.core.sound.dsp

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin

/** Each filter type's response against its textbook value, and the processor's real output. */
class ParametricEqDspTest {
    private val rate = 48_000

    private fun db(
        spec: BandSpec,
        hz: Float,
    ) = combinedResponseDb(listOf(spec), floatArrayOf(hz), rate)[0]

    @Test
    fun peak_reachesItsGainAtTheCentre() {
        assertEquals(6f, db(BandSpec(FilterType.PEAKING, 1000f, 6f, 1f), 1000f), 0.05f)
        assertEquals(0f, db(BandSpec(FilterType.PEAKING, 1000f, 6f, 1f), 50f), 0.3f)
    }

    @Test
    fun shelves_liftTheirSideOnly() {
        assertEquals(6f, db(BandSpec(FilterType.LOW_SHELF, 200f, 6f, 0.7f), 20f), 0.5f)
        assertEquals(0f, db(BandSpec(FilterType.LOW_SHELF, 200f, 6f, 0.7f), 10_000f), 0.3f)
        assertEquals(-6f, db(BandSpec(FilterType.HIGH_SHELF, 5000f, -6f, 0.7f), 19_000f), 0.6f)
    }

    @Test
    fun lowPass_slopesMatchButterworth() {
        // -3 dB at the cutoff; one octave above, -10*log10(1 + 2^(2n)) for order n.
        listOf(12 to 12.3f, 24 to 24.1f, 48 to 48.2f).forEach { (slope, expected) ->
            val spec = BandSpec(FilterType.LOW_PASS, 1000f, 0f, 0.707f, slope)
            assertEquals("cutoff $slope", -3f, db(spec, 1000f), 0.4f)
            assertEquals("octave $slope", -expected, db(spec, 2000f), 1.0f)
            assertEquals("pass band $slope", 0f, db(spec, 100f), 0.2f)
        }
    }

    @Test
    fun highPass_mirrorsTheLowPass() {
        val spec = BandSpec(FilterType.HIGH_PASS, 1000f, 0f, 0.707f, 24)
        assertEquals(-3f, db(spec, 1000f), 0.4f)
        assertEquals(-24.1f, db(spec, 500f), 1.0f)
        assertEquals(0f, db(spec, 10_000f), 0.2f)
    }

    @Test
    fun notch_removesItsCentreOnly() {
        val spec = BandSpec(FilterType.NOTCH, 1000f, 0f, 4f)
        assertTrue(db(spec, 1000f) < -40f)
        assertEquals(0f, db(spec, 100f), 0.2f)
        assertEquals(0f, db(spec, 10_000f), 0.2f)
    }

    @Test
    fun disabledAndFlatBands_doNothing() {
        assertEquals(0f, db(BandSpec(FilterType.PEAKING, 1000f, 9f, 1f, enabled = false), 1000f), 0f)
        assertEquals(0f, db(BandSpec(FilterType.LOW_PASS, 200f, 0f, 0.7f, enabled = false), 5000f), 0f)
        assertEquals(0f, db(BandSpec(FilterType.PEAKING, 1000f, 0f, 1f), 1000f), 0f)
    }

    /** Measured output level of a [hz] sine through [processor], in dB relative to the input. */
    private fun measuredDb(
        processor: CustomEqAudioProcessor,
        hz: Float,
        amplitude: Float = 0.1f,
    ): Float {
        val frames = rate / 2
        val input = ByteBuffer.allocateDirect(frames * 2).order(ByteOrder.LITTLE_ENDIAN)
        repeat(frames) { input.putShort((sin(2 * PI * hz * it / rate) * amplitude * Short.MAX_VALUE).toInt().toShort()) }
        input.flip()
        processor.queueInput(input)
        val out = processor.output.order(ByteOrder.LITTLE_ENDIAN)
        val samples = ShortArray(out.remaining() / 2) { out.short }
        val peak = samples.copyOfRange(samples.size / 2, samples.size).maxOf { abs(it.toInt()) }
        return (20 * log10(peak / (amplitude * Short.MAX_VALUE))).toFloat()
    }

    private fun processor(vararg bands: BandSpec) =
        CustomEqAudioProcessor().apply {
            configure(AudioProcessor.AudioFormat(rate, 1, C.ENCODING_PCM_16BIT))
            flush()
            configureBands(bands.toList())
        }

    @Test
    fun audioOutput_followsTheDrawnResponse() {
        val bands = arrayOf(BandSpec(FilterType.PEAKING, 1000f, 6f, 1f), BandSpec(FilterType.HIGH_PASS, 200f, 0f, 0.707f, 24))
        listOf(1000f, 100f, 4000f).forEach { hz ->
            val drawn = combinedResponseDb(bands.toList(), floatArrayOf(hz), rate)[0]
            assertEquals("at $hz Hz", drawn, measuredDb(processor(*bands), hz), 0.6f)
        }
    }

    @Test
    fun fullBoostOfALoudSignal_neverClips() {
        val p = processor(BandSpec(FilterType.PEAKING, 1000f, 12f, 1f))
        val level = measuredDb(p, 1000f, amplitude = 0.9f)
        // +12 dB of a 0.9 full-scale sine would clip hard; the limiter holds it at 0.97 of full scale
        // (+0.65 dB relative to the 0.9 input), never reaching full scale (+0.92 dB).
        assertTrue("level $level", level > 0.3f && level < 0.8f)
    }

    @Test
    fun aParameterChange_fadesInWithoutAJump() {
        val p = processor(BandSpec(FilterType.PEAKING, 1000f, 0f, 1f))
        measuredDb(p, 200f)
        p.configureBands(listOf(BandSpec(FilterType.LOW_PASS, 300f, 0f, 0.707f, 48)))
        val frames = 4096
        val input = ByteBuffer.allocateDirect(frames * 2).order(ByteOrder.LITTLE_ENDIAN)
        repeat(frames) { input.putShort((sin(2 * PI * 200 * it / rate) * 0.3 * Short.MAX_VALUE).toInt().toShort()) }
        input.flip()
        p.queueInput(input)
        val out = p.output.order(ByteOrder.LITTLE_ENDIAN)
        val samples = ShortArray(out.remaining() / 2) { out.short }
        val biggestStep = (1 until samples.size).maxOf { abs(samples[it] - samples[it - 1]) }
        // A 200 Hz sine at 0.3 full scale moves at most ~260 per sample; a click would jump far more.
        assertTrue("step $biggestStep", biggestStep < 600)
    }
}
