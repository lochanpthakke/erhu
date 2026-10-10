package com.lochanpthakke.empee3.core.sound.visualizer

import com.lochanpthakke.empee3.core.model.VisualizerSensitivity
import com.lochanpthakke.empee3.core.model.VisualizerSettings
import com.lochanpthakke.empee3.core.model.VisualizerSmoothing

/** Where the visualizer reads recent playback samples from ([PlaybackAudioTap] in the app). */
interface VisualizerAudioSource {
    val currentSampleRate: Int
    val generation: Int

    /** System.nanoTime of the last flush (seek / new format). */
    val flushNanos: Long
    val framesWritten: Long
    val lastWriteNanos: Long
    val isFormatSupported: Boolean

    fun read(
        dest: FloatArray,
        count: Int,
        framesBeforeNewest: Long,
    ): Boolean
}

enum class VisualizerStatus {
    /** Showing the music that is playing. */
    ACTIVE,

    /** Playback paused or stopped: levels settle to rest. */
    PAUSED,

    /** Playing, no samples yet (just opened, buffering). */
    WAITING,

    /** Playing, but this audio cannot be read (unsupported format, or nothing arrives). */
    UNAVAILABLE,
}

/** What the player reports at a frame: whether it plays, its media position, and which entry is current. */
data class PlaybackClock(
    val isPlaying: Boolean,
    val positionMs: Long,
    val entryKey: Any?,
)

/**
 * One visualizer's analysis: reads the window of samples being heard right now from the source,
 * turns it into smoothed spectrum levels ([spectrum]) and a normalized waveform ([waveform]).
 * Every array is allocated once; [update] allocates nothing per frame.
 *
 * Samples reach the tap before the audio output, which plays them a little later. The heard
 * sample is located from the player's media position: frames are counted from the last flush
 * (seek) or entry change, and media time maps to frames at the stream's sample rate, so playback
 * speed does not matter. When that mapping is not established it falls back to [FALLBACK_LATENCY_MS].
 */
