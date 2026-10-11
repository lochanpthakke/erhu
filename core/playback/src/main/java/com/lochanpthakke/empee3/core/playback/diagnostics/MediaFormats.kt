package com.lochanpthakke.empee3.core.playback.diagnostics

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import java.util.Locale

/** Readable names for Media3 / Android audio constants. Unknown values stay unknown. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object MediaFormats {
    /** Media3 [Format] to a summary; Format.NO_VALUE becomes null (unknown), a real 0 is never invented. */
    fun summarize(format: Format): FormatSummary =
        FormatSummary(
            sampleMimeType = format.sampleMimeType,
            codecs = format.codecs,
            sampleRateHz = format.sampleRate.known(),
            channelCount = format.channelCount.known(),
            pcmEncoding = format.pcmEncoding.takeIf { it != Format.NO_VALUE && it != C.ENCODING_INVALID }?.let(::encodingName),
            averageBitrate = format.averageBitrate.known(),
            peakBitrate = format.peakBitrate.known(),
        )

    private fun Int.known(): Int? = takeIf { it != Format.NO_VALUE && it > 0 }

    fun encodingName(encoding: Int): String =
        when (encoding) {
            C.ENCODING_PCM_8BIT -> "PCM 8-bit"
            C.ENCODING_PCM_16BIT, C.ENCODING_PCM_16BIT_BIG_ENDIAN -> "PCM 16-bit"
            C.ENCODING_PCM_24BIT, C.ENCODING_PCM_24BIT_BIG_ENDIAN -> "PCM 24-bit"
            C.ENCODING_PCM_32BIT, C.ENCODING_PCM_32BIT_BIG_ENDIAN -> "PCM 32-bit"
            C.ENCODING_PCM_FLOAT -> "PCM 32-bit float"
            C.ENCODING_AC3 -> "AC-3 (passthrough)"
            C.ENCODING_E_AC3 -> "E-AC-3 (passthrough)"
            C.ENCODING_DTS -> "DTS (passthrough)"
            C.ENCODING_MP3 -> "MP3 (offloaded)"
            C.ENCODING_AAC_LC -> "AAC (offloaded)"
            C.ENCODING_OPUS -> "Opus (offloaded)"
            else -> "Encoding $encoding"
        }

    /** Codec name for a sample MIME type, e.g. audio/flac to FLAC. */
    @Suppress("CyclomaticComplexMethod")
    fun codecName(mime: String?): String? =
        when (mime) {
            null -> null
            MimeTypes.AUDIO_FLAC -> "FLAC"
            MimeTypes.AUDIO_MPEG -> "MP3"
            MimeTypes.AUDIO_AAC -> "AAC"
            MimeTypes.AUDIO_ALAC -> "ALAC"
            MimeTypes.AUDIO_OPUS -> "Opus"
            MimeTypes.AUDIO_VORBIS -> "Vorbis"
            MimeTypes.AUDIO_RAW, MimeTypes.AUDIO_WAV -> "PCM"
            MimeTypes.AUDIO_AC3 -> "AC-3"
            MimeTypes.AUDIO_E_AC3 -> "E-AC-3"
            else -> mime.removePrefix("audio/").uppercase(Locale.ROOT)
        }

    /** Whether a codec is lossless (so its stored bit depth is meaningful). */
    fun isLossless(codec: String?): Boolean = codec in setOf("FLAC", "ALAC", "PCM")

    /** Common names for channel counts; null for counts without a standard layout name. */
    fun channelLayout(count: Int?): String? =
        when (count) {
            1 -> "Mono"
            2 -> "Stereo"
            SURROUND_5_1 -> "5.1"
            SURROUND_7_1 -> "7.1"
            else -> null
        }

    /** Number of channels in an Android AudioFormat channel-out mask (one bit per channel). */
    fun channelsInMask(mask: Int): Int? = Integer.bitCount(mask).takeIf { it > 0 }

    fun sampleRateText(hz: Int): String =
        if (hz % PER_KILO == 0) "${hz / PER_KILO} kHz" else String.format(Locale.ROOT, "%.1f kHz", hz.toDouble() / PER_KILO)

    fun bitrateText(bitsPerSecond: Int): String = "${(bitsPerSecond + PER_KILO / 2) / PER_KILO} kbps"

    fun channelsText(count: Int): String = channelLayout(count)?.let { "$count ($it)" } ?: "$count"

    private const val SURROUND_5_1 = 6
    private const val SURROUND_7_1 = 8
    private const val PER_KILO = 1000
}
