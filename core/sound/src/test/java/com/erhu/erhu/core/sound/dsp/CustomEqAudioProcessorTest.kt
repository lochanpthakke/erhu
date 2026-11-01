package com.erhu.erhu.core.sound.dsp

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class CustomEqAudioProcessorTest {
    private val sampleRate = 44_100
    private val stereo16 = AudioProcessor.AudioFormat(sampleRate, 2, C.ENCODING_PCM_16BIT)

    private fun configured(): CustomEqAudioProcessor =
        CustomEqAudioProcessor().apply {
            configure(stereo16)
            flush()
        }

    /** Interleaved stereo sine at [frequency] Hz, amplitude 0.25 (headroom for boosts). */
    private fun sine(
        frequency: Float,
        frames: Int = sampleRate / 2,
    ): ShortArray =
        ShortArray(frames * 2) { i ->
            val frame = i / 2
            (sin(2 * PI * frequency * frame / sampleRate) * 0.25 * Short.MAX_VALUE).toInt().toShort()
        }

    private fun process(
        processor: CustomEqAudioProcessor,
        samples: ShortArray,
    ): ShortArray {
        val input = ByteBuffer.allocateDirect(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        samples.forEach { input.putShort(it) }
        input.flip()
        processor.queueInput(input)
        val output = processor.output.order(ByteOrder.LITTLE_ENDIAN)
        return ShortArray(output.remaining() / 2) { output.short }
    }

    /** Peak absolute amplitude over the second half (after the filter settles). */
    private fun peak(samples: ShortArray): Int = samples.copyOfRange(samples.size / 2, samples.size).maxOf { abs(it.toInt()) }

    @Test
    fun flatGains_passAudioThroughUnchanged() {
        val input = sine(1000f)
        assertArrayEquals(input, process(configured(), input))
    }

    @Test
    fun boostingABand_increasesThatFrequency() {
        val processor = configured()
        processor.setBandGain(3, 12f) // 1 kHz band
        val input = sine(1000f)
        val out = process(processor, input)
        // +12 dB ~= x4; allow slack for filter shape.
        assertTrue("peak ${peak(out)} vs ${peak(input)}", peak(out) > peak(input) * 3)
    }

    @Test
    fun cuttingABand_reducesThatFrequency() {
        val processor = configured()
        processor.setBandGain(3, -12f)
        val input = sine(1000f)
        val out = process(processor, input)
        assertTrue("peak ${peak(out)} vs ${peak(input)}", peak(out) < peak(input) / 2)
    }

    @Test
    fun disabledEq_bypassesEvenWithGainsSet() {
        val processor = configured()
        processor.setBandGain(3, 12f)
        processor.setEqEnabled(false)
        val input = sine(1000f)
        assertArrayEquals(input, process(processor, input))
    }

    @Test
    fun presetGains_areClampedToSafeRange() {
        val processor = CustomEqAudioProcessor()
        processor.setBandGains(FloatArray(CustomEqAudioProcessor.BAND_COUNT) { 99f })
        processor.getBandGains().forEach { assertEquals(15f, it, 0f) }
    }

    @Test(expected = AudioProcessor.UnhandledAudioFormatException::class)
    fun nonPcm16Input_isRejectedSoExoPlayerBypassesTheProcessor() {
        CustomEqAudioProcessor().configure(AudioProcessor.AudioFormat(sampleRate, 2, C.ENCODING_PCM_FLOAT))
    }

    @Test
    fun monoAndMultichannel_areProcessedWithoutCrashing() {
        listOf(1, 6).forEach { channels ->
            val processor = CustomEqAudioProcessor()
            processor.configure(AudioProcessor.AudioFormat(sampleRate, channels, C.ENCODING_PCM_16BIT))
            processor.flush()
            processor.setBandGain(0, 6f)
            val input = ShortArray(channels * 1024) { (it % 200).toShort() }
            assertEquals(input.size, process(processor, input).size)
        }
    }
}
