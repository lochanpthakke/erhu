package com.erhu.erhu.core.model

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
    val trackNumber: Int = 0,
    val discNumber: Int = 1,
    val filePath: String? = null,
    val mimeType: String? = null,
    val sampleRate: Int = 0,
    val bitrate: Int = 0,
    val channels: Int = 2,
    /** The file's genre tag(s), joined by a semicolon if there are multiple. */
    val genre: String? = null,
    /** The file's BPM tag, when it holds a plausible tempo. */
    val bpm: Int? = null,
) {
    val isLossless: Boolean
        get() =
            mimeType?.contains("flac", ignoreCase = true) == true ||
                mimeType?.contains("wav", ignoreCase = true) == true ||
                mimeType?.contains("alac", ignoreCase = true) == true ||
                uri.contains(".flac", ignoreCase = true) ||
                uri.contains(".wav", ignoreCase = true) ||
                filePath?.endsWith(".flac", ignoreCase = true) == true ||
                filePath?.endsWith(".wav", ignoreCase = true) == true ||
                filePath?.endsWith(".alac", ignoreCase = true) == true

    val isDolbyAtmos: Boolean
        get() =
            channels > 2 ||
                mimeType?.contains("ac3", ignoreCase = true) == true ||
                mimeType?.contains("eac3", ignoreCase = true) == true ||
                mimeType?.contains("ac4", ignoreCase = true) == true ||
                mimeType?.contains("spatial", ignoreCase = true) == true ||
                mimeType?.contains("atmos", ignoreCase = true) == true ||
                filePath?.contains("atmos", ignoreCase = true) == true ||
                filePath?.contains("dolby", ignoreCase = true) == true ||
                filePath?.contains("spatial", ignoreCase = true) == true ||
                album.contains("atmos", ignoreCase = true) ||
                album.contains("dolby", ignoreCase = true) ||
                album.contains("spatial", ignoreCase = true) ||
                title.contains("atmos", ignoreCase = true) ||
                title.contains("dolby", ignoreCase = true)
}
