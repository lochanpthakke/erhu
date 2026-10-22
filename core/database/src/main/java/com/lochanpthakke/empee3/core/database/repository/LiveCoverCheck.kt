package com.lochanpthakke.empee3.core.database.repository

import com.lochanpthakke.empee3.core.model.AnimatedArtOrientation

/** What an album page can offer for the album's live (animated) cover. */
sealed interface LiveCoverCheck {
    /** Apple has no motion cover for the album (or no match for it at all). */
    data object None : LiveCoverCheck

    /**
     * A motion cover exists and can be downloaded: the phone-sized variant's resolution, and its
     * size in bytes when the playlist states it (null otherwise; never estimated).
     */
    data class Downloadable(
        val orientation: AnimatedArtOrientation,
        val width: Int?,
        val height: Int?,
        val bytes: Long?,
    ) : LiveCoverCheck

    /** The cover is saved on the phone at [filePath] ([bytes] long). */
    data class Downloaded(
        val orientation: AnimatedArtOrientation,
        val filePath: String,
        val bytes: Long,
    ) : LiveCoverCheck
}
