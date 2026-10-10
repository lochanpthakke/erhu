package com.lochanpthakke.empee3.core.playback.waveform

import com.lochanpthakke.empee3.core.database.repository.CachedWaveform
import com.lochanpthakke.empee3.core.model.Waveform
import com.lochanpthakke.empee3.core.model.WaveformTimeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class WaveformTest {
    private val sampleRate = 8_000

    /** Interleaved stereo 16-bit: a sine whose amplitude is [amplitude] (0..1) in both channels. */
    private fun stereoSine(
        frames: Int,
        amplitude: Double,
    ): ShortArray =
        ShortArray(frames * 2) { i ->
            (sin(2 * PI * 440 * (i / 2) / sampleRate) * amplitude * Short.MAX_VALUE).toInt().toShort()
        }

    @Test
    fun `levels are bounded and follow the loudness of the audio`() {
        // 2 s of audio: the first second quiet, the second loud; 4 bins.
        val accumulator = WaveformAccumulator(bins = 4, durationUs = 2_000_000)
        accumulator.addPcm16(stereoSine(sampleRate, 0.1), sampleRate * 2, 2, sampleRate, ptsUs = 0)
        accumulator.addPcm16(stereoSine(sampleRate, 0.8), sampleRate * 2, 2, sampleRate, ptsUs = 1_000_000)
        val levels = accumulator.finish()!!

        assertEquals(4, levels.size)
        assertTrue(levels.all { it in 0f..1f && it.isFinite() })
        assertEquals(1f, levels.max(), 1e-6f)
        assertTrue(levels[0] < 0.3f && levels[1] < 0.3f)
        assertTrue(levels[2] > 0.9f && levels[3] > 0.9f)
    }

    @Test
    fun `channels are downmixed so opposite channels cancel, and float input is accepted`() {
        val accumulator = WaveformAccumulator(bins = 2, durationUs = 1_000_000)
        val frames = sampleRate / 2
        // First half: left = -right (silence when mixed). Second half: a real signal.
        val cancelling = FloatArray(frames * 2) { if (it % 2 == 0) 0.5f else -0.5f }
        val signal = FloatArray(frames * 2) { 0.5f }
        accumulator.addPcmFloat(cancelling, cancelling.size, 2, sampleRate, 0)
        accumulator.addPcmFloat(signal, signal.size, 2, sampleRate, 500_000)
        val levels = accumulator.finish()!!
        assertEquals(0f, levels[0], 1e-6f)
        assertEquals(1f, levels[1], 1e-6f)
    }

    @Test
    fun `empty audio gives no waveform and silence gives a flat one`() {
        assertNull(WaveformAccumulator(bins = 8, durationUs = 1_000_000).finish())

        val silent = WaveformAccumulator(bins = 8, durationUs = 1_000_000)
        silent.addPcm16(ShortArray(2_000), 2_000, 2, sampleRate, 0)
        val levels = silent.finish()!!
        assertTrue(levels.all { it == 0f })
    }

    @Test
    fun `samples past the stated duration land in the last bin and bad formats are ignored`() {
        val accumulator = WaveformAccumulator(bins = 4, durationUs = 1_000_000)
        accumulator.addPcm16(stereoSine(100, 0.5), 200, 2, sampleRate, ptsUs = 5_000_000)
        accumulator.addPcm16(stereoSine(100, 0.5), 200, 0, sampleRate, ptsUs = 0) // no channels: ignored
        val levels = accumulator.finish()!!
        assertEquals(1f, levels[3], 1e-6f)
        assertEquals(0f, levels[0], 1e-6f)
    }

    @Test
    fun `levels survive the compact byte form`() {
        val levels = floatArrayOf(0f, 0.25f, 0.5f, 1f, 1.7f, -1f)
        val back = levels.toLevelBytes().toLevels()
        assertEquals(0f, back[0], 0f)
        assertEquals(0.25f, back[1], 1f / 255)
        assertEquals(1f, back[3], 0f)
        assertEquals(1f, back[4], 0f) // clamped
        assertEquals(0f, back[5], 0f)
    }

    private val source = SourceFingerprint("content://media/external/audio/media/7", sizeBytes = 4_000_000, dateModified = 1_700_000_000)

    private fun cached(
        source: SourceFingerprint = this.source,
        version: Int = WAVEFORM_FORMAT_VERSION,
        levels: ByteArray = ByteArray(WAVEFORM_BINS) { 7 },
    ) = CachedWaveform(7, source.uri, source.sizeBytes, source.dateModified, version, levels)

    @Test
    fun `a cache is used only while the file is unchanged`() {
        assertNotNull(cached().toWaveformIfValid(source))
        // The file was re-encoded, edited (new size) or retagged (new modification time): regenerate.
        assertNull(cached().toWaveformIfValid(source.copy(sizeBytes = 4_000_001)))
        assertNull(cached().toWaveformIfValid(source.copy(dateModified = 1_700_000_001)))
        assertNull(cached().toWaveformIfValid(source.copy(uri = "content://media/external/audio/media/8")))
    }

    @Test
    fun `damaged or outdated cache data is rejected`() {
        assertNull(cached(levels = ByteArray(3)).toWaveformIfValid(source))
        assertNull(cached(levels = ByteArray(0)).toWaveformIfValid(source))
        assertNull(cached(version = WAVEFORM_FORMAT_VERSION + 1).toWaveformIfValid(source))
    }

    @Test
    fun `a waveform round-trips through the cache`() {
        val waveform = Waveform(FloatArray(WAVEFORM_BINS) { it / (WAVEFORM_BINS - 1f) })
        val restored = waveform.toCache(7, source).toWaveformIfValid(source)!!
        assertEquals(WAVEFORM_BINS, restored.size)
        restored.levels.forEachIndexed { i, level -> assertEquals(waveform.levels[i], level, 1f / 255) }
    }

    @Test
    fun `timeline maps both ends exactly`() {
        val duration = 200_000L
        assertEquals(0f, WaveformTimeline.fractionOf(0, duration), 0f)
        assertEquals(1f, WaveformTimeline.fractionOf(duration, duration), 0f)
        assertEquals(1f, WaveformTimeline.fractionOf(duration + 5_000, duration), 0f)
        assertEquals(0f, WaveformTimeline.fractionOf(-10, duration), 0f)
        assertEquals(0f, WaveformTimeline.fractionOf(1_000, 0), 0f)

        assertEquals(0L, WaveformTimeline.positionAtX(0f, 300f, duration))
        assertEquals(duration, WaveformTimeline.positionAtX(300f, 300f, duration))
        assertEquals(duration, WaveformTimeline.positionAtX(450f, 300f, duration))
        assertEquals(0L, WaveformTimeline.positionAtX(-20f, 300f, duration))
        assertEquals(100_000L, WaveformTimeline.positionAtX(150f, 300f, duration))
        assertEquals(0L, WaveformTimeline.positionAtX(100f, 0f, duration))
        // Round trip at a few points.
        listOf(0L, 1L, 61_234L, duration).forEach {
            assertEquals(it, WaveformTimeline.positionAt(WaveformTimeline.fractionOf(it, duration), duration))
        }
    }
}
