package com.erhu.erhu.core.database.library

import com.erhu.erhu.core.database.entity.ArtistArtworkEntity

/**
 * Rules for stored artist artwork ([ArtistArtworkEntity]).
 *
 * Automatic rows are decided by a matcher version. Rows from an older matcher (the first exact-name
 * pick that chose Deezer duplicates, or blank photos) are re-checked once. [NOT_FOUND] means the
 * current matcher found no safe photo; it is retried after [NOT_FOUND_RETRY_MS]. A failed lookup
 * stores nothing, so it is retried on a later launch. CUSTOM rows are never looked up.
 */
object ArtistArtworkCache {
    /** Versioned "looked up, no safe photo" marker of matcher [MATCHER_VERSION]. */
    const val NOT_FOUND = "NONE:3"
    private const val NOT_FOUND_PREFIX = "NONE"

    /** The Deezer matcher with exact-name candidates, fan-count disambiguation and photo validation. */
    const val MATCHER_VERSION = 2

    const val NOT_FOUND_RETRY_MS = 30L * 24 * 60 * 60 * 1000

    /** Whether the automatic lookup should (re)decide this artist's artwork; never for custom artwork. */
    fun needsAutomaticLookup(
        stored: ArtistArtworkEntity?,
        now: Long,
    ): Boolean =
        when {
            stored == null -> true
            stored.source != ArtistArtworkEntity.SOURCE_AUTO -> false
            stored.matcherVersion < MATCHER_VERSION -> true
            // A clock that went backwards must not postpone the retry forever.
            isNotFound(stored.artworkUri) -> now - stored.updatedAt !in 0 until NOT_FOUND_RETRY_MS
            else -> false
        }

    fun isNotFound(artworkUri: String?): Boolean = artworkUri?.startsWith(NOT_FOUND_PREFIX) == true

    fun hasArtwork(artworkUri: String?): Boolean = !artworkUri.isNullOrBlank() && !isNotFound(artworkUri)
}
