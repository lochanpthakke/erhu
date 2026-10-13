package com.lochanpthakke.empee3.core.common.text

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * UTF-8 text that was decoded as Windows-1252 ("mojibake"): "IGOR’S THEME" read as
 * "IGORâ€™S THEME". Android's media scanner does this to some correctly tagged files: its
 * charset guess depends on the string (observed 2026-10-13 on a Galaxy S23, Android 16, for a
 * UTF-16 ID3v2.3 title "IGOR’S THEME"; "WHAT’S GOOD" in an identical tag was read correctly).
 *
 * Nothing here guesses: [misreadAsWindows1252] reproduces the error exactly, so a caller can
 * prove that a value is the misreading of a known-good one, and [repair] only undoes a misreading
 * when the bytes are strict UTF-8. Legitimate text ("São Paulo", "Ångström", "Ðорт", emoji,
 * a lone "â") is never changed, and repairing repaired text changes nothing.
 */
object MojibakeText {
    /** A UTF-8 lead byte (Â–ï as Windows-1252) followed by a continuation byte, as Windows-1252 shows them. */
    private val SIGNATURE =
        Regex(
            "[Â-ï][\u0080-¿ŒœŠšŸŽžƒˆ˜" +
                "–—‘-„†-•…‰‹›€™]",
        )

    /**
     * What Windows-1252 shows for bytes 0x80–0x9F, in order. The five bytes it leaves undefined
     * (0x81, 0x8D, 0x8F, 0x90, 0x9D) stay as the matching C1 control characters, as Android shows them.
     */
    private const val CP1252_C1 =
        "\u20ac\u0081\u201a\u0192\u201e\u2026\u2020\u2021\u02c6\u2030\u0160\u2039\u0152\u008d\u017d\u008f" +
            "\u0090\u2018\u2019\u201c\u201d\u2022\u2013\u2014\u02dc\u2122\u0161\u203a\u0153\u009d\u017e\u0178"

    private const val C1_FIRST = 0x80
    private const val C1_LAST = 0x9F
    private const val BYTE_MASK = 0xFF

    /** The byte each character of [CP1252_C1] came from. */
    private val CP1252_REVERSE: Map<Char, Int> = CP1252_C1.withIndex().associate { (i, char) -> char to C1_FIRST + i }

    /** True when [text] contains the byte pattern UTF-8 leaves behind when read as Windows-1252. */
    fun looksMisread(text: String?): Boolean = text != null && SIGNATURE.containsMatchIn(text)

    /** [text] as it looks after its UTF-8 bytes are misread as Windows-1252. ASCII text is returned unchanged. */
    fun misreadAsWindows1252(text: String): String =
        buildString {
            for (byte in text.toByteArray(StandardCharsets.UTF_8)) {
                val b = byte.toInt() and BYTE_MASK
                append(if (b in C1_FIRST..C1_LAST) CP1252_C1[b - C1_FIRST] else b.toChar())
            }
        }

    /**
     * The original text if [text] is a Windows-1252 misreading of valid UTF-8, else null: it must
     * carry the misreading's byte pattern, map back to Windows-1252 bytes exactly, and those bytes
     * must be strict UTF-8 that differs from the input. Repairing a repaired string gives null.
     */
    @Suppress("ReturnCount")
    fun repair(text: String?): String? {
        if (text == null || !looksMisread(text)) return null
        val bytes = ByteArray(text.length)
        for ((i, char) in text.withIndex()) {
            bytes[i] =
                when {
                    char.code < C1_FIRST -> char.code
                    char.code in (C1_LAST + 1)..BYTE_MASK -> char.code
                    else -> CP1252_REVERSE[char] ?: return null
                }.toByte()
        }
        val decoded =
            try {
                StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString()
            } catch (_: CharacterCodingException) {
                return null
            }
        return decoded.takeIf { it != text }
    }

    /**
     * The value to keep when an index (MediaStore) reports [indexed] for a file whose own tag reads
     * [fromTag]: the tag's text when [indexed] is exactly its Windows-1252 misreading, else [indexed].
     * [fromTag] is only read (lazily) when [indexed] looks misread.
     */
    fun correctedFromTag(
        indexed: String,
        fromTag: () -> String?,
    ): String {
        if (!looksMisread(indexed)) return indexed
        val tagText = fromTag()
        val proven = !tagText.isNullOrBlank() && tagText != indexed && misreadAsWindows1252(tagText) == indexed
        return if (proven && tagText != null) tagText else indexed
    }
}
