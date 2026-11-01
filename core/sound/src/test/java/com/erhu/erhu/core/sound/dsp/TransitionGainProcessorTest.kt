package com.erhu.erhu.core.sound.dsp

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.erhu.erhu.core.model.TransitionCurve
import com.erhu.erhu.core.model.TransitionFilter
import com.erhu.erhu.core.model.TransitionPreset
import com.erhu.erhu.core.model.TransitionRole
import com.erhu.erhu.core.model.TransitionSpec
import com.erhu.erhu.core.model.VolumeCurve
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

class TransitionGainProcessorTest {
    private val linear = TransitionSpec(durationMs = 100, curve = TransitionCurve.LINEAR)

    @Test
    fun `automations follow the spec, ramp in, lead in and hold their end levels`() {
        val inc = TransitionAutomation(TransitionRole.INCOMING, linear, 1_000)
        assertEquals(0.25f, inc.gainAt(250.0), 1e-6f)
        assertTrue(inc.isFinished(1_000.0))
        assertFalse(inc.isFinished(999.0))
        val out = TransitionAutomation(TransitionRole.OUTGOING, linear, 1_000, entryRampMs = 40)
        assertEquals(0f, out.gainAt(0.0), 1e-6f)
        assertEquals(0.5f * (1f - 0.02f), out.gainAt(20.0), 1e-6f)
        assertEquals(0f, out.gainAt(5_000.0), 1e-6f)
        assertFalse(out.isFinished(5_000.0))
        // Preview lead-in: the outgoing track plays at full level, the incoming one is silent, until the transition.
        val leadOut = TransitionAutomation(TransitionRole.OUTGOING, linear, 1_000, leadInMs = 3_000)
        assertEquals(1f, leadOut.gainAt(1_000.0), 0f)
        assertEquals(0.5f, leadOut.gainAt(3_500.0), 1e-6f)
        assertEquals(0f, TransitionAutomation(TransitionRole.INCOMING, linear, 1_000, leadInMs = 3_000).gainAt(1_000.0), 0f)
    }

    private val rate = 1_000 // 1 frame per ms keeps the arithmetic readable

    private fun processor(
        encoding: Int = C.ENCODING_PCM_16BIT,
        cancelOnLaterFlush: Boolean = true,
        sampleRate: Int = rate,
    ) = TransitionGainProcessor(cancelOnLaterFlush = cancelOnLaterFlush).apply {
        configure(AudioProcessor.AudioFormat(sampleRate, 1, encoding))
        flush()
    }

    private fun TransitionGainProcessor.run16(vararg samples: Short): ShortArray {
        val input = ByteBuffer.allocateDirect(samples.size * 2).order(ByteOrder.nativeOrder())
        samples.forEach { input.putShort(it) }
        input.flip()
        queueInput(input)
        val out = output.order(ByteOrder.nativeOrder())
        return ShortArray(out.remaining() / 2) { out.short }
    }

    private fun constant(frames: Int) = ShortArray(frames) { 10_000 }

    @Test
    fun `idle is unity gain, an armed automation starts at the next flush and ends at full level`() {
        val p = processor()
        assertEquals(10_000.toShort(), p.run16(*constant(4))[3])
        p.arm(TransitionAutomation(TransitionRole.INCOMING, linear, 100))
        assertEquals(10_000.toShort(), p.run16(*constant(4))[0]) // not before its flush
        assertFalse(p.hasStarted)
        p.flush() // the seek to the next track
        assertTrue(p.hasStarted)
        val faded = p.run16(*constant(200))
        assertEquals(0, faded[0].toInt())
        assertEquals(5_000, faded[50].toInt())
        assertEquals(10_000, faded[150].toInt())
        assertFalse(p.isEngaged)
    }

    @Test
    fun `a custom curve is executed point by point`() {
        val custom =
            TransitionSpec(
                durationMs = 100,
                preset = TransitionPreset.CUSTOM,
                curve = TransitionCurve.CUSTOM,
                outgoingCurve = VolumeCurve.of(0f to 1f, 0.5f to 0.2f, 1f to 0f),
                incomingCurve = VolumeCurve.of(0f to 0.5f, 0.25f to 0.5f, 1f to 1f),
            )
        val p = processor()
        p.arm(TransitionAutomation(TransitionRole.INCOMING, custom, 100))
        p.flush()
        val out = p.run16(*constant(100))
        assertEquals(5_000, out[10].toInt()) // flat at 0.5 until 25 %
        assertEquals(7_467f, out[62].toFloat(), 2f) // 0.5 + 0.5 * (0.62 - 0.25) / 0.75
        val q = processor(cancelOnLaterFlush = false)
        q.arm(TransitionAutomation(TransitionRole.OUTGOING, custom, 100))
        q.flush()
        assertEquals(2_000, q.run16(*constant(100))[50].toInt())
    }

    @Test
    fun `a later flush (user skip) cancels, one right at the start restarts`() {
        val p = processor()
        p.arm(TransitionAutomation(TransitionRole.INCOMING, linear.copy(durationMs = 1_000), 1_000))
        p.flush()
        p.run16(*constant(30))
        p.flush()
        assertEquals(0, p.run16(*constant(1))[0].toInt())
        p.run16(*constant(200))
        p.flush()
        assertEquals(10_000, p.run16(*constant(1))[0].toInt())
        assertFalse(p.isEngaged)
    }

