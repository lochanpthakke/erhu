package com.lochanpthakke.empee3.core.database.library

/**
 * Values stored for an artist's profile artwork: a URL, or a "looked up, nothing
 * found" marker so the lookup is not repeated on every launch.
 *
 * The marker is versioned. Plain [LEGACY_NOT_FOUND] was written by the old lookup,
 * whose `artist:"…"` query often missed the real artist (e.g. Drake), so it is
 * retried exactly once; the retry stores [NOT_FOUND], which is never retried.
 */
object ArtistArtworkCache {
    const val NOT_FOUND = "NONE:2"
    private const val LEGACY_NOT_FOUND = "NONE"
    private const val NOT_FOUND_PREFIX = "NONE"

    fun needsLookup(artworkUri: String?): Boolean = artworkUri == null || artworkUri == LEGACY_NOT_FOUND

    fun hasArtwork(artworkUri: String?): Boolean = !artworkUri.isNullOrBlank() && !artworkUri.startsWith(NOT_FOUND_PREFIX)
}
