package com.erhu.erhu.core.playback.diagnostics

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.provider.MediaStore
import com.erhu.erhu.core.common.dispatcher.DispatcherProvider
import com.erhu.erhu.core.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Inspects the playing file without decoding it, preferring the most direct evidence:
 * its own audio header (FILE_HEADER), then Android's extractor (CONTAINER), then the library index
 * (LIBRARY). Reads at most [HEADER_BYTES] from the start of the file, on the IO dispatcher.
 */
@Singleton
class SourceInspector
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val dispatchers: DispatcherProvider,
    ) {
        private class ExtractorFacts(
            val mime: String?,
            val sampleRate: Int?,
            val channels: Int?,
            val bitrate: Int?,
            val durationUs: Long?,
        )

        @Suppress("TooGenericExceptionCaught")
        suspend fun inspect(song: Song): SourceDiagnostics =
            withContext(dispatchers.io) {
                val uri = Uri.parse(song.uri)
                var problem: String? = null
                val header =
                    try {
                        readPrefix(uri)?.let(SourceHeaders::parse)
                    } catch (e: Exception) {
                        // SecurityException (permission), IOException (missing file).
                        problem = "The file could not be read (${e.javaClass.simpleName})."
                        null
                    }
                val extractor = runCatching { extract(uri) }.getOrNull()
                val (size, storeMime) = runCatching { mediaStore(uri) }.getOrNull() ?: (null to null)
                build(song, header, extractor, size, storeMime, problem)
            }

        @Suppress("CyclomaticComplexMethod", "LongParameterList")
        private fun build(
            song: Song,
            header: SourceHeader?,
            extractor: ExtractorFacts?,
            size: Long?,
            storeMime: String?,
            problem: String?,
        ): SourceDiagnostics {
            val codecFromContainer = MediaFormats.codecName(extractor?.mime)
            val codec =
                header?.let { Fact(it.codec, Provenance.FILE_HEADER) }
                    ?: codecFromContainer?.let { Fact(it, Provenance.CONTAINER) }
            val container = containerOf(storeMime ?: song.mimeType, song.filePath ?: song.uri)?.let { Fact(it, Provenance.LIBRARY) }
            val sampleRate =
                header?.sampleRateHz?.let { Fact(MediaFormats.sampleRateText(it), Provenance.FILE_HEADER) }
                    ?: extractor?.sampleRate?.let { Fact(MediaFormats.sampleRateText(it), Provenance.CONTAINER) }
                    ?: song.sampleRate.takeIf { it > 0 }?.let { Fact(MediaFormats.sampleRateText(it), Provenance.LIBRARY) }
            val channels =
                header?.channels?.let { Fact(MediaFormats.channelsText(it), Provenance.FILE_HEADER) }
                    ?: extractor?.channels?.let { Fact(MediaFormats.channelsText(it), Provenance.CONTAINER) }
            val lossless = MediaFormats.isLossless(header?.codec ?: codecFromContainer)
            val bitDepth = header?.bitsPerSample?.let { Fact("$it-bit", Provenance.FILE_HEADER, "Stored in the file") }
            val bitDepthNote =
                when {
                    bitDepth != null -> null
                    !lossless -> "Lossy formats have no fixed bit depth"
                    else -> "Not stated in a header the app can read"
                }
            val bitrate =
                header?.firstFrameBitrateKbps?.let {
                    Fact(
                        "$it kbps",
                        Provenance.FILE_HEADER,
                        if (header.bitrateMode ==
                            "VBR"
                        ) {
                            "First frame only; varies across the track"
                        } else {
                            null
                        },
                    )
                } ?: extractor?.bitrate?.let { Fact(MediaFormats.bitrateText(it), Provenance.CONTAINER, "Nominal / average") }
                    ?: song.bitrate.takeIf { it > 0 }?.let { Fact(MediaFormats.bitrateText(it), Provenance.LIBRARY, "Average") }
            val duration =
                extractor?.durationUs?.let { Fact(formatDuration(it / US_PER_MS), Provenance.CONTAINER) }
                    ?: song.durationMs.takeIf { it > 0 }?.let { Fact(formatDuration(it), Provenance.LIBRARY) }
            return SourceDiagnostics(
                title = song.title,
                artist = song.artist,
                album = song.album,
                container = container,
                codec = codec,
                sampleRate = sampleRate,
                channels = channels,
                bitDepth = bitDepth,
                bitDepthNote = bitDepthNote,
                bitrate = bitrate,
                bitrateMode = header?.bitrateMode?.let { Fact(it, Provenance.FILE_HEADER) },
                duration = duration,
                fileSize = size?.let { Fact(formatSize(it), Provenance.LIBRARY) },
                problem = problem,
            )
        }

        private fun readPrefix(uri: Uri): ByteArray? =
            context.contentResolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArray(HEADER_BYTES)
                var total = 0
                while (total < buffer.size) {
                    val n = input.read(buffer, total, buffer.size - total)
                    if (n < 0) break
                    total += n
                }
                buffer.copyOf(total)
            }

        /** Facts from Android's extractor; null when it cannot open the file (inspect() also guards). */
        private fun extract(uri: Uri): ExtractorFacts? {
            val extractor = MediaExtractor()
            return try {
                extractor.setDataSource(context, uri, null)
                (0 until extractor.trackCount)
                    .map(extractor::getTrackFormat)
                    .firstOrNull { it.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
                    ?.let(::factsOf)
            } catch (ignored: IOException) {
                null
            } finally {
                extractor.release()
            }
        }

        private fun factsOf(format: MediaFormat): ExtractorFacts {
            fun int(key: String) = if (format.containsKey(key)) format.getInteger(key).takeIf { it > 0 } else null
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
            return ExtractorFacts(
                mime = format.getString(MediaFormat.KEY_MIME),
                sampleRate = int(MediaFormat.KEY_SAMPLE_RATE),
                channels = int(MediaFormat.KEY_CHANNEL_COUNT),
                bitrate = int(MediaFormat.KEY_BIT_RATE),
                durationUs = durationUs.takeIf { it > 0 },
            )
        }

        private fun mediaStore(uri: Uri): Pair<Long?, String?>? =
            context.contentResolver
                .query(uri, arrayOf(MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.MIME_TYPE), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getLong(0).takeIf { it > 0 } to c.getString(1) else null }

        companion object {
            private const val HEADER_BYTES = 256 * 1024
            private const val US_PER_MS = 1_000L
            private const val MS_PER_S = 1_000L
            private const val S_PER_MIN = 60
            private const val BYTES_PER_MB = 1024.0 * 1024.0

            private val CONTAINERS_BY_MIME =
                mapOf(
                    "audio/flac" to "FLAC",
                    "audio/mpeg" to "MPEG audio (MP3)",
                    "audio/mp4" to "MPEG-4 (M4A)",
                    "audio/x-m4a" to "MPEG-4 (M4A)",
                    "audio/m4a" to "MPEG-4 (M4A)",
                    "audio/aac" to "ADTS AAC",
                    "audio/ogg" to "Ogg",
                    "audio/wav" to "WAV (RIFF)",
                    "audio/x-wav" to "WAV (RIFF)",
                )
            private val CONTAINERS_BY_EXTENSION =
                mapOf(
                    "flac" to "FLAC",
                    "mp3" to "MPEG audio (MP3)",
                    "m4a" to "MPEG-4 (M4A)",
                    "mp4" to "MPEG-4 (M4A)",
                    "aac" to "ADTS AAC",
                    "ogg" to "Ogg",
                    "oga" to "Ogg",
                    "opus" to "Ogg",
                    "wav" to "WAV (RIFF)",
                )

            /** Container from the MIME type the library recorded, or the file extension. */
            fun containerOf(
                mime: String?,
                pathOrUri: String,
            ): String? {
                val ext = pathOrUri.substringAfterLast('/').substringAfterLast('.', "").lowercase(Locale.ROOT)
                // The MIME type the library recorded wins; the extension is the fallback.
                return CONTAINERS_BY_MIME[mime] ?: CONTAINERS_BY_EXTENSION[ext] ?: mime
            }

            fun formatDuration(ms: Long): String {
                val total = ms / MS_PER_S
                return String.format(Locale.ROOT, "%d:%02d", total / S_PER_MIN, total % S_PER_MIN)
            }

            fun formatSize(bytes: Long): String = String.format(Locale.ROOT, "%.1f MB", bytes / BYTES_PER_MB)
        }
    }
