package com.erhu.erhu.core.model

data class Artist(
    val id: Long,
    val name: String,
    val songCount: Int,
    val albumCount: Int,
    val artworkUri: String? = null,
    /** Canonical identity key (lowercased, normalized name); empty for artists not built by the scanner. */
    val artistKey: String = "",
    /** The credit text could not be split or confirmed (e.g. "Tyler/ The Creator"); shown as written. */
    val isUnresolved: Boolean = false,
)

/** An artist verified on a bounded set of songs, with one of those songs for artwork fallback. */
data class ArtistOnSongs(
    val artist: Artist,
    val representativeSongId: Long,
)
