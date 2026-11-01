package com.erhu.erhu.core.sound.dsp

import com.erhu.erhu.core.sound.model.BandConfig
import com.erhu.erhu.core.sound.model.SoundSettings
import com.erhu.erhu.core.sound.visualizer.PlaybackAudioTap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DspDiagnosticsTest {
    private fun runtime(
        enabled: Boolean = true,
        configured: Boolean = true,
        flat: Boolean = false,
        rate: Int? = 48_000,
    ) = EqRuntimeState(enabled, configured, rate, 2, flat)

    private fun settings(vararg gains: Float) =
        SoundSettings(
            bands = SoundSettings.defaultBands().mapIndexed { i, band -> band.copy(gainDb = gains.getOrElse(i) { 0f }) },
        )

    @Test
    fun `the stage reflects the processor, not just the stored setting`() {
        val boosted = settings(6f)
        assertEquals(StageStatus.ACTIVE, DspDiagnostics.eqStage(runtime(), boosted).status)
        assertEquals(StageStatus.OFF, DspDiagnostics.eqStage(runtime(enabled = false), boosted).status)
        // Enabled in settings, but the player has not configured it for this audio: not claimed as active.
        assertEquals(StageStatus.IDLE, DspDiagnostics.eqStage(runtime(configured = false), boosted).status)
        assertEquals(StageStatus.BYPASSED, DspDiagnostics.eqStage(runtime(flat = true), settings()).status)
    }

    @Test
    fun `bands the processor skips at this sample rate are marked`() {
        val high =
            SoundSettings(bands = SoundSettings.defaultBands().toMutableList().also { it[7] = BandConfig(16_000f, 6f) })
        val at32k = DspDiagnostics.eqStage(runtime(rate = 32_000), high)
        assertTrue(at32k.activeBands[7].skippedAtThisSampleRate) // 16 kHz >= 0.45 * 32 kHz
        assertTrue(!DspDiagnostics.eqStage(runtime(rate = 48_000), high).activeBands[7].skippedAtThisSampleRate)
    }

    @Test
    fun `headroom estimate follows the curve`() {
        val boost = DspDiagnostics.eqStage(runtime(), settings(0f, 0f, 0f, 6f))
        val estimate = DspDiagnostics.headroom(boost, 48_000)
        assertEquals(6f, estimate.maxBoostDb, 0.3f)
        assertEquals(1_000f, estimate.atFrequencyHz!!, 150f)
        assertTrue(estimate.risk == ClippingRisk.MODERATE || estimate.risk == ClippingRisk.HIGH)
        assertTrue(estimate.advice.contains("no preamp"))

        val cutOnly = DspDiagnostics.headroom(DspDiagnostics.eqStage(runtime(), settings(-6f, -3f)), 48_000)
        assertEquals(ClippingRisk.NONE, cutOnly.risk)
        assertNull(cutOnly.atFrequencyHz)

        val off = DspDiagnostics.headroom(DspDiagnostics.eqStage(runtime(enabled = false), settings(12f)), 48_000)
        assertEquals(ClippingRisk.NONE, off.risk)
        assertEquals(0f, off.maxBoostDb, 0f)

        val big = DspDiagnostics.headroom(DspDiagnostics.eqStage(runtime(), settings(12f, 12f)), 48_000)
        assertEquals(ClippingRisk.HIGH, big.risk)
    }

    @Test
    fun `the peak meter counts full-scale samples after the eq and resets`() {
        val tap = PlaybackAudioTap()
        tap.onFormat(48_000, 2, pcm16 = true, pcmFloat = false)
        val buffer = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder())
        listOf<Short>(16_384, -8_192, 32_767, -32_768, 100, 100, 0, 0).forEach { buffer.putShort(it) }
        buffer.flip()
        tap.write(buffer)
        assertEquals(0L, tap.meterReading().frames) // not measured while nobody reads

        tap.acquire()
        tap.write(buffer)
        val reading = tap.meterReading()
        assertEquals(4L, reading.frames)
        assertEquals(2L, reading.fullScaleSamples)
        assertEquals(0f, reading.peakDbfs!!, 0.01f)
        tap.resetMeter()
        assertNull(tap.meterReading().peakDbfs)
        tap.release()
    }
}
