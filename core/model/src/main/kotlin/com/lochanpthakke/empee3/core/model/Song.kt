package com.lochanpthakke.empee3.core.model

/**
 * A playable track from the local library.
 *
 * Artwork is referenced by [artworkUri] (MediaStore album-art URI) rather than
 * carried as decoded bytes: decoding embedded pictures for every row of every
 * library query was the dominant cost of mapping songs. UI loads artwork lazily
 * from [uri]/[artworkUri] instead.
 */
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val uri: String,
    val artworkUri: String? = null,
    val isFavorite: Boolean = false,
)
