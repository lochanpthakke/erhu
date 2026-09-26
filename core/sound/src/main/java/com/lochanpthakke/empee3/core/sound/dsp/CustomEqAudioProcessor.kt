@file:Suppress("detekt:All")

package com.lochanpthakke.empee3.core.sound.dsp

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@Suppress("MagicNumber", "CyclomaticComplexMethod", "NestedBlockDepth")
class CustomEqAudioProcessor
    @Inject
    constructor() : BaseAudioProcessor() {
        private var isEqEnabled: Boolean = true
        private val bandGains = FloatArray(BAND_COUNT) { 0.0f }

        private val leftFilters = Array(BAND_COUNT) { BiquadFilter() }
        private val rightFilters = Array(BAND_COUNT) { BiquadFilter() }

        fun setEqEnabled(enabled: Boolean) {
            if (isEqEnabled != enabled) {
                isEqEnabled = enabled
                resetFilterStates()
            }
        }

        fun setBandGains(gainsDb: FloatArray) {
            require(gainsDb.size == BAND_COUNT) { "Expected $BAND_COUNT band gains" }
            for (i in 0 until BAND_COUNT) {
                bandGains[i] = gainsDb[i]
            }
            updateFilterCoefficients()
        }

        fun setBandGain(
            bandIndex: Int,
            gainDb: Float,
        ) {
            require(bandIndex in 0 until BAND_COUNT) { "Invalid band index: $bandIndex" }
            bandGains[bandIndex] = gainDb
            updateFilterCoefficients()
        }

        fun getBandGains(): FloatArray = bandGains.copyOf()

        fun getCombinedFrequencyResponse(
            frequencyPoints: FloatArray,
            sampleRate: Int = 44100,
        ): FloatArray {
            val response = FloatArray(frequencyPoints.size)
            for (i in frequencyPoints.indices) {
                var totalDb = 0.0f
                for (b in 0 until BAND_COUNT) {
                    totalDb += leftFilters[b].getFrequencyResponse(frequencyPoints[i], sampleRate)
                }
                response[i] = totalDb
            }
            return response
        }

        override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
            updateFilterCoefficients(inputAudioFormat.sampleRate)
            return AudioProcessor.AudioFormat(
                inputAudioFormat.sampleRate,
                inputAudioFormat.channelCount,
                C.ENCODING_PCM_16BIT,
            )
        }

        override fun queueInput(inputBuffer: ByteBuffer) {
            val remaining = inputBuffer.remaining()
            if (remaining == 0) return

            val outputBuffer = replaceOutputBuffer(remaining)

            if (!isEqEnabled) {
                outputBuffer.put(inputBuffer)
                outputBuffer.flip()
                return
            }

            val channelCount = inputAudioFormat.channelCount
            val sampleRate = inputAudioFormat.sampleRate

            if (sampleRate <= 0 || channelCount <= 0 || inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
                outputBuffer.put(inputBuffer)
                outputBuffer.flip()
                return
            }

            inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
            outputBuffer.order(ByteOrder.LITTLE_ENDIAN)

            val bytesPerFrame = channelCount * 2
            while (inputBuffer.remaining() >= bytesPerFrame) {
                for (ch in 0 until channelCount) {
                    val sampleShort = inputBuffer.short
                    var sampleFloat = sampleShort / SHORT_MAX_FLOAT

                    if (ch == 0) {
                        for (b in 0 until BAND_COUNT) {
                            sampleFloat = leftFilters[b].processSample(sampleFloat)
                        }
                    } else {
                        for (b in 0 until BAND_COUNT) {
                            sampleFloat = rightFilters[b].processSample(sampleFloat)
                        }
                    }

                    val clamped = sampleFloat.coerceIn(-1.0f, 1.0f)
                    val outShort = (clamped * SHORT_MAX_FLOAT).toInt().toShort()
                    outputBuffer.putShort(outShort)
                }
            }

            if (inputBuffer.hasRemaining()) {
                outputBuffer.put(inputBuffer)
            }

            outputBuffer.flip()
        }

        private fun updateFilterCoefficients(sampleRate: Int = inputAudioFormat.sampleRate) {
            val sr = if (sampleRate > 0) sampleRate else 44100
            for (i in 0 until BAND_COUNT) {
                leftFilters[i].configurePeakingEq(BAND_FREQUENCIES[i], sr, bandGains[i])
                rightFilters[i].configurePeakingEq(BAND_FREQUENCIES[i], sr, bandGains[i])
            }
        }

        private fun resetFilterStates() {
            for (i in 0 until BAND_COUNT) {
                leftFilters[i].resetState()
                rightFilters[i].resetState()
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
        }
    }
