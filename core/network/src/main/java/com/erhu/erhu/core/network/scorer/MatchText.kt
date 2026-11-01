package com.erhu.erhu.core.network.scorer

/*
 * Text handling for searching and matching tracks, from what MusicBrainz actually does with
 * queries (checked live 2026-10-14):
 * - a featured-artist note in a title makes a phrase search miss: `recording:"911 / Mr. Lonely
 *   (feat. Frank Ocean & Steve Lacy)"` finds nothing, `recording:"911 / Mr. Lonely"` finds the song;
 * - a multi-artist credit flattened by a downloader ("Tyler/ The Creator/Frank Ocean/ Steve Lacy")
 *   can't be split back into names (the "/" replaced the comma inside "Tyler, The Creator" too) and
 *   never matches as a phrase, while its words do: `artist:(Tyler The Creator Frank Ocean Steve Lacy)`.
 */

/** "(feat. X)", "[ft. X]" or a trailing "feat. X", including a note cut off before its ")". */
private val FEATURING_NOTE = Regex("""\s*[(\[]\s*(?:feat\.?|ft\.?|featuring)\s[^)\]]*[)\]]?""", RegexOption.IGNORE_CASE)
private val TRAILING_FEATURING = Regex("""\s+(?:feat\.?|ft\.?|featuring)\s.*$""", RegexOption.IGNORE_CASE)

/** [title] without its featured-artist note: "911 / Mr. Lonely (feat. Frank Ocean & Steve Lacy)" -> "911 / Mr. Lonely". */
fun stripFeaturing(title: String): String = title.replace(FEATURING_NOTE, "").replace(TRAILING_FEATURING, "").trim()

/** Words that join names in a credit rather than being part of one. */
private val CREDIT_CONNECTORS = setOf("feat", "ft", "featuring", "and", "with", "x", "vs")

/** The words of an artist credit, joiners dropped: "Tyler, The Creator ft. Frank Ocean & Steve Lacy" -> {tyler, the, creator, frank, …}. */
fun creditWords(credit: String): Set<String> =
    foldForMatch(credit)
        .split(' ')
        .filter { it.isNotEmpty() && it !in CREDIT_CONNECTORS }
        .toSet()

/**
 * How alike two artist credits are by the names they contain, ignoring how they are joined:
 * the downloader's "Tyler/ The Creator/Frank Ocean/ Steve Lacy" and MusicBrainz's "Tyler, The
 * Creator ft. Frank Ocean & Steve Lacy" are the same credit (1.0). Jaccard similarity of the words.
 */
fun creditSimilarity(
    a: String,
    b: String,
): Float {
    val x = creditWords(a)
    val y = creditWords(b)
    if (x.isEmpty() || y.isEmpty()) return 0f
    return (x intersect y).size.toFloat() / (x union y).size
}

/** The parts of a two-sided title: "911 / Mr. Lonely" -> ["911", "Mr. Lonely"]. Single titles give themselves. */
fun titleSides(title: String): List<String> = title.split(" / ").map { it.trim() }.filter { it.isNotEmpty() }

/** Marks of a special mix of the same recording: not wrong, but not the plain version a file usually holds. */
private val SPECIAL_MIX = Regex("""\b(dolby atmos|atmos|spatial audio|360 reality audio|binaural|surround)\b""", RegexOption.IGNORE_CASE)

fun isSpecialMix(text: String?): Boolean = text != null && SPECIAL_MIX.containsMatchIn(text)

/** An artist credit as search words for MusicBrainz's `artist:(…)`: no joiners, no punctuation. */
fun artistSearchTerms(credit: String?): String? =
    credit
        ?.let { creditWords(it) }
        ?.takeIf { it.isNotEmpty() }
        ?.joinToString(" ")

/** [text] safe inside a quoted Lucene phrase (MusicBrainz search syntax): backslashes and quotes escaped. */
fun escapePhrase(text: String): String = text.replace("\\", "\\\\").replace("\"", "\\\"")
