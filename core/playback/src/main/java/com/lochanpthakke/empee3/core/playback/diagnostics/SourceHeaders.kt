package com.lochanpthakke.empee3.core.playback.diagnostics

/** What a file's own audio header states (never inferred from decoded output). Null fields: not stated. */
data class SourceHeader(
    val codec: String,
    val sampleRateHz: Int? = null,
    val channels: Int? = null,
    /** Bits per sample stored in the file (FLAC, WAV). */
    val bitsPerSample: Int? = null,
    val totalSamples: Long? = null,
    /** Bitrate of the first audio frame (MP3); for VBR files it is not the bitrate of the whole track. */
    val firstFrameBitrateKbps: Int? = null,
    /** "VBR", "CBR" when the file says so (Xing / Info / VBRI tag), null when it does not. */
    val bitrateMode: String? = null,
)

/**
 * Small, bounded parsers for the headers that state technical facts directly. They read only the
 * given prefix of the file and return null for anything they do not recognise.
 *
 * Offsets, bit fields and tables follow the FLAC, RIFF/WAVE and MPEG audio specifications (hence the
 * suppressions); each parser bails out early on the first field that does not match.
 */
@Suppress("MagicNumber", "ReturnCount", "NestedBlockDepth", "ComplexCondition")
object SourceHeaders {
    fun parse(bytes: ByteArray): SourceHeader? {
        val start = skipId3(bytes)
        return parseFlac(bytes, start) ?: parseWav(bytes) ?: parseMp3(bytes, start)
    }

    /** Length of a leading ID3v2 tag (header + syncsafe size + footer), 0 if none. */
    fun skipId3(bytes: ByteArray): Int {
        if (bytes.size < 10 || !ascii(bytes, 0, "ID3")) return 0
        val size = (u8(bytes, 6) shl 21) or (u8(bytes, 7) shl 14) or (u8(bytes, 8) shl 7) or u8(bytes, 9)
        val footer = if (u8(bytes, 5) and 0x10 != 0) 10 else 0
        return 10 + size + footer
    }

    /** FLAC STREAMINFO (always the first metadata block). */
    fun parseFlac(
        bytes: ByteArray,
        start: Int = 0,
    ): SourceHeader? {
        if (!ascii(bytes, start, "fLaC")) return null
        val block = start + 4
        if (bytes.size < block + 4 + 18 || u8(bytes, block) and 0x7F != 0) return null
        val info = block + 4
        val sampleRate = (u8(bytes, info + 10) shl 12) or (u8(bytes, info + 11) shl 4) or (u8(bytes, info + 12) ushr 4)
        val channels = ((u8(bytes, info + 12) ushr 1) and 0x07) + 1
        val bits = (((u8(bytes, info + 12) and 0x01) shl 4) or (u8(bytes, info + 13) ushr 4)) + 1
        val total =
            ((u8(bytes, info + 13) and 0x0F).toLong() shl 32) or
                (u8(bytes, info + 14).toLong() shl 24) or
                (u8(bytes, info + 15).toLong() shl 16) or
                (u8(bytes, info + 16).toLong() shl 8) or
                u8(bytes, info + 17).toLong()
        return SourceHeader("FLAC", sampleRate.takeIf { it > 0 }, channels, bits, total.takeIf { it > 0 })
    }