class VisualizerEngine(
    private val source: VisualizerAudioSource,
    val bandCount: Int = DEFAULT_BANDS,
) {
    val spectrum: FloatArray get() = smoother.values
    val waveform = FloatArray(WAVE_POINTS)
    var status: VisualizerStatus = VisualizerStatus.WAITING
        private set

    private val smoother = LevelSmoother(bandCount)
    private val target = FloatArray(bandCount)
    private val samples = FloatArray(SpectrumAnalyzer.DEFAULT_FFT_SIZE)
    private val waveSamples = FloatArray(WAVE_WINDOW)
    private val waveNormalized = FloatArray(WAVE_WINDOW)
    private val waveGain = WaveformGain()
    private var analyzer: SpectrumAnalyzer? = null

    private var lastNanos = 0L
    private var seenGeneration = Int.MIN_VALUE
    private var seenEntry: Any? = NO_ENTRY
    private var baseFrames = 0L
    private var basePositionMs = 0L
    private var mapped = false
    private var lastLatencyFrames = -1L
    private var waitingSinceNanos = 0L

    /** Center frequencies (Hz) of the bands, or null before the sample rate is known. */
    val bandCenterHz: FloatArray? get() = analyzer?.bandCenterHz

    fun apply(settings: VisualizerSettings) {
        val (attack, release) =
            when (settings.smoothing) {
                VisualizerSmoothing.RESPONSIVE -> RESPONSIVE_ATTACK to RESPONSIVE_RELEASE
                VisualizerSmoothing.BALANCED -> LevelSmoother.DEFAULT_ATTACK to LevelSmoother.DEFAULT_RELEASE
                VisualizerSmoothing.SMOOTH -> SMOOTH_ATTACK to SMOOTH_RELEASE
            }
        smoother.attackSeconds = attack
        smoother.releaseSeconds = if (settings.reduceMotion) release * REDUCED_MOTION_RELEASE else release
        dynamicRangeDb =
            when (settings.sensitivity) {
                VisualizerSensitivity.LOW -> LOW_RANGE_DB
                VisualizerSensitivity.NORMAL -> SpectrumAnalyzer.DEFAULT_DYNAMIC_RANGE_DB
                VisualizerSensitivity.HIGH -> HIGH_RANGE_DB
            }
        analyzer?.dynamicRangeDb = dynamicRangeDb
    }

    private var dynamicRangeDb = SpectrumAnalyzer.DEFAULT_DYNAMIC_RANGE_DB

    fun update(
        nowNanos: Long,
        clock: PlaybackClock,
    ) {
        val elapsed = if (lastNanos == 0L) 0f else ((nowNanos - lastNanos) / NANOS_PER_SECOND).toFloat()
        lastNanos = nowNanos
        trackMapping(clock, nowNanos)
        val heard = clock.isPlaying && readHeardWindow(clock)
        status = statusFor(clock, heard, nowNanos)
        if (heard) {
            val active = analyzerFor(source.currentSampleRate)
            active.analyze(samples, target, elapsed)
            waveGain.normalize(waveSamples, WAVE_WINDOW, waveNormalized, elapsed)
            for (i in 0 until WAVE_POINTS) waveform[i] = waveNormalized[i * WAVE_STEP]
        } else {
            target.fill(0f)
            for (i in 0 until WAVE_POINTS) waveform[i] *= WAVE_REST_FACTOR
        }
        smoother.update(target, elapsed)
    }

    /** Forget all history (closing, or a different session). */
    fun reset() {
        smoother.clear()
        waveform.fill(0f)
        waveGain.reset()
        analyzer?.reset()
        mapped = false
        lastLatencyFrames = -1L
        seenGeneration = Int.MIN_VALUE
        seenEntry = NO_ENTRY
        lastNanos = 0L
        waitingSinceNanos = 0L
    }

    private fun statusFor(
        clock: PlaybackClock,
        heard: Boolean,
        nowNanos: Long,
    ): VisualizerStatus {
        if (!clock.isPlaying || heard) {
            waitingSinceNanos = 0L
            return if (heard) VisualizerStatus.ACTIVE else VisualizerStatus.PAUSED
        }
        if (waitingSinceNanos == 0L) waitingSinceNanos = nowNanos
        val waited = nowNanos - waitingSinceNanos
        val gaveUp = waited > UNAVAILABLE_AFTER_NANOS || (!source.isFormatSupported && waited > FORMAT_GRACE_NANOS)
        return if (gaveUp) VisualizerStatus.UNAVAILABLE else VisualizerStatus.WAITING
    }

    /** Re-anchors media time to frames after a flush (seek) or when another entry starts. */
    private fun trackMapping(
        clock: PlaybackClock,
        nowNanos: Long,
    ) {
        val generation = source.generation
        if (generation != seenGeneration) {
            // A flush restarts frame counting at the media position playback resumes from. Only a flush
            // seen just now pins that position; an old one (visualizer opened mid-song) cannot.
            val fresh = seenGeneration != Int.MIN_VALUE && nowNanos - source.flushNanos in 0..FRESH_FLUSH_NANOS
            seenGeneration = generation
            seenEntry = clock.entryKey
            baseFrames = 0L
            basePositionMs = clock.positionMs
            mapped = fresh
            lastLatencyFrames = -1L
            analyzer?.reset()
        } else if (clock.entryKey != seenEntry) {
            // Gapless change of entry (no flush): keep the measured offset, re-based on the new position.
            seenEntry = clock.entryKey
            if (lastLatencyFrames >= 0) {
                baseFrames = (source.framesWritten - lastLatencyFrames).coerceAtLeast(0L)
                basePositionMs = clock.positionMs
            } else {
                mapped = false
            }
        }
    }

    /** Frames between the newest written frame and the one being heard now. */
    private fun currentLatencyFrames(clockPositionMs: Long): Long {
        val rate = source.currentSampleRate
        val fallback = FALLBACK_LATENCY_MS * rate / MS_PER_SECOND
        if (!mapped || rate <= 0) return fallback
        val heardFrame = baseFrames + (clockPositionMs - basePositionMs) * rate / MS_PER_SECOND
        val latency = source.framesWritten - heardFrame
        return if (latency in 0..MAX_LATENCY_MS * rate / MS_PER_SECOND) latency.also { lastLatencyFrames = it } else fallback
    }

    private fun readHeardWindow(clock: PlaybackClock): Boolean {
        if (source.currentSampleRate <= 0 || source.lastWriteNanos == 0L) return false
        val latency = currentLatencyFrames(clock.positionMs)
        val ok = source.read(samples, samples.size, latency)
        if (ok) System.arraycopy(samples, samples.size - WAVE_WINDOW, waveSamples, 0, WAVE_WINDOW)
        return ok
    }

    private fun analyzerFor(sampleRate: Int): SpectrumAnalyzer {
        val current = analyzer
        if (current != null && current.sampleRate == sampleRate) return current
        return SpectrumAnalyzer(sampleRate, bandCount).also {
            it.dynamicRangeDb = dynamicRangeDb
            analyzer = it
        }
    }

    companion object {
        const val DEFAULT_BANDS = 48
        const val WAVE_POINTS = 256
        private const val WAVE_WINDOW = 1024
        private const val WAVE_STEP = WAVE_WINDOW / WAVE_POINTS
        private const val WAVE_REST_FACTOR = 0.85f

        /** Typical audio output buffering, used until the media-time mapping is known. */
        const val FALLBACK_LATENCY_MS = 250L
        private const val MAX_LATENCY_MS = 2_000L
        private const val MS_PER_SECOND = 1_000L
        private const val NANOS_PER_SECOND = 1e9
        private const val UNAVAILABLE_AFTER_NANOS = 2_000_000_000L
        private const val FORMAT_GRACE_NANOS = 500_000_000L
        private const val FRESH_FLUSH_NANOS = 300_000_000L

        private const val RESPONSIVE_ATTACK = 0.02f
        private const val RESPONSIVE_RELEASE = 0.12f
        private const val SMOOTH_ATTACK = 0.08f
        private const val SMOOTH_RELEASE = 0.5f
        private const val REDUCED_MOTION_RELEASE = 1.6f
        private const val LOW_RANGE_DB = 60f
        private const val HIGH_RANGE_DB = 38f

        private val NO_ENTRY = Any()
    }
}
