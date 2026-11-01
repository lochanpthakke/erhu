package com.erhu.erhu.core.network.bpm

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/** Mono PCM in -1..1 at [sampleRate]. */
class Pcm(
    val samples: FloatArray,
    val sampleRate: Int,
)

/** Audio of a local file as [Pcm], for on-device analysis. Throws [IOException] when it can't be decoded. */
fun interface PcmSource {
    suspend fun decode(filePath: String): Pcm
}

/**
 * Decodes [ANALYSIS_SECONDS] of a file (from a quarter of the way in, past most intros) with the
 * platform decoders (MediaExtractor + MediaCodec), mixed to mono and reduced to about 11 kHz,
 * plenty for beats. Everything stays on the device.
 */
@Singleton
class AudioPcmDecoder
    @Inject
    constructor() : PcmSource {
        override suspend fun decode(filePath: String): Pcm =
            withContext(Dispatchers.IO) {
                val extractor = MediaExtractor()
                var codec: MediaCodec? = null
                try {
                    extractor.setDataSource(filePath)
                    val track =
                        (0 until extractor.trackCount).firstOrNull { index ->
                            extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
                        } ?: throw IOException("No audio track in the file")
                    extractor.selectTrack(track)
                    val format = extractor.getTrackFormat(track)
                    seekToAnalysisStart(extractor, format)
                    val decoder = MediaCodec.createDecoderByType(requireNotNull(format.getString(MediaFormat.KEY_MIME)))
                    codec = decoder
                    decoder.configure(format, null, null, 0)
                    decoder.start()
                    decodeLoop(extractor, decoder, format)
                } catch (e: IllegalStateException) {
                    throw IOException("The audio couldn't be decoded: ${e.message}", e)
                } catch (e: IllegalArgumentException) {
                    throw IOException("The audio couldn't be decoded: ${e.message}", e)
                } finally {
                    runCatching { codec?.stop() }
                    runCatching { codec?.release() }
                    extractor.release()
                }
            }

        private fun seekToAnalysisStart(
            extractor: MediaExtractor,
            format: MediaFormat,
        ) {
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
            val windowUs = ANALYSIS_SECONDS * MICROS
            val startUs = minOf((durationUs * START_FRACTION).toLong(), durationUs - windowUs).coerceAtLeast(0L)
            if (startUs > 0) extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
        }

        @Suppress("CyclomaticComplexMethod", "LongMethod", "NestedBlockDepth", "LoopWithTooManyJumpStatements")
        private suspend fun decodeLoop(
            extractor: MediaExtractor,
            codec: MediaCodec,
            inputFormat: MediaFormat,
        ): Pcm {
            var sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var floatPcm = false
            var step = maxOf(1, sampleRate / TARGET_RATE)
            var out = FloatArray(ANALYSIS_SECONDS * (sampleRate / step + 1))
            var size = 0
            var sum = 0f
            var summed = 0
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            val deadline = System.nanoTime() + TIMEOUT_NANOS

            while (size < out.size) {
                coroutineContext.ensureActive()
                if (System.nanoTime() > deadline) throw IOException("Decoding took too long")
                if (!inputDone) {
                    val index = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                    if (index >= 0) {
                        val buffer = requireNotNull(codec.getInputBuffer(index))
                        val read = extractor.readSampleData(buffer, 0)
                        if (read < 0) {
                            codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(index, 0, read, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val index = codec.dequeueOutputBuffer(info, DEQUEUE_TIMEOUT_US)
                if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val format = codec.outputFormat
                    sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    floatPcm =
                        format.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                        format.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                    if (size == 0) {
                        step = maxOf(1, sampleRate / TARGET_RATE)
                        out = FloatArray(ANALYSIS_SECONDS * (sampleRate / step + 1))
                    }
                }
                if (index < 0) continue
                val buffer = requireNotNull(codec.getOutputBuffer(index)).order(ByteOrder.nativeOrder())
                buffer.position(info.offset)
                buffer.limit(info.offset + info.size)
                val frames = if (floatPcm) info.size / (FLOAT_BYTES * channels) else info.size / (SHORT_BYTES * channels)
                repeat(frames) {
                    var mono = 0f
                    repeat(channels) {
                        mono += if (floatPcm) buffer.getFloat() else buffer.getShort() / SHORT_SCALE
                    }
                    sum += mono / channels
                    if (++summed == step) {
                        if (size < out.size) out[size++] = sum / step
                        sum = 0f
                        summed = 0
                    }
                }
                codec.releaseOutputBuffer(index, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
            }
            return Pcm(out.copyOf(size), sampleRate / step)
        }

        companion object {
            const val ANALYSIS_SECONDS = TempoEstimator.SECTION_SECONDS * 3
            private const val START_FRACTION = 0.25
            private const val TARGET_RATE = 11_025
            private const val MICROS = 1_000_000L
            private const val DEQUEUE_TIMEOUT_US = 10_000L
            private const val TIMEOUT_NANOS = 30_000_000_000L
            private const val FLOAT_BYTES = 4
            private const val SHORT_BYTES = 2
            private const val SHORT_SCALE = 32_768f
        }
    }
