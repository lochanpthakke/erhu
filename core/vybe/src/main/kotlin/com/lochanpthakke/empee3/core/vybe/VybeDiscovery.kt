package com.lochanpthakke.empee3.core.vybe

/** Where song vectors come from when ranking (the on-disk index in the app, a map in tests). */
interface SongVectors {
    /** The [limit] best songs by [score] (null leaves a song out), best first. */
    fun rank(
        limit: Int,
        score: (songId: Long, vector: FloatArray) -> Float?,
    ): List<VybeMatch>

    fun vectorOf(songId: Long): FloatArray?
}

/** A discovered song, its score, and why it is there (shown to the user). */
data class VybeHit(
    val songId: Long,
    val score: Float,
    val reasons: List<String>,
)

/** VYBE's answer to one query. */
sealed interface VybeAnswer {
    /** The query named library songs exactly (artist, album or title): those songs, from metadata. */
    data class Named(
        val kind: EntityKind,
        val name: String,
        val songIds: List<Long>,
        /** For "like …" queries: the songs nearest to the named ones by genre and tempo. */
        val similar: List<VybeHit>,
    ) : VybeAnswer

    /**
     * A described mood or style, matched against each song's genre / tempo description. [weak] is
     * true when no song's description is close to the query, or the scores barely separate the
     * library: the order then says little (moods like "sad" aren't in genre or tempo tags).
     */
    data class Described(
        val query: String,
        val hits: List<VybeHit>,
        val weak: Boolean,
        /** Set when the query named something the library doesn't have ("songs like Blonde"). */
        val unknownName: String? = null,
    ) : VybeAnswer

    /** Songs nearest to [seedSongId] by their genre / tempo description. */
    data class SimilarTo(
        val seedSongId: Long,
        val hits: List<VybeHit>,
    ) : VybeAnswer
}

/**
 * VYBE's hybrid search over the library, the user's own songs only:
 * 1. [VybeQueryRouter]: a query naming a library artist, album or title is answered exactly from
 *    metadata; semantic matching never overrides it.
 * 2. Otherwise the query is embedded and compared with each song's description vector
 *    ([VybeText.describe]: genres, tempo, artist, lyrics excerpt; no titles, which leaked words
 *    like "Late" and "Dark" into mood queries in Experiment 3).
 * 3. Metadata that the query names outright counts: songs by a library artist it names ("Frank
 *    Ocean vibes") come first, and one of the library's genres ("trap") is boosted. Tempo words ("gym", "mellow") only nudge songs whose
 *    BPM tag fits, since tags can be half or double time.
 * Songs without genre or BPM stay in: they are ranked by what they have.
 */
