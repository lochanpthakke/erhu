// Numeric bounds here describe token edit distance, catalog segmentation, BPM and calendar ranges.
@file:Suppress("MagicNumber")

package com.erhu.erhu.core.vybe

data class VybeConstraints(
    val minBpm: Int? = null,
    val maxBpm: Int? = null,
    val minYear: Int? = null,
    val maxYear: Int? = null,
    val excludedArtists: Set<String> = emptySet(),
    val excludedGenres: Set<String> = emptySet(),
    val descriptions: List<String> = emptyList(),
    val error: String? = null,
) {
    fun allows(
        song: SongText,
        index: VybeNameIndex,
    ): Boolean =
        error == null &&
            numericMatches(song.bpm?.takeIf { it in VybeText.PLAUSIBLE_BPM }, minBpm, maxBpm) &&
            numericMatches(song.year.takeIf { it > 0 }, minYear, maxYear) &&
            excludedArtists.none { index.isArtist(song.artist, it) } &&
            song.genres.none { genreKey(it) in excludedGenres }
}

data class VybeQueryIntent(
    val text: String,
    val constraints: VybeConstraints,
)

/** Explicit constraints are metadata facts. Missing BPM/year never satisfies a numeric filter. */
object VybeQueryIntentParser {
    @Suppress("LongMethod", "CyclomaticComplexMethod") // Rules consume explicit filters in precedence order; quoted names stay intact.
    fun parse(
        query: String,
        index: VybeNameIndex,
    ): VybeQueryIntent {
        var text = query.trim()
        var minBpm: Int? = null
        var maxBpm: Int? = null
        var minYear: Int? = null
        var maxYear: Int? = null
        val artists = mutableSetOf<String>()
        val genres = mutableSetOf<String>()
        val descriptions = mutableListOf<String>()
        var error: String? = null
        val catalogProtection = mutableMapOf<String, List<IntRange>>()

        fun extract(
            pattern: String,
            consume: (MatchResult) -> Unit,
        ) {
            val regex = Regex(pattern, RegexOption.IGNORE_CASE)
            // Quoted titles remain catalog names, even when they look like a filter.
            val quoted = Regex("\"[^\"]*\"|“[^”]*”").findAll(text).map { it.range }.toList()
            val catalog = catalogProtection.getOrPut(text) { VybeQueryRouter.catalogRanges(text, index) }
            val searchable = text.toCharArray()
            (quoted + catalog).forEach { range -> range.forEach { position -> searchable[position] = ' ' } }
            val matches = regex.findAll(String(searchable)).toList()
            matches.forEach {
                consume(it)
                descriptions += text.substring(it.range).trim()
            }
            for (match in matches.asReversed()) text = text.replaceRange(match.range, " ")
        }

        fun bpm(
            low: Int?,
            high: Int?,
        ) {
            minBpm = listOfNotNull(minBpm, low).maxOrNull()
            maxBpm = listOfNotNull(maxBpm, high).minOrNull()
        }

        fun year(
            low: Int?,
            high: Int?,
        ) {
            minYear = listOfNotNull(minYear, low).maxOrNull()
            maxYear = listOfNotNull(maxYear, high).minOrNull()
        }
        extract("""\b(\d{1,5})\s*[-–]\s*(\d{1,5})\s*bpm\b""") { bpm(it.groupValues[1].toInt(), it.groupValues[2].toInt()) }
        extract("""\b(?:under|below|less than)\s+(\d{1,5})\s*bpm\b""") { bpm(null, it.groupValues[1].toInt() - 1) }
        extract("""\b(?:over|above|more than)\s+(\d{1,5})\s*bpm\b""") { bpm(it.groupValues[1].toInt() + 1, null) }
        extract("""\b(?:around|about)\s+(\d{1,5})\s*bpm\b""") {
            val n = it.groupValues[1].toInt()
            bpm(n - 5, n + 5)
        }
        extract("""\b(?:at\s+)?(\d{1,5})\s*bpm\b""") {
            val n = it.groupValues[1].toInt()
            bpm(n, n)
        }
        extract("""\b(?:released\s+)?(?:between|from)\s+((?:19|20)\d{2})\s+(?:and|to)\s+((?:19|20)\d{2})\b""") {
            year(it.groupValues[1].toInt(), it.groupValues[2].toInt())
        }
        extract("""\b(?:released\s+)?(?:in|during)\s+((?:19|20)\d{2})\b""") {
            val n = it.groupValues[1].toInt()
            year(n, n)
        }
        extract("""\b(?:released\s+)?(?:after|since)\s+((?:19|20)\d{2})\b""") {
            year(it.groupValues[1].toInt() + if (it.value.contains("since", true)) 0 else 1, null)
        }
        extract("""\b(?:released\s+)?before\s+((?:19|20)\d{2})\b""") { year(null, it.groupValues[1].toInt() - 1) }
        extract("""\b(?:(?:music\s+)?from\s+|in\s+)?(?:the\s+)?((?:19|20)?\d0)['’]?s\b""") {
            val raw = it.groupValues[1].toInt()
            val n =
                if (raw >= 1900) {
                    raw
                } else if (raw <= 20) {
                    2000 + raw
                } else {
                    1900 + raw
                }
            year(n, n + 9)
        }
        extract("""\b(?:without|excluding|except|no)\s+(.+?)(?=[,;]|$)""") { match ->
            val phrase =
                match.groupValues[1]
                    .trim()
                    .removeSuffix(" and")
                    .trim()

            fun exclude(part: String) {
                val key = genreKey(part)
                when {
                    index.entries.any { it.kind == EntityKind.ARTIST && index.isArtist(it.name, part) } -> artists += part
                    key in knownGenres || index.songs.any { song -> song.genres.any { genreKey(it) == key } } -> genres += key
                    else -> error = "Unknown exclusion: " + part
                }
            }
            if (index.entries.any { it.kind == EntityKind.ARTIST && index.isArtist(it.name, phrase) } ||
                genreKey(phrase) in knownGenres
            ) {
                exclude(phrase)
            } else {
                phrase.split(Regex("""\s+(?:and|or)\s+""", RegexOption.IGNORE_CASE)).forEach(::exclude)
            }
        }
        val invalidTempo = minBpm?.let { it > 400 || it > (maxBpm ?: Int.MAX_VALUE) } == true || maxBpm?.let { it < 20 } == true
        val invalidYears = minYear?.let { it > (maxYear ?: Int.MAX_VALUE) } == true
        if (invalidTempo || invalidYears) error = "These filters do not describe a valid range."
        text =
            text
                .replace(Regex("""\s+"""), " ")
                .trim(' ', ',', ';')
                .replace(Regex("""(?:\s+\band\b|\s+\bwith\b)$""", RegexOption.IGNORE_CASE), "")
                .trim()
        return VybeQueryIntent(text, VybeConstraints(minBpm, maxBpm, minYear, maxYear, artists, genres, descriptions, error))
    }
}

internal fun genreKey(value: String): String =
    when (val key = nameKey(value).replace(" ", "")) {
        "rap", "hiphop", "hiphoprap" -> "hiphop"
        "rnb", "rb", "rhythmandblues" -> "rnb"
        "dnb", "drumandbass", "drumbass" -> "drumandbass"
        "lofi", "lowfidelity" -> "lofi"
        else -> key
    }

private val knownGenres =
    setOf(
        "hiphop",
        "rnb",
        "drumandbass",
        "lofi",
        "rock",
        "pop",
        "jazz",
        "metal",
        "soul",
        "ambient",
        "trap",
        "electronic",
        "classical",
        "country",
        "folk",
        "reggae",
        "blues",
    )

private fun numericMatches(
    value: Int?,
    minimum: Int?,
    maximum: Int?,
): Boolean {
    if (minimum == null && maximum == null) return true
    return value != null && value >= (minimum ?: Int.MIN_VALUE) && value <= (maximum ?: Int.MAX_VALUE)
}
