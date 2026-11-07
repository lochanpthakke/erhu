package com.erhu.erhu.core.vybe

/** What a name in a query was found as in the library. */
enum class EntityKind { ARTIST, ALBUM, TITLE }

/** How VYBE answers a query. */
sealed interface VybeRoute {
    /**
     * The query names a library artist, album or title ("more from Tyler, the Creator", "songs like
     * Runaway", "albums like IGOR"): [songIds] are the songs it names, found exactly from metadata.
     * [wantsSimilar] is true for "like …" queries, whose *similar* songs need mood or style data
     * the library doesn't have; only the exact songs are certain.
     */
    data class Named(
        val kind: EntityKind,
        val name: String,
        val songIds: List<Long>,
        val wantsSimilar: Boolean,
    ) : VybeRoute

    data class Ambiguous(
        val name: String,
        val matches: List<VybeNameMatch>,
        val wantsSimilar: Boolean,
    ) : VybeRoute

    data class References(
        val references: List<VybeReference>,
        val wantsSimilar: Boolean,
    ) : VybeRoute

    /**
     * A "like …" / "from …" query whose name matches nothing in the library (e.g. an album the user
     * doesn't have, or "songs like rain"). It may still be searched as a description, labelled as such.
     */
    data class UnknownName(
        val name: String,
        val query: String,
    ) : VybeRoute

    /** Not about a name: a mood / activity description for semantic search. */
    data class Describe(
        val query: String,
    ) : VybeRoute
}

/** Catalog matching runs before embeddings. A typo never turns an ambiguous title into a guess. */
object VybeQueryRouter {
    private data class Pattern(
        val regex: Regex,
        val similar: Boolean,
        val kind: EntityKind?,
    )

    private val patterns =
        listOf(
            Pattern(
                Regex("""^(?:play\s+)?(?:some\s+)?albums?\s+(?:like|similar to)\s+(.+)$""", RegexOption.IGNORE_CASE),
                true,
                EntityKind.ALBUM,
            ),
            Pattern(
                Regex("""^(?:play\s+)?(?:some\s+)?artists?\s+(?:like|similar to)\s+(.+)$""", RegexOption.IGNORE_CASE),
                true,
                EntityKind.ARTIST,
            ),
            Pattern(
                Regex("""^(?:play\s+)?(?:some\s+)?(?:songs?|music|tracks?|stuff)\s+(?:like|similar to)\s+(.+)$""", RegexOption.IGNORE_CASE),
                true,
                EntityKind.TITLE,
            ),
            Pattern(Regex("""^(?:more\s+)?(?:like|similar to)\s+(.+)$""", RegexOption.IGNORE_CASE), true, null),
            Pattern(
                Regex("""^(?:play\s+)?(?:more\s+)?(?:songs?|music|tracks?)?\s*(?:from|by|in)\s+(.+)$""", RegexOption.IGNORE_CASE),
                false,
                EntityKind.ARTIST,
            ),
        )
    private val moods =
        setOf(
            "sad",
            "happy",
            "chill",
            "dark",
            "calm",
            "fast",
            "slow",
            "relax",
            "workout",
            "gym",
            "sleep",
            "energetic",
            "mellow",
            "music",
            "songs",
            "vibes",
            "rap",
            "rock",
            "jazz",
            "pop",
            "soul",
            "ambient",
        )

    /** Keep known reference titles out of filter parsing, including titles beginning with No/Without. */
    @Suppress("ReturnCount")
    internal fun catalogRanges(
        query: String,
        index: VybeNameIndex,
    ): List<IntRange> {
        if (index.find(query, partial = false).isNotEmpty()) return listOf(query.indices)
        val group =
            patterns.firstNotNullOfOrNull {
                it.regex
                    .matchEntire(query)
                    ?.groups
                    ?.get(1)
            } ?: return emptyList()
        val text = group.value
        val starts =
            listOf(0) +
                Regex("""\s+(?:and|plus|&)\s+|[,;]""", RegexOption.IGNORE_CASE)
                    .findAll(text)
                    .map { it.range.last + 1 }
                    .toList()
        val ends = Regex("""\s+|[,;]""").findAll(text).map { it.range.first }.toList() + text.length
        return starts.mapNotNull { start ->
            val end =
                ends.asReversed().firstOrNull { end ->
                    end > start && index.find(text.substring(start, end).trim(), partial = false).isNotEmpty()
                }
            end?.let { (group.range.first + start)..(group.range.first + it - 1) }
        }
    }

    fun route(
        query: String,
        songs: List<SongText>,
    ): VybeRoute = route(query, VybeNameIndex(songs))

    @Suppress("ReturnCount") // Exact, ambiguous and missing references are distinct outcomes.
    fun route(
        query: String,
        index: VybeNameIndex,
    ): VybeRoute {
        val text = query.trim()
        val pattern =
            patterns.firstNotNullOfOrNull { p ->
                p.regex.matchEntire(text)?.let { Triple(it.groupValues[1].trim(), p.similar, p.kind) }
            }
        val name = pattern?.first ?: text
        val similar = pattern?.second ?: false
        if (pattern == null &&
            nameWords(name).any { it in moods } &&
            index.find(name, partial = false).isEmpty()
        ) {
            return VybeRoute.Describe(text)
        }
        val matches = index.find(name, pattern?.third)
        if (matches.size == 1) {
            val match = matches.single()
            return VybeRoute.Named(match.kind, match.name, match.songIds, similar)
        }
        if (matches.isNotEmpty()) return VybeRoute.Ambiguous(name, matches, similar)
        VybeReferences.parse(name, index, pattern?.third)?.let { return VybeRoute.References(it, similar) }
        return if (pattern == null) VybeRoute.Describe(text) else VybeRoute.UnknownName(name, text)
    }
}
