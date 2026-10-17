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
 * The sources offered for tag fixing, with what each really provides. Last.fm is offered with its
 * true state: without an API key it is listed as not configured (and refused when chosen alone);
 * LRCLIB serves lyrics only (Lyrics page) and is not a tag source.
 */
fun metadataSourceOptions(lastFmConfigured: Boolean): List<MetadataSourceOption> =
    listOf(
        MetadataSourceOption(
            MetadataSources.ALL,
            "All providers",
            "Genius, DuckDuckGo, Fanart.tv, TheAudioDB, Discogs, Wikipedia, iTunes, MusicBrainz, Deezer and Last.fm together",
        ),
        MetadataSourceOption(
            MetadataSources.GENIUS,
            "Genius",
            "Keyless artist profile pictures and high-res header portraits",
        ),
        MetadataSourceOption(
            MetadataSources.DUCKDUCKGO,
            "DuckDuckGo Web Search",
            "Keyless public web image search for instant out-of-the-box artist photos",
        ),
        MetadataSourceOption(
            MetadataSources.FANART_TV,
            "Fanart.tv",
            "Dedicated high-resolution music fanart and square artist portraits",
        ),
        MetadataSourceOption(
            MetadataSources.THE_AUDIO_DB,
            "TheAudioDB",
            "Open music database for official artist promo photos and fanart",
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
        MetadataSources.THE_AUDIO_DB,
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
        MetadataField.GENRE -> "The album's genre: MusicBrainz's top-voted genre, or Deezer's"
        MetadataField.ARTWORK -> "The cover from the match"
        MetadataField.FILENAME -> "Renames the file after its title"
        MetadataField.BPM -> unsupportedReason.orEmpty()
    }

/** How the selection behaves, and why unsupported fields are missing, for a footnote. */
val FIELD_SELECTION_NOTE: String =
    "With every field on (All Fields) the title is updated, while artist, album, year, genre and cover are only filled " +
        "where the file has none (a 1970 year counts as none). With fewer fields, those fields are replaced. " +
        MetadataField.entries
            .filterNot { it.isSupported }
            .joinToString(" ") { "${it.displayName}: ${it.unsupportedReason}" }
