package com.erhu.erhu.feature.transfer.server

import java.util.Locale

/**
 * File names and formats accepted by Wi-Fi Transfer. The browser only ever supplies a display
 * name: any folder part is discarded, so a client can never choose where a file is written.
 */
object TransferFiles {
    /** Ordinary local audio formats Android plays (no conversion is done). */
    val AUDIO_EXTENSIONS: Set<String> = setOf("mp3", "flac", "m4a", "aac", "ogg", "oga", "opus", "wav")

    private const val MAX_BASE_LENGTH = 120
    private val RESERVED = Regex("""[<>:"/\\|?*\u0000-\u001F\u007F]""")
    private val WHITESPACE = Regex("""\s+""")

    /**
     * A safe file name for [raw] (last path segment only, no control or reserved characters, no
     * leading dots, at most [MAX_BASE_LENGTH] characters before the extension), or null when nothing
     * usable is left or the extension is not a supported audio format.
     */
    fun sanitize(raw: String?): String? {
        val cleaned =
            raw
                ?.substringAfterLast('/')
                ?.substringAfterLast('\\')
                ?.replace(RESERVED, "_")
                ?.replace(WHITESPACE, " ")
                ?.trim()
                ?.trimStart('.', ' ')
                ?.trimEnd('.', ' ')
                .orEmpty()
        val dot = cleaned.lastIndexOf('.')
        if (dot <= 0 || dot == cleaned.lastIndex) return null
        val extension = cleaned.substring(dot + 1).lowercase(Locale.ROOT)
        val base =
            cleaned
                .substring(0, dot)
                .trimEnd('.', ' ')
                .take(MAX_BASE_LENGTH)
                .trimEnd('.', ' ')
        val usableBase = base.isNotEmpty() && base != "." && base != ".."
        return if (extension in AUDIO_EXTENSIONS && usableBase) "$base.$extension" else null
    }

    fun extensionOf(name: String): String = name.substringAfterLast('.', "").lowercase(Locale.ROOT)

    /** "Song (2).mp3" for [name] "Song.mp3" and [n] 2: the predictable name used on a collision. */
    fun numbered(
        name: String,
        n: Int,
    ): String {
        val dot = name.lastIndexOf('.')
        return if (dot <= 0) "$name ($n)" else "${name.substring(0, dot)} ($n)${name.substring(dot)}"
    }

    /** MIME type from the (already validated) extension; the browser's claim is never used. */
    fun mimeTypeOf(name: String): String =
        when (extensionOf(name)) {
            "mp3" -> "audio/mpeg"
            "flac" -> "audio/flac"
            "m4a" -> "audio/mp4"
            "aac" -> "audio/aac"
            "ogg", "oga" -> "audio/ogg"
            "opus" -> "audio/opus"
            "wav" -> "audio/wav"
            else -> "application/octet-stream"
        }

    /** Bytes needed by [looksLikeAudio]. */
    const val SIGNATURE_BYTES = 12

    /**
     * Whether the first bytes of the upload match the format its extension claims (MP3 frame or
     * ID3 tag, FLAC, MP4 "ftyp", ADTS AAC, Ogg, RIFF/WAVE). A renamed non-audio file is refused.
     */
    @Suppress("MagicNumber", "CyclomaticComplexMethod")
    fun looksLikeAudio(
        name: String,
        header: ByteArray,
        length: Int,
    ): Boolean {
        if (length < 4) return false

        fun at(i: Int) = if (i < length) header[i].toInt() and 0xFF else -1

        fun ascii(
            offset: Int,
            text: String,
        ) = offset + text.length <= length && text.indices.all { at(offset + it) == text[it].code }
        val id3 = ascii(0, "ID3")
        val frameSync = at(0) == 0xFF && (at(1) and 0xE0) == 0xE0
        return when (extensionOf(name)) {
            "mp3" -> id3 || frameSync
            "flac" -> ascii(0, "fLaC") || id3
            "m4a" -> ascii(4, "ftyp")
            "aac" -> (at(0) == 0xFF && (at(1) and 0xF6) == 0xF0) || ascii(0, "ADIF") || id3
            "ogg", "oga", "opus" -> ascii(0, "OggS")
            "wav" -> ascii(0, "RIFF") && ascii(8, "WAVE")
            else -> false
        }
    }
}
