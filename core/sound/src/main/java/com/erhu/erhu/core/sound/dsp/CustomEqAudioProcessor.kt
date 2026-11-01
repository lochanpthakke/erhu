@file:Suppress("detekt:All")

package com.erhu.erhu.core.sound.dsp

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parametric/Graphic EQ inserted into ExoPlayer's DefaultAudioSink.
 * Supports up to 8 bands with dynamic center frequency, gain, Q factor,
 * filter type (Peaking, Low Shelf, High Shelf), and active band count (4..8).
 */
@Singleton
@Suppress("MagicNumber", "CyclomaticComplexMethod", "NestedBlockDepth")
class CustomEqAudioProcessor
    @Inject
    constructor() : BaseAudioProcessor() {
        @Volatile private var isEqEnabled: Boolean = true

        private val bandGains = FloatArray(BAND_COUNT) { 0.0f }
        private val bandFrequencies = BAND_FREQUENCIES.copyOf()
        private val bandQs = FloatArray(BAND_COUNT) { DEFAULT_Q }
        private val filterTypes = Array(BAND_COUNT) { FilterType.PEAKING }

        @Volatile private var activeBandCount: Int = BAND_COUNT

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
            synchronized(this) {
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
            synchronized(this) { bandGains[bandIndex] = gainDb.coerceIn(MIN_GAIN_DB, MAX_GAIN_DB) }
            coefficientsDirty = true
        }

        fun getBandGains(): FloatArray = synchronized(this) { bandGains.copyOf() }

        fun setBandFrequency(
            bandIndex: Int,
            freqHz: Float,
        ) {
            require(bandIndex in 0 until BAND_COUNT) { "Invalid band index: $bandIndex" }
            synchronized(this) { bandFrequencies[bandIndex] = freqHz.coerceIn(MIN_FREQ_HZ, MAX_FREQ_HZ) }
            coefficientsDirty = true
        }

        fun getBandFrequencies(): FloatArray = synchronized(this) { bandFrequencies.copyOf() }

        fun setBandQ(
            bandIndex: Int,
            q: Float,
        ) {
            require(bandIndex in 0 until BAND_COUNT) { "Invalid band index: $bandIndex" }
            synchronized(this) { bandQs[bandIndex] = q.coerceIn(MIN_Q, MAX_Q) }
            coefficientsDirty = true
        }

        fun getBandQs(): FloatArray = synchronized(this) { bandQs.copyOf() }

        fun setFilterType(
            bandIndex: Int,
            type: FilterType,
        ) {
            require(bandIndex in 0 until BAND_COUNT) { "Invalid band index: $bandIndex" }
            synchronized(this) { filterTypes[bandIndex] = type }
            coefficientsDirty = true
        }

        fun getFilterTypes(): Array<FilterType> = synchronized(this) { filterTypes.copyOf() }

        fun setActiveBandCount(count: Int) {
            synchronized(this) { activeBandCount = count.coerceIn(MIN_BAND_COUNT, BAND_COUNT) }
            coefficientsDirty = true
        }

        fun getActiveBandCount(): Int = activeBandCount

        fun configureParametricState(
            gainsDb: FloatArray,
            frequenciesHz: FloatArray,
            qs: FloatArray,
            types: Array<FilterType>,
            activeCount: Int,
        ) {
            require(gainsDb.size == BAND_COUNT && frequenciesHz.size == BAND_COUNT && qs.size == BAND_COUNT && types.size == BAND_COUNT) {
                "Expected size $BAND_COUNT for all parametric parameters"
            }
            synchronized(this) {
                for (i in 0 until BAND_COUNT) {
                    bandGains[i] = gainsDb[i].coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)
                    bandFrequencies[i] = frequenciesHz[i].coerceIn(MIN_FREQ_HZ, MAX_FREQ_HZ)
                    bandQs[i] = qs[i].coerceIn(MIN_Q, MAX_Q)
                    filterTypes[i] = types[i]
                }
                activeBandCount = activeCount.coerceIn(MIN_BAND_COUNT, BAND_COUNT)
            }
            coefficientsDirty = true
        }

        private data class StateSnapshot(
            val gains: FloatArray,
            val freqs: FloatArray,
            val qs: FloatArray,
            val types: Array<FilterType>,
            val activeCount: Int,
        )

        private fun getParametricStateSnapshot(): StateSnapshot =
            synchronized(this) {
                StateSnapshot(
                    gains = bandGains.copyOf(),
                    freqs = bandFrequencies.copyOf(),
                    qs = bandQs.copyOf(),
                    types = filterTypes.copyOf(),
                    activeCount = activeBandCount,
                )
            }

        fun getCombinedFrequencyResponse(
            frequencyPoints: FloatArray,
            sampleRate: Int = 44100,
        ): FloatArray {
            val (gains, freqs, qs, types, activeCount) = getParametricStateSnapshot()
            for (b in 0 until BAND_COUNT) {
                val active = b < activeCount
                previewFilters[b].configure(
                    centerFrequency = freqs[b],
                    sampleRate = sampleRate,
                    gainInDb = if (active) gains[b] else 0f,
                    q = qs[b],
                    type = types[b],
                )
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
            if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount <= 0) {
                throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
            }
            return inputAudioFormat
        }

        override fun onFlush() {
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

        /**
         * Read-only view of what the processor is doing now, for diagnostics. [configured]: the player
         * configured it for the current stream (it only accepts 16-bit PCM); processing happens only
         * when it is also enabled and not all gains are 0 (otherwise audio is passed through unchanged).
         */
        fun runtimeState(): EqRuntimeState =
            EqRuntimeState(
                enabled = isEqEnabled,
                configured = isActive,
                sampleRateHz = inputAudioFormat.sampleRate.takeIf { it > 0 },
                channelCount = inputAudioFormat.channelCount.takeIf { it > 0 },
                allGainsZero = isFlat(),
            )

        private fun isFlat(): Boolean = synchronized(this) { bandGains.all { it == 0.0f } }

        private fun applyPendingChanges() {
            if (coefficientsDirty) {
                coefficientsDirty = false
                val (gains, freqs, qs, types, activeCount) = getParametricStateSnapshot()
                val sampleRate = inputAudioFormat.sampleRate.takeIf { it > 0 } ?: 44100
                for (filters in channelFilters) {
                    for (b in 0 until BAND_COUNT) {
                        val active = b < activeCount
                        val freq = freqs[b]
                        val usable = active && (freq < sampleRate * MAX_BAND_TO_SAMPLE_RATE_RATIO)
                        filters[b].configure(
                            centerFrequency = freq,
                            sampleRate = sampleRate,
                            gainInDb = if (usable) gains[b] else 0f,
                            q = qs[b],
                            type = types[b],
                        )
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
            const val MIN_BAND_COUNT = 4
            const val DEFAULT_Q = 1.414f
            const val MIN_Q = 0.5f
            const val MAX_Q = 5.0f
            const val MIN_FREQ_HZ = 20.0f
            const val MAX_FREQ_HZ = 18000.0f

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
