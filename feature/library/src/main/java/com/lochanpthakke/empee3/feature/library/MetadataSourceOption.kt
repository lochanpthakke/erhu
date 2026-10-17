package com.lochanpthakke.empee3.feature.library

import com.lochanpthakke.empee3.core.model.MetadataField
import com.lochanpthakke.empee3.core.network.repository.MetadataSources

/** A metadata source the user can choose, with what it really provides (verified 2026-10-13). */
data class MetadataSourceOption(
    val key: String,
    val label: String,
    val summary: String,
)

/**
 * The sources offered for tag fixing, with what each really provides. Last.fm and Fanart.tv are offered with their
 * true state: without an API key they are listed as not configured;
 * LRCLIB serves lyrics only (Lyrics page) and is not a tag source.
 */
fun metadataSourceOptions(
    lastFmConfigured: Boolean,
    fanartTvConfigured: Boolean = false,
): List<MetadataSourceOption> =
    listOf(
        MetadataSourceOption(
            MetadataSources.ALL,
            "All providers",
            "Genius, DuckDuckGo, Fanart.tv, Discogs, Wikipedia, iTunes, MusicBrainz, Deezer and Last.fm together",
        ),
        MetadataSourceOption(
            MetadataSources.GENIUS,
            "Genius",
            "Keyless artist profile pictures and high-res header portraits",
        ),
        MetadataSourceOption(
            MetadataSources.DUCKDUCKGO,
            "DuckDuckGo Web Search",
            "Resilient web image search fallback for artist portraits",
        ),
        MetadataSourceOption(
            MetadataSources.FANART_TV,
            "Fanart.tv",
            if (fanartTvConfigured) {
                "High-resolution community artist thumbs and backgrounds"
            } else {
                "Not configured: needs a Fanart.tv API key in Settings"
            },
        ),
        MetadataSourceOption(
            MetadataSources.DISCOGS,
            "Discogs",
            "Music database catalog and artist profile pictures",
        ),
        MetadataSourceOption(
            MetadataSources.WIKIPEDIA,
            "Wikipedia / Wikimedia",
            "Wikipedia lead photos and Wikimedia Commons public domain artist images",
        ),
        MetadataSourceOption(
            MetadataSources.ITUNES,
            "iTunes / Apple Music",
            "Title, artist, album, release date, genre, track/disc number, and 1000×1000 px cover artwork from Apple Music catalog",
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

private val OFFERED_KEYS =
    listOf(
        MetadataSources.ALL,
        MetadataSources.GENIUS,
        MetadataSources.DUCKDUCKGO,
        MetadataSources.FANART_TV,
        MetadataSources.DISCOGS,
        MetadataSources.WIKIPEDIA,
        MetadataSources.ITUNES,
        MetadataSources.MUSICBRAINZ,
        MetadataSources.DEEZER,
        MetadataSources.LAST_FM,
    )

/** A stored source back to an offered one; anything unknown means all providers. */
fun offeredMetadataSource(stored: String?): String =
    OFFERED_KEYS.firstOrNull { it.equals(stored, ignoreCase = true) } ?: MetadataSources.ALL

/** What each field does when it is selected (all tag fields together = "All Fields"). */
fun MetadataField.summary(): String =
    when (this) {
        MetadataField.TITLE -> "The online title; a “(feat. …)” note in your title is kept"
        MetadataField.ARTIST -> "The artist; a fuller credit with featured artists is kept"
        MetadataField.ALBUM -> "The album name"
        MetadataField.YEAR -> "The original release date from MusicBrainz; skipped when uncertain"
        MetadataField.GENRE -> "Every genre MusicBrainz (most-voted first) or Deezer lists for the album"
        MetadataField.ARTWORK -> "The cover from the match"
        MetadataField.FILENAME -> "Renames the file after its title"
        MetadataField.BPM -> "Tempo from AcousticBrainz, else estimated on this device; skipped when uncertain"
    }

/** How the selection behaves, and why unsupported fields are missing, for a footnote. */
val FIELD_SELECTION_NOTE: String =
    "With every field on (All Fields) the title is updated, while artist, album, year, genre, cover and BPM are only " +
        "filled where the file has none (a 1970 year counts as none). With fewer fields, those fields are replaced, " +
        "except BPM: a found BPM only fills a missing one (use half / double time in the inspector to correct one). " +
        MetadataField.entries
            .filterNot { it.isSupported }
            .joinToString(" ") { "${it.displayName}: ${it.unsupportedReason}" }
