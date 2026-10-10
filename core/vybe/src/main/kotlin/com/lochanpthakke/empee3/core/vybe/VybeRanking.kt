package com.lochanpthakke.empee3.core.vybe

import kotlin.math.sqrt

/** Cosine similarity of two vectors; null when they cannot be compared (empty, different lengths, zero or non-finite). */
fun cosineSimilarity(
    a: FloatArray,
    b: FloatArray,
): Float? {
    if (a.isEmpty() || a.size != b.size) return null
    var dot = 0.0
    var normA = 0.0
    var normB = 0.0
    for (i in a.indices) {
        val x = a[i].toDouble()
        val y = b[i].toDouble()
        dot += x * y
        normA += x * x
        normB += y * y
    }
    val similarity = dot / (sqrt(normA) * sqrt(normB))
    return similarity.takeIf { it.isFinite() && normA > 0.0 && normB > 0.0 }?.toFloat()
}

/** A song's description vector. */
class EmbeddedSong(
    val songId: Long,
    val vector: FloatArray,
)

data class VybeMatch(
    val songId: Long,
    val score: Float,
)

/** Ranks songs against a query vector. Brute force: a phone library is small enough. */
object VybeRanker {
    /**
     * The [limit] songs most similar to [query], best first; equal scores by song id, so the
     * order never depends on the input order. Songs whose vectors cannot be compared with
     * the query (see [cosineSimilarity]) are left out; an invalid query gives no matches.
     */
    fun rank(
        query: FloatArray,
        songs: List<EmbeddedSong>,
        limit: Int,
    ): List<VybeMatch> {
        if (limit <= 0) return emptyList()
        return songs
            .mapNotNull { song -> cosineSimilarity(query, song.vector)?.let { VybeMatch(song.songId, it) } }
            .sortedWith(compareByDescending<VybeMatch> { it.score }.thenBy { it.songId })
            .take(limit)
    }
}

/**
 * Natural-language search over songs with a [TextEmbedder]: each song's description and the
 * query are embedded, then ranked by cosine similarity. Nothing is stored or sent anywhere.
 * (Phase 1: song vectors are computed per call; persisting them comes with indexing.)
 */
class VybeSearch(
    private val embedder: TextEmbedder,
) {
    fun search(
        query: String,
        songs: List<SongText>,
        limit: Int,
    ): List<VybeMatch> {
        val text = query.trim()
        if (text.isEmpty() || songs.isEmpty() || limit <= 0) return emptyList()
        val queryVector = embedder.embed(text, EmbeddingRole.QUERY)
        val embedded = songs.map { EmbeddedSong(it.songId, embedder.embed(SongDescriber.describe(it), EmbeddingRole.DOCUMENT)) }
        return VybeRanker.rank(queryVector, embedded, limit)
    }
}
