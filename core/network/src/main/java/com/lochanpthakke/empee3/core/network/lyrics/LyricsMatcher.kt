package com.lochanpthakke.empee3.core.network.lyrics

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

/** How a result compares to the track. [rejected] results are a different recording and never shown as a match. */
data class MatchScore(
    val score: Double,
    val confident: Boolean,
    val rejected: Boolean,
    val reason: String? = null,
)

/**
 * Decides whether an online result is the lyrics of this track. Harmless differences are ignored
 * (case, accents, punctuation, "feat." credits, "Remastered" notes, "&" vs "and"); version
 * markers that change the recording (live, remix, acoustic, cover, sped up…) must agree, and a
 * duration more than [REJECT_DURATION_SEC] away is a different recording. Only a confident match is
 * used automatically; anything else is offered to the user to choose.
 */
object LyricsMatcher {
    /** Markers of a different recording: must match on both sides. */
    private val STRICT_TAGS =
        mapOf(
            "live" to Regex("""\blive\b"""),
            "remix" to Regex("""\b(remix|rmx|bootleg|edit mix)\b"""),
            "acoustic" to Regex("""\b(acoustic|unplugged)\b"""),
            "instrumental" to Regex("""\binstrumental\b"""),
            "karaoke" to Regex("""\bkaraoke\b"""),
            "cover" to Regex("""\bcover\b"""),
            "demo" to Regex("""\bdemo\b"""),
            "sped up" to Regex("""\b(sped up|speed up|nightcore)\b"""),
            "slowed" to Regex("""\b(slowed|reverb)\b"""),
            "reprise" to Regex("""\breprise\b"""),
        )

    /** Markers that usually keep the same words: a mismatch only lowers the score. */
    private val SOFT_TAGS =
        mapOf(
            "clean" to Regex("""\b(clean|radio edit|censored)\b"""),
            "explicit" to Regex("""\bexplicit\b"""),
            "extended" to Regex("""\bextended\b"""),
        )

    private val BRACKETED = Regex("""[(\[][^)\]]*[)\]]""")
    private val DASH_SUFFIX = Regex("""\s+-\s+.*$""")
    private val FEATURING = Regex("""\b(feat|ft|featuring)\b.*$""")
    private val ARTIST_SPLIT = Regex("""\s*(,|&|;|/|\bfeat\.?|\bft\.?|\bfeaturing\b|\band\b|\bx\b)\s*""")
    private val NON_WORD = Regex("""[^\p{L}\p{N}]+""")
    private val ACCENTS = Regex("""\p{Mn}+""")

    const val CONFIDENT_SCORE = 0.85
    const val PLAUSIBLE_SCORE = 0.5
    private const val CLOSE_DURATION_SEC = 2.0
    private const val NEAR_DURATION_SEC = 5.0
    private const val FAR_DURATION_SEC = 10.0
    const val REJECT_DURATION_SEC = 10.0
    private const val TITLE_WEIGHT = 0.45
    private const val ARTIST_WEIGHT = 0.35
    private const val DURATION_WEIGHT = 0.15
    private const val ALBUM_WEIGHT = 0.05
    private const val STRONG = 0.8
    private const val CONTAINED = 0.8
    private const val PARTIAL_ARTIST = 0.6
    private const val UNKNOWN = 0.5
    private const val NEAR = 0.6
    private const val FAR = 0.2
    private const val SOFT_PENALTY = 0.1
    private const val MIN_CONTAINED_LENGTH = 4
    private const val MS_PER_SECOND = 1_000.0

    @Suppress("ReturnCount")
    fun score(
        query: LyricsQuery,
        candidate: LyricsCandidate,
    ): MatchScore {
        val queryTags = tags(query.title, query.album)
        val candidateTags = tags(candidate.trackName, candidate.albumName)
        val strictDiff = STRICT_TAGS.keys.filter { (it in queryTags) != (it in candidateTags) }
        if (strictDiff.isNotEmpty()) {
            return MatchScore(
                0.0,
                confident = false,
                rejected = true,
                reason = "different version (${strictDiff.first()})",
            )
        }
        val queryDuration = query.durationMs.takeIf { it > 0 }?.div(MS_PER_SECOND)
        val durationDiff = if (queryDuration != null && candidate.durationSec != null) abs(queryDuration - candidate.durationSec) else null
        if (durationDiff != null && durationDiff > REJECT_DURATION_SEC) {
            return MatchScore(0.0, confident = false, rejected = true, reason = "different length")
        }
        val title = textSimilarity(baseTitle(query.title), baseTitle(candidate.trackName))
        val artist = artistSimilarity(query.artist, candidate.artistName)
        val duration = durationScore(durationDiff)
        val album = albumScore(query.album, candidate.albumName)
        val softPenalty = SOFT_TAGS.keys.count { (it in queryTags) != (it in candidateTags) } * SOFT_PENALTY
        val total =
            (TITLE_WEIGHT * title + ARTIST_WEIGHT * artist + DURATION_WEIGHT * duration + ALBUM_WEIGHT * album - softPenalty)
                .coerceIn(
                    0.0,
                    1.0,
                )
        // Without a known duration, only an exact title and artist are trusted on their own.
        val durationOk = if (durationDiff == null) title == 1.0 && artist == 1.0 else duration >= NEAR
        val confident = total >= CONFIDENT_SCORE && title >= STRONG && artist >= STRONG && durationOk
        return MatchScore(total, confident, rejected = false)
    }

