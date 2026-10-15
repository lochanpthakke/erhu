package com.lochanpthakke.empee3.core.model

/**
 * A track's genres as one stored string, and back.
 *
 * The library (Room) and the tag-edit pipeline keep a track's genres in one string joined with
 * [SEPARATOR] ("Electronic; Synth-pop; Alternative"), so no schema change was needed. Tags keep
 * them the way their format allows (see the tag writer): separate values where the format has
 * them (ID3v2.4, Vorbis comments), this joined string where it doesn't (ID3v2.3, WAV, MP4).
 * Reading always splits on ';' (and on the NUL separator of multi-value ID3 frames), so either
 * form comes back as the same list. '/' and '&' are never split: "Hip Hop/Rap" and "R&B" are
 * single genres.
 */
object Genres {
    const val SEPARATOR = "; "

    /** Longer than this is not a genre name but junk (a comment or a URL in the wrong field). */
    private const val MAX_LENGTH = 64

    private val SPLIT = Regex("""[;\u0000]""")
    private val WHITESPACE = Regex("""\s+""")
    private val PLACEHOLDERS = setOf("unknown", "unknown genre", "<unknown>", "genre", "none", "n/a")

    /**
     * Usable, distinct genres in their original order: whitespace trimmed and collapsed, blanks,
     * placeholders and junk dropped, and duplicates (ignoring case) kept once.
     */
    fun normalize(values: Iterable<String?>): List<String> {
        val seen = HashSet<String>()
        return values
            .asSequence()
            .filterNotNull()
            .map { it.replace(WHITESPACE, " ").trim() }
            .filter { isUsable(it) }
            .filter { seen.add(it.lowercase()) }
            .toList()
    }

    /** The genres in a stored or tag value; empty when there are none. */
    fun parse(stored: String?): List<String> = normalize(stored?.split(SPLIT).orEmpty())

    /** The stored form of [genres], or null when none is usable. */
    fun join(genres: Iterable<String?>): String? = normalize(genres).takeIf { it.isNotEmpty() }?.joinToString(SEPARATOR)

    /** True when [a] and [b] hold the same genres in the same order (ignoring case and spacing). */
    fun same(
        a: String?,
        b: String?,
    ): Boolean = parse(a).map { it.lowercase() } == parse(b).map { it.lowercase() }

    private fun isUsable(genre: String): Boolean =
        genre.isNotEmpty() &&
            genre.length <= MAX_LENGTH &&
            genre.lowercase() !in PLACEHOLDERS &&
            genre.none { it.isISOControl() } &&
            genre.any { it.isLetter() }
}
