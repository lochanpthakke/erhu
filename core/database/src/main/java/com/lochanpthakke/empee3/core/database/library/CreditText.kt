package com.lochanpthakke.empee3.core.database.library

import java.text.Normalizer
import java.util.Locale

/** A piece of raw credit text between candidate separators; [start]/[end] index the owning run's raw text. */
data class Segment(
    val text: String,
    val key: String,
    val start: Int,
    val end: Int,
)

/** Raw credit text on one side of an artist-text feat marker. */
data class CreditRun(
    val raw: String,
    val segments: List<Segment>,
    val featured: Boolean,
)

/**
 * Text utilities for artist credits. Separators found here are only CANDIDATE
 * boundaries: nothing in this file decides whether text is one artist or several.
 */
object CreditText {
    private val WHITESPACE = Regex("\\s+")
    private val CANDIDATE_SEPARATOR = Regex("\\s*[/;,&|+]\\s*|\\s+(?:and|x)\\s+", RegexOption.IGNORE_CASE)
    private val ARTIST_FEAT_MARKER =
        Regex("\\s+(?:feat\\.?|ft\\.|featuring)\\s+|\\s*[(\\[]\\s*(?:feat\\.?|ft\\.|featuring|with)\\s+", RegexOption.IGNORE_CASE)
    private val BLOCK_NOISE = Regex("[\\s/;,&|+]+")
    private const val TRIM_CHARS = " ()[]"

    fun canonicalKey(text: String): String =
        Normalizer
            .normalize(text, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .replace(WHITESPACE, " ")
            .trim()

    /** Key for comparing a whole marker block with credit text: separators are ignored, nothing is split. */
    fun blockKey(text: String): String = canonicalKey(text).replace(BLOCK_NOISE, " ").trim()

    fun runs(raw: String): List<CreditRun> {
        val marker = ARTIST_FEAT_MARKER.find(raw)
        val pieces =
            if (marker == null) {
                listOf(raw to false)
            } else {
                listOf(raw.substring(0, marker.range.first) to false, raw.substring(marker.range.last + 1) to true)
            }
        return pieces.mapNotNull { (text, featured) ->
            val segments = segmentsOf(text)
            if (segments.isEmpty()) null else CreditRun(text, segments, featured)
        }
    }

    fun segmentsOf(text: String): List<Segment> {
        val out = mutableListOf<Segment>()
        var cursor = 0
        for (separator in CANDIDATE_SEPARATOR.findAll(text)) {
            addSegment(text, cursor, separator.range.first, out)
            cursor = separator.range.last + 1
        }
        addSegment(text, cursor, text.length, out)
        return out
    }

    private fun addSegment(
        text: String,
        from: Int,
        to: Int,
        out: MutableList<Segment>,
    ) {
        var start = from
        var end = to
        while (start < end && text[start] in TRIM_CHARS) start++
        while (end > start && text[end - 1] in TRIM_CHARS) end--
        if (start == end) return
        val piece = text.substring(start, end)
        val key = canonicalKey(piece)
        if (out.lastOrNull()?.key == key) return // the same credit repeated back to back
        out += Segment(piece, key, start, end)
    }
}
