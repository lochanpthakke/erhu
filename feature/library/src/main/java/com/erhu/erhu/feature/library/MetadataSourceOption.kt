package com.erhu.erhu.feature.library

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.erhu.erhu.core.model.MetadataField
import com.erhu.erhu.core.network.repository.MetadataSources
import com.erhu.erhu.core.common.R as CommonR

/** A metadata source the user can choose, with what it really provides (verified 2026-10-13). */
data class MetadataSourceOption(
    val key: String,
    val label: String,
    val summary: String,
    @StringRes val labelRes: Int = 0,
    @StringRes val summaryRes: Int = 0,
)

@Composable
fun MetadataSourceOption.localizedLabel(): String = if (labelRes != 0) stringResource(labelRes) else label

@Composable
fun MetadataSourceOption.localizedSummary(): String = if (summaryRes != 0) stringResource(summaryRes) else summary

/** Keyless metadata and artwork sources. Lyrics providers belong to the separate Lyrics flow. */
fun metadataSourceOptions(): List<MetadataSourceOption> =
    listOf(
        MetadataSourceOption(
            MetadataSources.ALL,
            "All providers",
            "iTunes, MusicBrainz and Deezer together",
            CommonR.string.metadata_source_all_label,
            CommonR.string.metadata_source_all_summary,
        ),
        MetadataSourceOption(
            MetadataSources.ITUNES,
            "iTunes / Apple Music",
            "Title, artist, album, release date, genre, track/disc number, and 1000×1000 px cover artwork from Apple Music catalog",
            CommonR.string.metadata_source_itunes_label,
            CommonR.string.metadata_source_itunes_summary,
        ),
        MetadataSourceOption(
            MetadataSources.MUSICBRAINZ,
            "MusicBrainz",
            "Title, artist, album and release date; album genre; cover from the Cover Art Archive",
            CommonR.string.metadata_source_mb_label,
            CommonR.string.metadata_source_mb_summary,
        ),
        MetadataSourceOption(
            MetadataSources.DEEZER,
            "Deezer",
            "Title, artist, album and cover; album genre; no release year. Returns no tracks where Deezer isn't offered, such as India",
            CommonR.string.metadata_source_deezer_label,
            CommonR.string.metadata_source_deezer_summary,
        ),
    )

/** Old or unsupported saved provider choices fall back to the working keyless providers. */
fun offeredMetadataSource(stored: String?): String = MetadataSources.normalizeSelection(stored)

private val FIELD_DISPLAY_NAME_RES =
    mapOf(
        MetadataField.TITLE to CommonR.string.metadata_field_title_name,
        MetadataField.ARTIST to CommonR.string.metadata_field_artist_name,
        MetadataField.ALBUM to CommonR.string.metadata_field_album_name,
        MetadataField.YEAR to CommonR.string.metadata_field_year_name,
        MetadataField.GENRE to CommonR.string.metadata_field_genre_name,
        MetadataField.BPM to CommonR.string.metadata_field_bpm_name,
        MetadataField.ARTWORK to CommonR.string.metadata_field_artwork_name,
        MetadataField.FILENAME to CommonR.string.metadata_field_filename_name,
    )

private val FIELD_SUMMARY_RES =
    mapOf(
        MetadataField.TITLE to CommonR.string.metadata_field_title_summary,
        MetadataField.ARTIST to CommonR.string.metadata_field_artist_summary,
        MetadataField.ALBUM to CommonR.string.metadata_field_album_summary,
        MetadataField.YEAR to CommonR.string.metadata_field_year_summary,
        MetadataField.GENRE to CommonR.string.metadata_field_genre_summary,
        MetadataField.ARTWORK to CommonR.string.metadata_field_artwork_summary,
        MetadataField.FILENAME to CommonR.string.metadata_field_filename_summary,
        MetadataField.BPM to CommonR.string.metadata_field_bpm_summary,
    )

@Composable
fun MetadataField.localizedName(): String = stringResource(FIELD_DISPLAY_NAME_RES.getValue(this))

@Composable
fun MetadataField.localizedSummary(): String = stringResource(FIELD_SUMMARY_RES.getValue(this))

@Composable
fun localizedFieldSelectionNote(): String {
    val base = stringResource(CommonR.string.metadata_field_selection_note)
    val unsupported =
        buildList {
            for (field in MetadataField.entries) {
                if (!field.isSupported) {
                    val reason =
                        when (field) {
                            MetadataField.ARTWORK -> stringResource(CommonR.string.metadata_field_artwork_unsupported)
                            else -> field.unsupportedReason.orEmpty()
                        }
                    add("${field.localizedName()}: $reason")
                }
            }
        }.joinToString(" ")
    return if (unsupported.isBlank()) base else "$base $unsupported"
}

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
