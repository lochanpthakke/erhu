package com.erhu.erhu.core.model

/**
 * A playlist the app builds from the library itself (never stored as a user
 * playlist): its songs are always computed from current play history, favorites
 * and library data.
 */
enum class SmartPlaylist(
    val title: String,
) {
    /** Songs by total plays, most played first. */
    MOST_PLAYED("Most Played"),

    /** Songs by when their file was added / last changed in the library, newest first. */
    RECENTLY_ADDED("Recently Added"),

    /** Songs by their latest play, most recent first. */
    RECENTLY_PLAYED("Recently Played"),

    /** Songs marked as favorite. */
    FAVORITES("Favorites"),

    /** Songs that have never been played. */
    NEVER_PLAYED("Never Played"),
}