    private fun durationScore(diffSec: Double?): Double =
        when {
            diffSec == null -> UNKNOWN
            diffSec <= CLOSE_DURATION_SEC -> 1.0
            diffSec <= NEAR_DURATION_SEC -> NEAR
            diffSec <= FAR_DURATION_SEC -> FAR
            else -> 0.0
        }

    private fun albumScore(
        query: String?,
        candidate: String?,
    ): Double =
        when {
            query.isNullOrBlank() || candidate.isNullOrBlank() -> UNKNOWN
            baseTitle(query) == baseTitle(candidate) -> 1.0
            else -> 0.0
        }

    /** Version markers found in a title and album, normalised. */
    fun tags(
        title: String,
        album: String?,
    ): Set<String> {
        val text = fold("$title ${album.orEmpty()}")
        return (STRICT_TAGS + SOFT_TAGS).filterValues { it.containsMatchIn(text) }.keys
    }

    /** The title without bracketed notes, " - …" suffixes or credits, folded for comparison. */
    fun baseTitle(title: String): String {
        val withoutNotes = title.replace(BRACKETED, " ").replace(DASH_SUFFIX, "")
        return normalize(fold(withoutNotes).replace(FEATURING, ""))
    }

    private fun artistSimilarity(
        query: String,
        candidate: String,
    ): Double {
        val a = artists(query)
        val b = artists(candidate)
        if (a.isNotEmpty() && a.intersect(b).isNotEmpty()) return 1.0
        // A name inside another ("Someone" in "Someone Else") is a different artist: never confident.
        return textSimilarity(a.joinToString(" "), b.joinToString(" ")).coerceAtMost(PARTIAL_ARTIST)
    }

    private fun artists(text: String): Set<String> =
        fold(text)
            .split(ARTIST_SPLIT)
            .map { normalize(it).removePrefix("the ") }
            .filter { it.isNotEmpty() }
            .toSet()

    @Suppress("ReturnCount") // one return per similarity level
    private fun textSimilarity(
        a: String,
        b: String,
    ): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (a == b) return 1.0
        if (minOf(a.length, b.length) >= MIN_CONTAINED_LENGTH && (a.contains(b) || b.contains(a))) return CONTAINED
        val ta = a.split(' ').toSet()
        val tb = b.split(' ').toSet()
        return ta.intersect(tb).size.toDouble() / ta.union(tb).size
    }

    /** Lower case, no accents, "&" as "and". */
    private fun fold(text: String): String =
        Normalizer
            .normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFKD)
            .replace(ACCENTS, "")
            .replace("&", " and ")

    private fun normalize(text: String): String = text.replace(NON_WORD, " ").trim()

    /**
     * The result to use automatically: among confident matches, genuine word timing first, then line
     * timing, then plain; ties by score. Null when no result is confident.
     */
    fun best(
        query: LyricsQuery,
        candidates: List<LyricsCandidate>,
    ): LyricsCandidate? =
        candidates
            .map { it to score(query, it) }
            .filter { (_, match) -> match.confident }
            .maxWithOrNull(compareBy<Pair<LyricsCandidate, MatchScore>>({ it.first.timing.ordinal }, { it.second.score }))
            ?.first

    /** Results worth offering when nothing is confident: not rejected, plausible, best first. */
    fun plausible(
        query: LyricsQuery,
        candidates: List<LyricsCandidate>,
        limit: Int = 8,
    ): List<LyricsCandidate> =
        candidates
            .map { it to score(query, it) }
            .filter { (_, match) -> !match.rejected && match.score >= PLAUSIBLE_SCORE }
            .sortedByDescending { it.second.score }
            .take(limit)
            .map { it.first }
}
