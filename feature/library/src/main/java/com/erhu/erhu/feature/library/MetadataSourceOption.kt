package com.erhu.erhu.feature.library

import com.erhu.erhu.core.model.MetadataField
import com.erhu.erhu.core.network.repository.MetadataSources

/** A metadata source the user can choose, with what it really provides (verified 2026-10-13). */
data class MetadataSourceOption(
    val key: String,
    val label: String,
    val summary: String,
)

/** Keyless metadata and artwork sources. Lyrics providers belong to the separate Lyrics flow. */
fun metadataSourceOptions(): List<MetadataSourceOption> =
    listOf(
        MetadataSourceOption(
            MetadataSources.ALL,
            "All providers",
            "iTunes, MusicBrainz and Deezer together",
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
    )

/** Old or unsupported saved provider choices fall back to the working keyless providers. */
fun offeredMetadataSource(stored: String?): String = MetadataSources.normalizeSelection(stored)

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
