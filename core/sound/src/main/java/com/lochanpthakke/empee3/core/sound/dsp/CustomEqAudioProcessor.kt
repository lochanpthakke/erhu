@file:Suppress("detekt:All")

package com.lochanpthakke.empee3.core.sound.dsp

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 8-band peaking EQ inserted into ExoPlayer's DefaultAudioSink (see
 * PlayerProviderModule).
 *
 * Threading: gain / enable changes arrive on the main thread while samples are
 * processed on the playback thread. Setters only write the (volatile) target
 * state; filter coefficients are recomputed on the playback thread before the
 * next buffer, so filters are never mutated mid-buffer.
 */
@Singleton
@Suppress("MagicNumber", "CyclomaticComplexMethod", "NestedBlockDepth")
class CustomEqAudioProcessor
    @Inject
    constructor() : BaseAudioProcessor() {
        @Volatile private var isEqEnabled: Boolean = true
        private val bandGains = FloatArray(BAND_COUNT) { 0.0f }

        @Volatile private var coefficientsDirty = true

        @Volatile private var resetRequested = false

        /** One filter chain per channel of the current input format. */
        private var channelFilters: Array<Array<BiquadFilter>> = emptyArray()

        /** Filters used only for drawing the response curve in the UI. */
        private val previewFilters = Array(BAND_COUNT) { BiquadFilter() }

        fun setEqEnabled(enabled: Boolean) {
            if (isEqEnabled != enabled) {
                isEqEnabled = enabled
                resetRequested = true
            }
        }

        fun isEqEnabled(): Boolean = isEqEnabled

        fun setBandGains(gainsDb: FloatArray) {
            require(gainsDb.size == BAND_COUNT) { "Expected $BAND_COUNT band gains" }
            synchronized(bandGains) {
                for (i in 0 until BAND_COUNT) {
                    bandGains[i] = gainsDb[i].coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)
                }
            }
            coefficientsDirty = true
        }

        fun setBandGain(
            bandIndex: Int,
            gainDb: Float,
        ) {
            require(bandIndex in 0 until BAND_COUNT) { "Invalid band index: $bandIndex" }
            synchronized(bandGains) { bandGains[bandIndex] = gainDb.coerceIn(MIN_GAIN_DB, MAX_GAIN_DB) }
            coefficientsDirty = true
        }

        fun getBandGains(): FloatArray = synchronized(bandGains) { bandGains.copyOf() }

        fun getCombinedFrequencyResponse(
            frequencyPoints: FloatArray,
            sampleRate: Int = 44100,
        ): FloatArray {
            val gains = getBandGains()
            for (b in 0 until BAND_COUNT) {
                previewFilters[b].configurePeakingEq(BAND_FREQUENCIES[b], sampleRate, gains[b])
            }
            val response = FloatArray(frequencyPoints.size)
            for (i in frequencyPoints.indices) {
                var totalDb = 0.0f
                for (b in 0 until BAND_COUNT) {
                    totalDb += previewFilters[b].getFrequencyResponse(frequencyPoints[i], sampleRate)
                }
                response[i] = totalDb
            }
            return response
        }

        override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
            // Only 16-bit PCM is supported; for anything else ExoPlayer bypasses this
            // processor instead of receiving mislabelled output.
            if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount <= 0) {
                throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
            }
            return inputAudioFormat
        }

        override fun onFlush() {
            // Called on the playback thread after configure, seek or track change.
            val channels = inputAudioFormat.channelCount.coerceAtLeast(0)
            if (channelFilters.size != channels) {
                channelFilters = Array(channels) { Array(BAND_COUNT) { BiquadFilter() } }
            }
            coefficientsDirty = true
            resetRequested = true
        }

        override fun onReset() {
            channelFilters = emptyArray()
        }

        override fun queueInput(inputBuffer: ByteBuffer) {
            val remaining = inputBuffer.remaining()
            if (remaining == 0) return

            val outputBuffer = replaceOutputBuffer(remaining)
            val channelCount = inputAudioFormat.channelCount

            if (!isEqEnabled || channelFilters.size != channelCount || isFlat()) {
                outputBuffer.put(inputBuffer)
                outputBuffer.flip()
                return
            }

            applyPendingChanges()

            inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
            outputBuffer.order(ByteOrder.LITTLE_ENDIAN)

            val bytesPerFrame = channelCount * 2
            while (inputBuffer.remaining() >= bytesPerFrame) {
                for (ch in 0 until channelCount) {
                    var sample = inputBuffer.short / SHORT_MAX_FLOAT
                    val filters = channelFilters[ch]
                    for (b in 0 until BAND_COUNT) {
                        sample = filters[b].processSample(sample)
                    }
                    val clamped = sample.coerceIn(-1.0f, 1.0f)
                    outputBuffer.putShort((clamped * SHORT_MAX_FLOAT).toInt().toShort())
                }
            }

            if (inputBuffer.hasRemaining()) {
                outputBuffer.put(inputBuffer)
            }

            outputBuffer.flip()
        }

        private fun isFlat(): Boolean = synchronized(bandGains) { bandGains.all { it == 0.0f } }

        private fun applyPendingChanges() {
            if (coefficientsDirty) {
                coefficientsDirty = false
                val gains = getBandGains()
                val sampleRate = inputAudioFormat.sampleRate.takeIf { it > 0 } ?: 44100
                for (filters in channelFilters) {
                    for (b in 0 until BAND_COUNT) {
                        // Bands at/above Nyquist would make the biquad unstable; leave them flat.
                        val usable = BAND_FREQUENCIES[b] < sampleRate * MAX_BAND_TO_SAMPLE_RATE_RATIO
                        filters[b].configurePeakingEq(BAND_FREQUENCIES[b], sampleRate, if (usable) gains[b] else 0f)
                    }
                }
            }
            if (resetRequested) {
                resetRequested = false
                channelFilters.forEach { filters -> filters.forEach { it.resetState() } }
            }
        }

        companion object {
            const val BAND_COUNT = 8
            val BAND_FREQUENCIES =
                floatArrayOf(
                    60.0f,
                    150.0f,
                    400.0f,
                    1000.0f,
                    2400.0f,
                    6000.0f,
                    12000.0f,
                    16000.0f,
                )
            private const val SHORT_MAX_FLOAT = 32767.0f
            private const val MIN_GAIN_DB = -15f
            private const val MAX_GAIN_DB = 15f
            private const val MAX_BAND_TO_SAMPLE_RATE_RATIO = 0.45f
        }
    }
