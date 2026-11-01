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

/**
 * Sends explicit artist / album / title queries to exact metadata matching instead of embeddings,
 * which matched "songs like Blonde" to unrelated songs (Experiment 3, 2026-10-09). Names are
 * compared as word sequences, so "Tyler, the Creator" finds the credit "Tyler/ The Creator", and
 * an artist inside a credit ("A feat. B") counts. No similarity is inferred from metadata.
 */
object VybeQueryRouter {
    private val PATTERNS =
        listOf(
            Regex("""^(?:play\s+)?(?:some\s+)?(?:songs?|music|tracks?|stuff|albums?)\s+(?:like|similar to)\s+(.+)$""") to true,
            Regex("""^(?:more\s+)?(?:like|similar to)\s+(.+)$""") to true,
            Regex("""^(?:play\s+)?(?:more\s+)?(?:songs?|music|tracks?)?\s*(?:from|by)\s+(.+)$""") to false,
        )
    private val NON_WORD = Regex("""[^\p{L}\p{N}$]+""")

    /** Names shorter than this (in letters and digits) are too ambiguous to match inside credits. */
    private const val MIN_NAME_CHARS = 3

    @Suppress("ReturnCount")
    fun route(
        query: String,
        songs: List<SongText>,
    ): VybeRoute {
        val original = query.trim()
        val text = original.lowercase()
        val (name, wantsSimilar) =
            PATTERNS.firstNotNullOfOrNull { (regex, similar) ->
                regex.find(text)?.let { match ->
                    // The name as the user typed it ("Drake", not "drake"), when lower-casing kept the length.
                    val range = match.groups[1]!!.range
                    val typed =
                        if (original.length ==
                            text.length
                        ) {
                            original.substring(range.first, range.last + 1)
                        } else {
                            match.groupValues[1]
                        }
                    typed.trim() to similar
                }
            } ?: return VybeRoute.Describe(original)
        val words = words(name)
        if (words.joinToString("").length < MIN_NAME_CHARS) return VybeRoute.UnknownName(name, query.trim())

        val byArtist = songs.filter { containsSequence(words(it.artist), words) }
        val byAlbum = songs.filter { words(it.album) == words }
        val byTitle = songs.filter { words(it.title) == words }
        // An album named like its artist ("Drake" …) is rare; artist wins, then album, then title.
        return when {
            byArtist.isNotEmpty() -> VybeRoute.Named(EntityKind.ARTIST, name, byArtist.ids(), wantsSimilar)
            byAlbum.isNotEmpty() -> VybeRoute.Named(EntityKind.ALBUM, name, byAlbum.ids(), wantsSimilar)
            byTitle.isNotEmpty() -> VybeRoute.Named(EntityKind.TITLE, name, byTitle.ids(), wantsSimilar)
            else -> VybeRoute.UnknownName(name, query.trim())
        }
    }

    private fun List<SongText>.ids(): List<Long> = map { it.songId }.sorted()

    private fun words(text: String): List<String> = text.lowercase().split(NON_WORD).filter { it.isNotEmpty() }

    private fun containsSequence(
        haystack: List<String>,
        needle: List<String>,
    ): Boolean =
        needle.isNotEmpty() &&
            needle.size <= haystack.size &&
            (0..haystack.size - needle.size).any { start -> haystack.subList(start, start + needle.size) == needle }
}