class VybeDiscovery(
    private val embedder: TextEmbedder,
    private val vectors: SongVectors,
) {
    fun search(
        query: String,
        songs: List<SongText>,
        limit: Int,
    ): VybeAnswer {
        val text = query.trim()
        return when (val route = VybeQueryRouter.route(text, songs)) {
            is VybeRoute.Named -> {
                val similar = if (route.wantsSimilar) similarTo(route.songIds.toSet(), limit) else emptyList()
                VybeAnswer.Named(route.kind, route.name, route.songIds, similar)
            }
            is VybeRoute.UnknownName -> describe(text, songs, limit).copy(unknownName = route.name)
            is VybeRoute.Describe -> describe(text, songs, limit)
        }
    }

    /** Songs nearest to [songId]'s own description (the current track, say), itself excluded. */
    fun similarToSong(
        songId: Long,
        limit: Int,
    ): VybeAnswer.SimilarTo = VybeAnswer.SimilarTo(songId, similarTo(setOf(songId), limit))

    private fun similarTo(
        seeds: Set<Long>,
        limit: Int,
    ): List<VybeHit> {
        val seedVectors = seeds.mapNotNull { vectors.vectorOf(it) }
        if (seedVectors.isEmpty()) return emptyList()
        val centroid = FloatArray(seedVectors.first().size) { i -> seedVectors.sumOf { it[i].toDouble() }.toFloat() / seedVectors.size }
        return vectors
            .rank(limit) { id, v -> if (id in seeds) null else cosineSimilarity(centroid, v) }
            .map { VybeHit(it.songId, it.score, listOf(SIMILAR_REASON)) }
    }

    private fun describe(
        query: String,
        songs: List<SongText>,
        limit: Int,
    ): VybeAnswer.Described {
        if (query.isEmpty() || songs.isEmpty() || limit <= 0) return VybeAnswer.Described(query, emptyList(), weak = false)
        val queryVector = embedder.embed(query, EmbeddingRole.QUERY)
        val boosts = metadataBoosts(query, songs)
        // Scores of all songs (cheap: a dot product each) to judge whether the query separates them.
        val all = vectors.rank(songs.size.coerceAtLeast(1)) { _, v -> cosineSimilarity(queryVector, v) }
        val weak = isWeak(all.map { it.score })
        val ranked =
            all
                .map { match -> match to (boosts[match.songId]?.sumOf { it.second.toDouble() }?.toFloat() ?: 0f) }
                // Songs by an artist the query names come first, whatever their description scores.
                .sortedWith(
                    compareByDescending<Pair<VybeMatch, Float>> { (match, _) ->
                        boosts[match.songId].orEmpty().any {
                            it.second ==
                                ARTIST_BOOST
                        }
                    }.thenByDescending { it.first.score + it.second }
                        .thenBy { it.first.songId },
                ).take(limit)
        return VybeAnswer.Described(
            query = query,
            hits =
                ranked.map { (match, boost) ->
                    VybeHit(match.songId, match.score + boost, boosts[match.songId].orEmpty().map { it.first })
                },
            weak = weak,
        )
    }

    companion object {
        const val SIMILAR_REASON = "Similar genre and tempo"

        /** A library artist named in a mood query: its songs are ranked first (and scored up). */
        const val ARTIST_BOOST = 0.15f

        /** One of the library's genres named outright. */
        const val GENRE_BOOST = 0.08f

        /** Tempo words: a nudge only (tags can be half or double time). */
        const val TEMPO_BOOST = 0.03f

        /** Best-vs-median similarity below this: the query doesn't separate the library's songs. */
        const val WEAK_SPREAD = 0.03f

        /**
         * Best similarity below this: nothing in the library is described like the query. Measured
         * on the S23 library (EmbeddingGemma, 2026-10-16): genre-, artist- and style-led queries
         * scored 0.35-0.55 at the top, "sad but beautiful" 0.21.
         */
        const val WEAK_BEST = 0.30f

        private val FAST_WORDS =
            setOf("gym", "workout", "energy", "energetic", "high-energy", "hype", "fast", "running", "run", "party", "upbeat", "pump")
        private val SLOW_WORDS =
            setOf("mellow", "slow", "chill", "calm", "relax", "relaxing", "sleep", "late-night", "soft", "laid-back", "quiet")
        private const val FAST_BPM = 120
        private const val SLOW_BPM = 95
        private val WORD = Regex("""[\p{L}\p{N}&$]+(?:-[\p{L}\p{N}]+)*""")
        private const val MIN_NAME_CHARS = 3
        private val NOT_ARTISTS = setOf("unknown", "<unknown>", "unknown artist", "various artists")

        @Suppress("ReturnCount")
        internal fun isWeak(scores: List<Float>): Boolean {
            if (scores.isEmpty()) return false
            val sorted = scores.sortedDescending()
            if (sorted.first() < WEAK_BEST) return true
            return sorted.size >= 2 && sorted.first() - sorted[sorted.size / 2] < WEAK_SPREAD
        }

        /** Per song: (reason, boost) for metadata the query names outright. */
        internal fun metadataBoosts(
            query: String,
            songs: List<SongText>,
        ): Map<Long, List<Pair<String, Float>>> {
            val queryWords = words(query)
            val boosts = HashMap<Long, MutableList<Pair<String, Float>>>()

            fun add(
                id: Long,
                reason: String,
                boost: Float,
            ) = boosts.getOrPut(id) { mutableListOf() }.add(reason to boost)

            for (song in songs) {
                val artist = artistNames(song.artist).firstOrNull { name -> containsSequence(queryWords, words(name)) }
                artist?.let { add(song.songId, "Artist: $it", ARTIST_BOOST) }
                val genre = song.genres.firstOrNull { containsSequence(queryWords, words(it)) }
                genre?.let { add(song.songId, "Genre: $it", GENRE_BOOST) }
                val bpm = song.bpm?.takeIf { it in VybeText.PLAUSIBLE_BPM }
                if (bpm != null) {
                    when {
                        queryWords.any { it in FAST_WORDS } && bpm >= FAST_BPM -> add(song.songId, "Tempo: $bpm BPM (tag)", TEMPO_BOOST)
                        queryWords.any { it in SLOW_WORDS } && bpm <= SLOW_BPM -> add(song.songId, "Tempo: $bpm BPM (tag)", TEMPO_BOOST)
                    }
                }
            }
            return boosts
        }

        /** Each credited artist of "A/ B feat. C" on its own, long enough to match safely. */
        private fun artistNames(credit: String): List<String> =
            credit
                .split(Regex("""\s*(?:/|,|&|;|\bfeat\.?|\bft\.?|\bx\b)\s*""", RegexOption.IGNORE_CASE))
                .map { it.trim() }
                .filter { name -> words(name).joinToString("").length >= MIN_NAME_CHARS && name.lowercase() !in NOT_ARTISTS }

        private fun words(text: String): List<String> = WORD.findAll(text.lowercase()).map { it.value }.toList()

        private fun containsSequence(
            haystack: List<String>,
            needle: List<String>,
        ): Boolean =
            needle.isNotEmpty() &&
                needle.size <= haystack.size &&
                (0..haystack.size - needle.size).any { start -> haystack.subList(start, start + needle.size) == needle }
    }
}
