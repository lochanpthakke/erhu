package com.lochanpthakke.empee3.core.sound.visualizer

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Read-only tap on the PCM that playback sends to the audio output (after the equalizer), for the
 * visualizer. Fed by a pass-through processor in the player's audio chain; it never changes the audio.
 *
 * On the audio thread it only counts frames, and, while at least one visualizer holds the tap
 * ([acquire]), copies them downmixed to mono into a fixed ring buffer. No lock, no allocation,
 * nothing it can wait on: a slow or absent reader can only miss samples, never hold playback up.
 * Readers copy the most recent window out of the ring ([read]); a read racing a write may see a few
 * mixed samples, which is harmless for drawing.
 */
@Singleton
class PlaybackAudioTap
    @Inject
    constructor() : VisualizerAudioSource {
        private val ring = FloatArray(CAPACITY)
        private val users = AtomicInteger()

        @Volatile private var enabled = false

        @Volatile private var writtenFrames = 0L

        /** First frame copied into the ring by the current reader session: anything before it is stale. */
        @Volatile private var collectFrom = Long.MAX_VALUE

        @Volatile private var sampleRate = 0

        @Volatile private var channels = 0

        @Volatile private var floatPcm = false

        @Volatile private var supported = false

        /** Changes on every flush (seek, new stream format): frame counting restarts at 0. */
        @Volatile override var generation = 0
            private set

        /** System.nanoTime of the last flush. */
        @Volatile override var flushNanos = 0L
            private set

        /** System.nanoTime of the last buffer, 0 if none since the last flush. */
        @Volatile override var lastWriteNanos = 0L
            private set

        /** A visualizer starts reading. Samples are only collected while someone reads. */
        fun acquire() {
            if (users.incrementAndGet() == 1) {
                collectFrom = writtenFrames
                enabled = true
            }
        }

        /** A visualizer stops reading; with no reader left, collection stops. */
        fun release() {
            if (users.decrementAndGet() <= 0) {
                users.set(0)
                enabled = false
                collectFrom = Long.MAX_VALUE
            }
        }

        val isCollecting: Boolean get() = enabled

        // ---- Peak meter (post-EQ sample peaks, only while collecting) ----

        @Volatile private var meterPeak = 0f

        @Volatile private var meterFullScaleSamples = 0L

        @Volatile private var meterFrames = 0L

        /**
         * Sample peaks of the audio sent to the output (after the EQ), measured since [resetMeter], while
         * collecting. Sample peaks only: peaks between samples (true peak) are not measured.
         */
        fun meterReading(): PeakReading = PeakReading(meterPeak, meterFullScaleSamples, meterFrames, if (supported) sampleRate else 0)

        fun resetMeter() {
            meterPeak = 0f
            meterFullScaleSamples = 0L
            meterFrames = 0L
        }

        // ---- Audio thread ----

        /** New stream format, also on every flush. Only 16-bit and float PCM can be read. */
        fun onFormat(
            sampleRateHz: Int,
            channelCount: Int,
            pcm16: Boolean,
            pcmFloat: Boolean,
        ) {
            sampleRate = sampleRateHz
            channels = channelCount
            floatPcm = pcmFloat
            supported = (pcm16 || pcmFloat) && sampleRateHz > 0 && channelCount > 0
            writtenFrames = 0L
            collectFrom = if (enabled) 0L else Long.MAX_VALUE
            lastWriteNanos = 0L
            flushNanos = System.nanoTime()
            generation++
        }

        /** A buffer about to be played. [buffer]'s position and limit are left untouched. */
        fun write(buffer: ByteBuffer) {
            val ch = channels
            if (!supported || ch <= 0) return
            val bytesPerSample = if (floatPcm) Float.SIZE_BYTES else Short.SIZE_BYTES
            val frames = buffer.remaining() / (bytesPerSample * ch)
            val first = writtenFrames
            if (enabled) copyMono(buffer.duplicate().order(ByteOrder.nativeOrder()), first, frames, ch)
            writtenFrames = first + frames
            lastWriteNanos = System.nanoTime()
        }

        /** Downmixes [frames] interleaved frames of [data] into the ring, starting at frame [first]. */
        private fun copyMono(
            data: ByteBuffer,
            first: Long,
            frames: Int,
            ch: Int,
        ) {
            val float = floatPcm
            var peak = 0f
            var fullScale = 0L
            for (frame in 0 until frames) {
                var sum = 0f
                repeat(ch) {
                    val sample = if (float) data.float else data.short / PCM16_SCALE
                    val magnitude = kotlin.math.abs(sample)
                    if (magnitude > peak) peak = magnitude
                    if (magnitude >= FULL_SCALE) fullScale++
                    sum += sample
                }
                ring[((first + frame) and MASK).toInt()] = sum / ch
            }
            if (peak > meterPeak) meterPeak = peak
            meterFullScaleSamples += fullScale
            meterFrames += frames
        }

        // ---- Reader ----

        override val currentSampleRate: Int get() = if (supported) sampleRate else 0

        /** Frames written since the last flush. */
        override val framesWritten: Long get() = writtenFrames

        override val isFormatSupported: Boolean get() = supported

        /**
         * Copies [count] mono samples ending [framesBeforeNewest] frames before the newest one into
         * [dest]. Samples not collected by this reader session read as 0. False when nothing is available.
         */
        override fun read(
            dest: FloatArray,
            count: Int,
            framesBeforeNewest: Long,
        ): Boolean {
            val newest = writtenFrames
            val from = collectFrom
            if (!supported || !enabled || newest <= from) return false
            val end = newest - framesBeforeNewest.coerceIn(0L, (CAPACITY - count).toLong())
            val start = end - count
            val oldestKept = maxOf(from, newest - CAPACITY)
            for (i in 0 until count) {
                val frame = start + i
                dest[i] = if (frame < oldestKept || frame >= newest) 0f else ring[(frame and MASK).toInt()]
            }
            return end > oldestKept
        }

        companion object {
            /** About 2.7 s at 48 kHz: enough to look back past the output buffer, never more. */
            const val CAPACITY = 1 shl 17
            private const val MASK = (CAPACITY - 1).toLong()
            private const val PCM16_SCALE = 32_768f

            /** A 16-bit sample at its largest value (32767/32768) counts as full scale. */
            private const val FULL_SCALE = 32_767f / 32_768f
        }
    }

/** What the peak meter saw: the largest sample magnitude (0..1), samples at full scale, frames measured. */
data class PeakReading(
    val peak: Float,
    val fullScaleSamples: Long,
    val frames: Long,
    val sampleRateHz: Int,
) {
    /** Peak in dBFS, or null when nothing was measured. */
    val peakDbfs: Float? get() = if (frames == 0L || peak <= 0f) null else (DB_PER_DECADE * kotlin.math.log10(peak.toDouble())).toFloat()

    val secondsMeasured: Float get() = if (sampleRateHz > 0) frames.toFloat() / sampleRateHz else 0f
}

private const val DB_PER_DECADE = 20
