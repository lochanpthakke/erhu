package com.erhu.erhu.core.vybe

import kotlin.math.sqrt

/** Each catalog reference gets one normalized centroid, regardless of its track count. */
@Suppress("ReturnCount") // An incomplete seed/index never produces a partial recommendation.
internal fun rankReferences(
    references: List<Set<Long>>,
    allowed: Set<Long>,
    vectors: SongVectors,
    limit: Int,
): Pair<List<VybeHit>, Boolean> {
    if (references.isEmpty() || limit <= 0) return emptyList<VybeHit>() to false
    val groups =
        references.distinct().map { ids ->
            if (ids.isEmpty()) return emptyList<VybeHit>() to false
            val rows = ids.map { id -> unitVector(vectors.vectorOf(id)) ?: return emptyList<VybeHit>() to true }
            if (rows.any { it.size != rows.first().size }) return emptyList<VybeHit>() to true
            unitVector(FloatArray(rows.first().size) { i -> rows.sumOf { it[i].toDouble() }.toFloat() / rows.size })
                ?: return emptyList<VybeHit>() to true
        }
    if (groups.any { it.size != groups.first().size }) return emptyList<VybeHit>() to true
    val seeds = references.flatten().toSet()
    return vectors
        .rank(limit) { id, vector ->
            if (id in seeds || id !in allowed) {
                null
            } else {
                val scores = groups.map { cosineSimilarity(it, vector) ?: return@rank null }
                // Reward matching every reference; a huge artist catalog cannot drown out a single song.
                scores.average().toFloat() * REFERENCE_AVERAGE_WEIGHT + scores.min() * (1f - REFERENCE_AVERAGE_WEIGHT)
            }
        }.map { VybeHit(it.songId, it.score, listOf("Similar to all selected references")) } to false
}

@Suppress("ReturnCount")
private fun unitVector(vector: FloatArray?): FloatArray? {
    if (vector == null || vector.isEmpty() || vector.any { !it.isFinite() }) return null
    val norm = sqrt(vector.sumOf { it.toDouble() * it })
    if (!norm.isFinite() || norm <= 0) return null
    return FloatArray(vector.size) { (vector[it] / norm).toFloat() }
}

private const val REFERENCE_AVERAGE_WEIGHT = 0.75f
