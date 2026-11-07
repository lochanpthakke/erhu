package com.erhu.erhu.core.network.scorer

import com.erhu.erhu.core.model.MetadataCandidate
import kotlin.math.abs

/** Cross-provider field fallback is allowed only for confident matches to the same edition. */
internal fun metadataSiblings(
    match: MetadataCandidate,
    candidates: List<MetadataCandidate>,
    confidence: Float = HIGH_CONFIDENCE_THRESHOLD,
): List<MetadataCandidate> =
    candidates
        .filter { other ->
            val otherDuration = other.durationMs
            val matchDuration = match.durationMs
            other.id != match.id &&
                other.confidenceScore >= confidence &&
                match.confidenceScore - other.confidenceScore <= METADATA_TIE_MARGIN &&
                foldForMatch(stripFeaturing(other.title)) == foldForMatch(stripFeaturing(match.title)) &&
                creditSimilarity(primaryArtist(other.artist), primaryArtist(match.artist)) == 1f &&
                foldForMatch(other.album) == foldForMatch(match.album) &&
                (otherDuration == null || matchDuration == null || abs(otherDuration - matchDuration) <= METADATA_DURATION_TOLERANCE_MS)
        }.distinctBy { it.id }

private const val METADATA_TIE_MARGIN = 0.02f
private const val METADATA_DURATION_TOLERANCE_MS = 2000L
