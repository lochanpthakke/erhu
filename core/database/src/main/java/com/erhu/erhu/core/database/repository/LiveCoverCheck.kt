package com.erhu.erhu.core.database.repository

import com.erhu.erhu.core.model.AnimatedArtOrientation

/** What an album page can offer for the album's live (animated) cover. */
sealed interface LiveCoverCheck {
    /** Apple has no motion cover for the album (or no match for it at all). */
    data object None : LiveCoverCheck

    /**
     * A motion cover exists and can be downloaded: the phone-sized variant's resolution, its
     * size in bytes when the playlist states it (null otherwise; never estimated), and any
     * alternative stream variants available from Apple Music HLS.
     */
    data class Downloadable(
        val orientation: AnimatedArtOrientation,
        val width: Int?,
        val height: Int?,
        val bytes: Long?,
        val variants: List<LiveCoverVariant> = emptyList(),
        val selectedVariant: LiveCoverVariant? = null,
    ) : LiveCoverCheck

    /** The cover is saved on the phone at [filePath] ([bytes] long). */
    data class Downloaded(
        val orientation: AnimatedArtOrientation,
        val filePath: String,
        val bytes: Long,
    ) : LiveCoverCheck
}

/** A video stream option available for a live cover. */
data class LiveCoverVariant(
    val resolutionText: String,
    val width: Int?,
    val height: Int?,
    val bytes: Long?,
    val estimatedSizeMb: Float,
    val variantUrl: String,
    val bandwidth: Int = 0,
)