    @Test
    fun `the tail ends in silence and survives later flushes`() {
        val p = processor(cancelOnLaterFlush = false)
        p.arm(TransitionAutomation(TransitionRole.OUTGOING, linear.copy(curve = TransitionCurve.EQUAL_POWER), 100, entryRampMs = 10))
        p.flush()
        val out = p.run16(*constant(300))
        assertEquals(0, out[0].toInt())
        assertEquals(7_071f, out[50].toFloat(), 2f)
        assertEquals(0, out[299].toInt())
        assertTrue(p.isEngaged)
    }

    /** RMS of a sine of [hz] after the processor, over [fromMs, toMs) of a 48 kHz stream. */
    private fun sineRms(
        p: TransitionGainProcessor,
        hz: Double,
        totalMs: Int,
        fromMs: Int,
        toMs: Int,
    ): Double {
        val sr = 48_000
        val frames = sr * totalMs / 1_000
        val input = ByteBuffer.allocateDirect(frames * 4).order(ByteOrder.nativeOrder())
        repeat(frames) { input.putFloat((0.5 * sin(2 * PI * hz * it / sr)).toFloat()) }
        input.flip()
        p.queueInput(input)
        val out = p.output.order(ByteOrder.nativeOrder())
        var sum = 0.0
        var n = 0
        repeat(frames) { i ->
            val v = out.float
            if (i >= sr * fromMs / 1_000 && i < sr * toMs / 1_000) {
                sum += v * v
                n++
            }
        }
        return sqrt(sum / n)
    }

    @Test
    fun `bass swap really removes the incoming track's bass until the swap, highs untouched`() {
        val swap = TransitionSpec(durationMs = 1_000, curve = TransitionCurve.LINEAR, bassSwap = true)
        fun incoming(): TransitionGainProcessor =
            processor(encoding = C.ENCODING_PCM_FLOAT, sampleRate = 48_000).apply {
                arm(TransitionAutomation(TransitionRole.INCOMING, swap, 1_000))
                flush()
            }
        // Same window (200–300 ms, gain 0.2–0.3) for both tones; only the bass is cut before the swap.
        val bassBefore = sineRms(incoming(), 60.0, 1_000, 200, 300)
        val trebleBefore = sineRms(incoming(), 4_000.0, 1_000, 200, 300)
        assertTrue("bass $bassBefore vs treble $trebleBefore", bassBefore < trebleBefore * 0.25)
        // After the swap point the bass is back: same window gain, same level for both tones.
        val bassAfter = sineRms(incoming(), 60.0, 1_000, 700, 800)
        val trebleAfter = sineRms(incoming(), 4_000.0, 1_000, 700, 800)
        assertEquals(trebleAfter, bassAfter, trebleAfter * 0.1)
    }

    @Test
    fun `other encodings pass through untouched`() {
        val pcm24 = TransitionGainProcessor()
        assertEquals(AudioProcessor.AudioFormat.NOT_SET, pcm24.configure(AudioProcessor.AudioFormat(rate, 2, C.ENCODING_PCM_24BIT)))
        assertFalse(pcm24.isActive)
    }

    private fun swept(
        role: TransitionRole,
        filter: TransitionFilter,
        hz: Double,
        fromMs: Int,
        toMs: Int,
    ): Double {
        // Flat volume (Overlap-like) isolates the filter: compare against the same tone with no filter.
        val spec = TransitionSpec(durationMs = 1_000, curve = TransitionCurve.LINEAR, filter = filter)
        val p = processor(encoding = C.ENCODING_PCM_FLOAT, sampleRate = 48_000, cancelOnLaterFlush = role == TransitionRole.INCOMING)
        p.arm(TransitionAutomation(role, spec, 1_000))
        p.flush()
        return sineRms(p, hz, 1_000, fromMs, toMs)
    }

    @Test
    fun `high-pass in thins the incoming track at first and opens it by the middle`() {
        val none = TransitionFilter.NONE
        val hpf = TransitionFilter.HIGH_PASS_IN
        val bassEarly = swept(TransitionRole.INCOMING, hpf, 80.0, 50, 150) / swept(TransitionRole.INCOMING, none, 80.0, 50, 150)
        val bassLate = swept(TransitionRole.INCOMING, hpf, 80.0, 700, 800) / swept(TransitionRole.INCOMING, none, 80.0, 700, 800)
        val trebleEarly = swept(TransitionRole.INCOMING, hpf, 8_000.0, 50, 150) / swept(TransitionRole.INCOMING, none, 8_000.0, 50, 150)
        assertTrue("bass early $bassEarly", bassEarly < 0.1)
        assertEquals(1.0, bassLate, 0.05)
        assertEquals(1.0, trebleEarly, 0.05)
    }

    @Test
    fun `low-pass out darkens only the second half of the outgoing track`() {
        val none = TransitionFilter.NONE
        val lpf = TransitionFilter.LOW_PASS_OUT
        val trebleEarly = swept(TransitionRole.OUTGOING, lpf, 6_000.0, 100, 300) / swept(TransitionRole.OUTGOING, none, 6_000.0, 100, 300)
        val trebleLate = swept(TransitionRole.OUTGOING, lpf, 6_000.0, 900, 980) / swept(TransitionRole.OUTGOING, none, 6_000.0, 900, 980)
        val bassLate = swept(TransitionRole.OUTGOING, lpf, 100.0, 900, 980) / swept(TransitionRole.OUTGOING, none, 100.0, 900, 980)
        assertEquals(1.0, trebleEarly, 0.05)
        assertTrue("treble late $trebleLate", trebleLate < 0.1)
        assertTrue("bass late $bassLate", bassLate > 0.8)
    }
}
