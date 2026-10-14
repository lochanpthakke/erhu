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
 * The sources offered for tag fixing, with what each really provides. Last.fm is offered with its
 * true state: without an API key it is listed as not configured (and refused when chosen alone);
 * LRCLIB serves lyrics only (Lyrics page) and is not a tag source.
 */
fun metadataSourceOptions(lastFmConfigured: Boolean): List<MetadataSourceOption> =
    listOf(
        MetadataSourceOption(
            MetadataSources.ALL,
            "All providers",
            if (lastFmConfigured) {
                "MusicBrainz, Deezer and Last.fm together; the best-scoring match is used"
            } else {
                "MusicBrainz and Deezer together (Last.fm has no API key); the best-scoring match is used"
            },
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
        MetadataSourceOption(
            MetadataSources.LAST_FM,
            "Last.fm",
            if (lastFmConfigured) {
                "Title and artist only: Last.fm's search has no album, year or genre"
            } else {
                "Not configured: needs a Last.fm API key (lastfm.apiKey in local.properties)"
            },
        ),
    )

private val OFFERED_KEYS = listOf(MetadataSources.ALL, MetadataSources.MUSICBRAINZ, MetadataSources.DEEZER, MetadataSources.LAST_FM)

/** A stored source back to an offered one; anything unknown means all providers. */
fun offeredMetadataSource(stored: String?): String =
    OFFERED_KEYS.firstOrNull { it.equals(stored, ignoreCase = true) } ?: MetadataSources.ALL

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
