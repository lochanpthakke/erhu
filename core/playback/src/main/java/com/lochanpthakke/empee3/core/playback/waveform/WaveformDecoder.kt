package com.lochanpthakke.empee3.core.playback.waveform

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import com.lochanpthakke.empee3.core.model.BandLevels
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.nio.ByteOrder

/** Why no waveform could be made; playback is never affected. */
internal class WaveformDecodeException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * Decodes a track with the platform decoders (MediaExtractor + MediaCodec: every format the
 * device can play, from content:// or file URIs) and streams the PCM straight into a
 * [WaveformAccumulator]. Cancellable between buffers; the codec and extractor are always released.
 */
internal class WaveformDecoder(
    private val context: Context,
) {
    /** Levels 0..1 ([bins] values), or null when the track holds no audio frames. */
    suspend fun decode(
        uri: Uri,
        bins: Int,
        fallbackDurationMs: Long,
    ): FloatArray? = decodeGuarded(uri, fallbackDurationMs, null) { durationUs -> WaveformAccumulator(bins, durationUs) }.finish()

    /**
     * Three-band levels of [bins] equal slices of [startMs, endMs) only: the decoder seeks to the
     * range and stops after it, so a few seconds cost a few seconds of decoding.
     */
    suspend fun decodeBands(
        uri: Uri,
        startMs: Long,
        endMs: Long,
        bins: Int,
    ): BandLevels? {
        val range = LongRange(startMs * MICROS_PER_MS, endMs * MICROS_PER_MS)
        val length = (range.last - range.first).coerceAtLeast(1L)
        return decodeGuarded(uri, 0L, range) { BandWaveformAccumulator(bins, length, range.first) }.finish()
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun <S : PcmSink> decodeGuarded(
        uri: Uri,
        fallbackDurationMs: Long,
        rangeUs: LongRange?,
        sinkFor: (durationUs: Long) -> S,
    ): S =
        try {
            decodeOrFail(uri, fallbackDurationMs, rangeUs, sinkFor)
        } catch (e: CancellationException) {
            // Cancellation is a RuntimeException too: it must stay a cancellation.
            throw e
        } catch (e: Exception) {
            // No audio track, unsupported codec, corrupt data (IllegalState / IllegalArgument /
            // CodecException), unreadable file (IOException, SecurityException): no waveform.
            throw WaveformDecodeException("Could not decode: ${e.message}", e)
        }

    @Suppress("CyclomaticComplexMethod", "NestedBlockDepth", "LongMethod")
    private suspend fun <S : PcmSink> decodeOrFail(
        uri: Uri,
        fallbackDurationMs: Long,
        rangeUs: LongRange?,
        sinkFor: (durationUs: Long) -> S,
    ): S {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            val track =
                requireNotNull(
                    (0 until extractor.trackCount).firstOrNull {
                        extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
                    },
                ) { "No audio track" }
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = requireNotNull(format.getString(MediaFormat.KEY_MIME)) { "Unknown codec" }
            val durationUs =
                (if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L)
                    .takeIf { it > 0 } ?: (fallbackDurationMs * MICROS_PER_MS)
            require(durationUs > 0 || rangeUs != null) { "Unknown duration" }
            if (rangeUs != null) extractor.seekTo(rangeUs.first, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            val decoder = MediaCodec.createDecoderByType(mime).also { codec = it }
            decoder.configure(format, null, null, 0)
            decoder.start()

            val accumulator = sinkFor(durationUs)
            var channels = format.integerOr(MediaFormat.KEY_CHANNEL_COUNT, 2)
            var sampleRate = format.integerOr(MediaFormat.KEY_SAMPLE_RATE, DEFAULT_SAMPLE_RATE)
            var floatPcm = false
            var shorts = ShortArray(0)
            var floats = FloatArray(0)
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            while (true) {
                currentCoroutineContext().ensureActive()
                if (!inputDone) {
                    val inIndex = decoder.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val buffer = checkNotNull(decoder.getInputBuffer(inIndex)) { "No input buffer" }
                        val size = extractor.readSampleData(buffer, 0)
                        val pastRange = rangeUs != null && extractor.sampleTime > rangeUs.last
                        if (size < 0 || pastRange) {
                            decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIndex = decoder.dequeueOutputBuffer(info, TIMEOUT_US)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val out = decoder.outputFormat
                        channels = out.integerOr(MediaFormat.KEY_CHANNEL_COUNT, channels)
                        sampleRate = out.integerOr(MediaFormat.KEY_SAMPLE_RATE, sampleRate)
                        floatPcm = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                            out.integerOr(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT) == AudioFormat.ENCODING_PCM_FLOAT
                    }
                    outIndex >= 0 -> {
                        val buffer = decoder.getOutputBuffer(outIndex)
                        if (buffer != null && info.size > 0) {
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            val pcm = buffer.slice().order(ByteOrder.nativeOrder())
                            if (floatPcm) {
                                val count = info.size / Float.SIZE_BYTES
                                if (floats.size < count) floats = FloatArray(count)
                                pcm.asFloatBuffer().get(floats, 0, count)
                                accumulator.addPcmFloat(floats, count, channels, sampleRate, info.presentationTimeUs)
                            } else {
                                val count = info.size / Short.SIZE_BYTES
                                if (shorts.size < count) shorts = ShortArray(count)
                                pcm.asShortBuffer().get(shorts, 0, count)
                                accumulator.addPcm16(shorts, count, channels, sampleRate, info.presentationTimeUs)
                            }
                        }
                        decoder.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                }
            }
            return accumulator
        } finally {
            codec?.let { decoder ->
                runCatching { decoder.stop() }
                runCatching { decoder.release() }
            }
            runCatching { extractor.release() }
        }
    }

    private fun MediaFormat.integerOr(
        key: String,
        default: Int,
    ): Int = if (containsKey(key)) getInteger(key) else default

    private companion object {
        const val TIMEOUT_US = 10_000L
        const val MICROS_PER_MS = 1_000L
        const val DEFAULT_SAMPLE_RATE = 44_100
    }
}
