package com.lochanpthakke.empee3.feature.library

import com.lochanpthakke.empee3.core.model.MetadataFixAction
import com.lochanpthakke.empee3.core.network.repository.MetadataSources

/** A metadata source the user can choose, with what it really provides (verified 2026-10-13). */
data class MetadataSourceOption(
    val key: String,
    val label: String,
    val summary: String,
)

/**
 * The sources offered for tag fixing. Last.fm is not offered: its API rejects the app's key, and
 * its track search has no album, genre or year anyway. LRCLIB serves lyrics only (Lyrics page).
 */
val METADATA_SOURCE_OPTIONS: List<MetadataSourceOption> =
    listOf(
        MetadataSourceOption(
            MetadataSources.ALL,
            "All providers",
            "MusicBrainz and Deezer together; the best-scoring match is used",
        ),
        MetadataSourceOption(
            MetadataSources.MUSICBRAINZ,
            "MusicBrainz",
            "Title, artist, album and release date; album genre; cover from the Cover Art Archive",
        ),
        MetadataSourceOption(
            MetadataSources.DEEZER,
            "Deezer",
            "Title, artist, album and cover; album genre; no release year. Returns no tracks where Deezer isn't offered, such as India",
        ),
    )

/** A stored source back to an offered one; anything else (e.g. the old Last.fm choice) means all providers. */
fun offeredMetadataSource(stored: String?): String =
    METADATA_SOURCE_OPTIONS.firstOrNull { it.key.equals(stored, ignoreCase = true) }?.key ?: MetadataSources.ALL

/** What each offered fix action actually writes. */
fun MetadataFixAction.summary(): String =
    when (this) {
        MetadataFixAction.ALL_FIELDS ->
            "Title from the match; artist, album, year, genre and cover only where the file has none (a 1970 year counts as none)"
        MetadataFixAction.TITLE_ONLY -> "Only the title"
        MetadataFixAction.ARTIST_ONLY -> "Only the artist; a fuller credit with featured artists is kept"
        MetadataFixAction.ALBUM_ONLY -> "Only the album name, replacing the current one"
        MetadataFixAction.YEAR_ONLY -> "The original release date from MusicBrainz, replacing the file's year; skipped when uncertain"
        MetadataFixAction.GENRE_ONLY -> "The album's genre: MusicBrainz's top-voted genre, or Deezer's"
        MetadataFixAction.ARTWORK_ONLY -> "Replaces the embedded cover with the match's cover"
        MetadataFixAction.BPM_ONLY, MetadataFixAction.LYRICS_ONLY -> unsupportedReason.orEmpty()
    }

/** Why the actions that aren't offered are missing, for a footnote. */
val UNSUPPORTED_FIX_ACTIONS_NOTE: String =
    MetadataFixAction.entries
        .filterNot { it.isSupported }
        .joinToString(" ") { "${it.displayName.removeSuffix(" Only")}: ${it.unsupportedReason}" }