    /** RIFF/WAVE "fmt " chunk (WAVE_FORMAT_EXTENSIBLE: the valid-bits field). */
    fun parseWav(bytes: ByteArray): SourceHeader? {
        if (!ascii(bytes, 0, "RIFF") || !ascii(bytes, 8, "WAVE")) return null
        var offset = 12
        while (offset + 8 <= bytes.size) {
            val size = le32(bytes, offset + 4)
            if (ascii(bytes, offset, "fmt ") && offset + 8 + 16 <= bytes.size) {
                val body = offset + 8
                val format = le16(bytes, body)
                val channels = le16(bytes, body + 2)
                val rate = le32(bytes, body + 4)
                var bits = le16(bytes, body + 14)
                if (format == WAVE_FORMAT_EXTENSIBLE && size >= 24 && body + 20 <= bytes.size) {
                    le16(bytes, body + 18).takeIf { it > 0 }?.let { bits = it }
                }
                val codec = if (format == WAVE_FORMAT_FLOAT) "PCM float" else "PCM"
                return SourceHeader(codec, rate.takeIf { it > 0 }, channels.takeIf { it > 0 }, bits.takeIf { it > 0 })
            }
            if (size < 0) return null
            offset += 8 + size + (size and 1)
        }
        return null
    }

    private val MPEG1_L3_KBPS = intArrayOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 0)
    private val MPEG2_L3_KBPS = intArrayOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, 0)
    private val MPEG1_RATES = intArrayOf(44_100, 48_000, 32_000)

    /** First MPEG audio layer III frame after any ID3 tag, and its Xing / Info / VBRI tag. */
    @Suppress("CyclomaticComplexMethod", "ReturnCount")
    fun parseMp3(
        bytes: ByteArray,
        start: Int = 0,
    ): SourceHeader? {
        var i = start
        val limit = minOf(bytes.size - 4, start + MP3_SEARCH_BYTES)
        while (i < limit) {
            if (u8(bytes, i) == 0xFF && (u8(bytes, i + 1) and 0xE0) == 0xE0) {
                val versionBits = (u8(bytes, i + 1) ushr 3) and 0x03
                val layerBits = (u8(bytes, i + 1) ushr 1) and 0x03
                val bitrateIndex = u8(bytes, i + 2) ushr 4
                val rateIndex = (u8(bytes, i + 2) ushr 2) and 0x03
                if (versionBits != 1 && layerBits == 1 && bitrateIndex in 1..14 && rateIndex < 3) {
                    val mpeg1 = versionBits == 3
                    val rate =
                        MPEG1_RATES[rateIndex] /
                            if (mpeg1) {
                                1
                            } else if (versionBits == 2) {
                                2
                            } else {
                                4
                            }
                    val kbps = if (mpeg1) MPEG1_L3_KBPS[bitrateIndex] else MPEG2_L3_KBPS[bitrateIndex]
                    val mono = (u8(bytes, i + 3) ushr 6) == 3
                    val sideInfo = if (mpeg1) (if (mono) 17 else 32) else (if (mono) 9 else 17)
                    val tag = i + 4 + sideInfo
                    val mode =
                        when {
                            ascii(bytes, tag, "Xing") -> "VBR"
                            ascii(bytes, tag, "Info") -> "CBR"
                            ascii(bytes, i + 36, "VBRI") -> "VBR"
                            else -> null
                        }
                    return SourceHeader("MP3", rate, if (mono) 1 else 2, firstFrameBitrateKbps = kbps, bitrateMode = mode)
                }
            }
            i++
        }
        return null
    }

    private const val WAVE_FORMAT_FLOAT = 3
    private const val WAVE_FORMAT_EXTENSIBLE = 0xFFFE
    private const val MP3_SEARCH_BYTES = 64 * 1024

    private fun u8(
        bytes: ByteArray,
        i: Int,
    ): Int = if (i in bytes.indices) bytes[i].toInt() and 0xFF else 0

    private fun le16(
        bytes: ByteArray,
        i: Int,
    ) = u8(bytes, i) or (u8(bytes, i + 1) shl 8)

    private fun le32(
        bytes: ByteArray,
        i: Int,
    ) = le16(bytes, i) or (le16(bytes, i + 2) shl 16)

    private fun ascii(
        bytes: ByteArray,
        offset: Int,
        text: String,
    ) = offset >= 0 && offset + text.length <= bytes.size && text.indices.all { bytes[offset + it].toInt() == text[it].code }
}
