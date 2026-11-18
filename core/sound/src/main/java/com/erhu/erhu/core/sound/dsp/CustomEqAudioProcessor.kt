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
 * Parametric/Graphic EQ inserted into ExoPlayer's DefaultAudioSink: up to 8 bands of peak, low
 * and high shelf, low and high pass (12/24/48 dB/octave) and notch filters (RBJ cookbook biquads,
 * see [BandFilter]). Changes cross-fade in, and a safety limiter keeps boosts from clipping.
 */
@Singleton
@Suppress("MagicNumber", "CyclomaticComplexMethod", "NestedBlockDepth")
class CustomEqAudioProcessor
    @Inject
    constructor() : BaseAudioProcessor() {
        private val utilityDsp = AudioUtilityDsp()
        private var utilityFrame = FloatArray(0)

        @Volatile var utilitySettingsProvider: (() -> com.erhu.erhu.core.model.AudioUtilities)? = null

        fun setUtilities(value: com.erhu.erhu.core.model.AudioUtilities) {
            if (utilityDsp.settings !=
                value
            ) {
                utilityDsp.settings = value.sanitized()
            }
        }

        fun setReplayGain(value: ReplayGainData) {
            utilityDsp.replayGain = value
        }

        @Volatile private var isEqEnabled: Boolean = true

        /** The bands to apply, as last configured (always [BAND_COUNT] entries; unused ones disabled). */
        private var specs: List<BandSpec> = List(BAND_COUNT) { flatSpec(it) }

        @Volatile private var coefficientsDirty = true

        @Volatile private var resetRequested = false

        /** One band chain per channel of the current input format: what plays now. */
        private var current: Array<Array<BandFilter>> = emptyArray()

        /** While a change fades in: the chains with the new settings, run beside [current]. */
        private var next: Array<Array<BandFilter>>? = null
        private var fadePosition = 0
        private var fadeLength = 1

        /** True when every band of [current] passes audio unchanged. */
        private var currentFlat = true

        /**
         * Safety limiter after the bands: boosts keep their full effect, and only a sample that would
         * clip pulls the level down (at once), which then recovers smoothly ([limiterRelease]).
         */
        private var limiterGain = 1f
        private var limiterRelease = 0f

        fun setEqEnabled(enabled: Boolean) {
            if (isEqEnabled != enabled) {
                isEqEnabled = enabled
                resetRequested = true
            }
        }

        fun isEqEnabled(): Boolean = isEqEnabled

        /** Applies [bands] (up to [BAND_COUNT]; missing ones are off). Changes fade in without a click. */
        fun configureBands(bands: List<BandSpec>) {
            val clean =
                List(BAND_COUNT) { i ->
                    bands.getOrNull(i)?.let { sanitize(it) } ?: flatSpec(i).copy(enabled = false)
                }
            synchronized(this) { specs = clean }
            coefficientsDirty = true
        }

        fun bandSpecs(): List<BandSpec> = synchronized(this) { specs }

        /** Sets one band's gain, keeping the rest (a graphic-style edit of the configured bands). */
        fun setBandGain(
            bandIndex: Int,
            gainDb: Float,
        ) {
            require(bandIndex in 0 until BAND_COUNT) { "Invalid band index: $bandIndex" }
            synchronized(this) {
                specs = specs.toMutableList().also { it[bandIndex] = sanitize(it[bandIndex].copy(gainDb = gainDb)) }
            }
            coefficientsDirty = true
        }

        fun setBandGains(gainsDb: FloatArray) {
            require(gainsDb.size == BAND_COUNT) { "Expected $BAND_COUNT band gains" }
            synchronized(this) { specs = specs.mapIndexed { i, spec -> sanitize(spec.copy(gainDb = gainsDb[i])) } }
            coefficientsDirty = true
        }

        fun getBandGains(): FloatArray = synchronized(this) { FloatArray(BAND_COUNT) { specs[it].gainDb } }

        /** The sample rate the bands run at now (44.1 kHz before the first stream). */
        fun sampleRate(): Int = inputAudioFormat.sampleRate.takeIf { it > 0 } ?: DEFAULT_SAMPLE_RATE

        fun getCombinedFrequencyResponse(
            frequencyPoints: FloatArray,
            sampleRate: Int = sampleRate(),
        ): FloatArray = combinedResponseDb(bandSpecs(), frequencyPoints, sampleRate)

        override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
            if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount <= 0) {
                throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
            }
            return inputAudioFormat
        }

        override fun onFlush() {
            val channels = inputAudioFormat.channelCount.coerceAtLeast(0)
            // A new stream (or sample rate) starts from scratch: no fade from the previous stream's filters.
            current = Array(channels) { Array(BAND_COUNT) { BandFilter() } }
            next = null
            currentFlat = true
            utilityFrame = FloatArray(channels)
            utilityDsp.flush(inputAudioFormat.sampleRate, channels)
            coefficientsDirty = true
            resetRequested = true
        }

        override fun onReset() {
            current = emptyArray()
            next = null
        }

        override fun queueInput(inputBuffer: ByteBuffer) {
            val remaining = inputBuffer.remaining()
            if (remaining == 0) return

            val outputBuffer = replaceOutputBuffer(remaining)
            val channelCount = inputAudioFormat.channelCount

            utilitySettingsProvider?.invoke()?.let(::setUtilities)
            val utilityActive = utilityDsp.beginBlock()
            if (current.size == channelCount) applyPendingChanges()
            val eqActive = isEqEnabled && current.size == channelCount && (!currentFlat || next != null || limiterGain < 1f)
            if (!eqActive && !utilityActive) {
                outputBuffer.put(inputBuffer)
                outputBuffer.flip()
                return
            }

            inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
            outputBuffer.order(ByteOrder.LITTLE_ENDIAN)

            val bytesPerFrame = channelCount * 2
            while (inputBuffer.remaining() >= bytesPerFrame) {
                for (ch in 0 until channelCount) utilityFrame[ch] = inputBuffer.short / SHORT_MAX_FLOAT
                if (utilityActive) utilityDsp.beforeEq(utilityFrame)
                if (eqActive) processEqFrame(channelCount)
                if (utilityActive) utilityDsp.afterEq(utilityFrame)
                for (sample in utilityFrame) {
                    outputBuffer.putShort((sample.coerceIn(-1f, 1f) * SHORT_MAX_FLOAT).toInt().toShort())
                }
            }

            if (inputBuffer.hasRemaining()) {
                outputBuffer.put(inputBuffer)
            }

            outputBuffer.flip()
        }

        private fun processEqFrame(channelCount: Int) {
            val fading = next
            val t = if (fading != null) fadePosition.toFloat() / fadeLength else 1f
            for (ch in 0 until channelCount) {
                val x = utilityFrame[ch]
                var y = x
                for (band in current[ch]) y = band.process(y)
                if (fading != null) {
                    var z = x
                    for (band in fading[ch]) z = band.process(z)
                    y += (z - y) * t
                }
                utilityFrame[ch] = y
            }
            var loudest = 0f
            for (ch in 0 until channelCount) loudest = maxOf(loudest, kotlin.math.abs(utilityFrame[ch]))
            val allowed = if (loudest > LIMIT) LIMIT / loudest else 1f
            limiterGain = if (allowed < limiterGain) allowed else minOf(1f, limiterGain + (1f - limiterGain) * limiterRelease)
            if (limiterGain > 1f - LIMITER_SNAP) limiterGain = 1f
            if (limiterGain < 1f) for (ch in 0 until channelCount) utilityFrame[ch] *= limiterGain
            if (fading != null && ++fadePosition >= fadeLength) finishFade()
        }

        private fun finishFade() {
            next?.let { current = it }
            next = null
            currentFlat = current.firstOrNull()?.all { it.isIdentity } ?: true
        }

        /** Builds chains for the latest settings and fades them in (directly when nothing is audible yet). */
        private fun applyPendingChanges() {
            if (resetRequested) {
                resetRequested = false
                finishFade()
                current.forEach { chain -> chain.forEach { it.resetState() } }
            }
            if (!coefficientsDirty) return
            coefficientsDirty = false
            if (next != null) finishFade()
            val rate = sampleRate()
            val bands = bandSpecs()
            val built = Array(current.size) { Array(BAND_COUNT) { b -> BandFilter().also { it.configure(bands[b], rate) } } }
            for (ch in built.indices) for (b in 0 until BAND_COUNT) built[ch][b].copyStateFrom(current[ch][b])
            limiterRelease = 1f - kotlin.math.exp(-1f / (rate * LIMITER_RELEASE_SECONDS))
            val nowFlat = built.firstOrNull()?.all { it.isIdentity } ?: true
            if (currentFlat && nowFlat) {
                current = built
                return
            }
            next = built
            fadePosition = 0
            fadeLength = (rate * FADE_SECONDS).toInt().coerceAtLeast(1)
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

        private fun isFlat(): Boolean = currentFlat && next == null

        companion object {
            const val BAND_COUNT = 8
            const val MIN_BAND_COUNT = 4
            const val DEFAULT_Q = 1.414f
            const val MIN_Q = 0.1f
            const val MAX_Q = 10.0f
            const val MIN_FREQ_HZ = 20.0f
            const val MAX_FREQ_HZ = 20000.0f
            const val MIN_GAIN_DB = -12f
            const val MAX_GAIN_DB = 12f

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
            private const val DEFAULT_SAMPLE_RATE = 44100

            /** Long enough to hide a coefficient change, short enough to feel immediate while dragging. */
            private const val FADE_SECONDS = 0.02f
            private const val LIMIT = 0.97f
            private const val LIMITER_RELEASE_SECONDS = 0.15f
            private const val LIMITER_SNAP = 0.0005f

            fun flatSpec(index: Int): BandSpec =
                BandSpec(FilterType.PEAKING, BAND_FREQUENCIES[index.coerceIn(0, BAND_COUNT - 1)], 0f, DEFAULT_Q)

            fun sanitize(spec: BandSpec): BandSpec =
                spec.copy(
                    frequencyHz = spec.frequencyHz.coerceIn(MIN_FREQ_HZ, MAX_FREQ_HZ),
                    gainDb = if (spec.type.hasGain) spec.gainDb.coerceIn(MIN_GAIN_DB, MAX_GAIN_DB) else 0f,
                    q = spec.q.coerceIn(MIN_Q, MAX_Q),
                    slope = spec.slope.takeIf { it in BandFilter.SLOPES } ?: BandFilter.SLOPES.first(),
                )
        }
    }
