package com.erhu.erhu.core.vybe

/**
 * Songs whose descriptions sit closest to a listener's taste: the weighted centroid of the
 * [seeds]' stored vectors (song id -> weight, e.g. how much each is played), scored against every
 * other indexed song by cosine similarity, as VYBE's "similar to" does for a single song.
 *
 * Reads stored vectors only: no model, no query embedding. Two streamed passes over the index.
 * Seeds are left out of the result. Empty when no seed has a current vector.
 */
@Suppress("ReturnCount")
fun tasteNeighbours(
    index: VybeIndexFile,
    live: Map<Long, Long>,
    seeds: Map<Long, Float>,
    limit: Int,
): List<VybeMatch> {
    val positive = seeds.filterValues { it > 0f && it.isFinite() }
    if (positive.isEmpty() || limit <= 0) return emptyList()
    val vectors = index.vectorsOf(live, positive.keys)
    if (vectors.isEmpty()) return emptyList()
    val centroid = FloatArray(vectors.values.first().size)
    var total = 0f
    for ((id, vector) in vectors) {
        val weight = positive.getValue(id)
        val norm = norm(vector).takeIf { it > 0f } ?: continue
        for (i in centroid.indices) centroid[i] += vector[i] / norm * weight
        total += weight
    }
    if (total <= 0f) return emptyList()
    return index.rank(live, limit) { id, vector -> if (id in positive) null else cosineSimilarity(centroid, vector) }
}

private fun norm(vector: FloatArray): Float {
    var sum = 0.0
    for (x in vector) sum += x.toDouble() * x
    return kotlin.math.sqrt(sum).toFloat()
}
