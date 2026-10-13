package com.lochanpthakke.empee3.core.network.tagger

import com.lochanpthakke.empee3.core.model.MetadataCandidate
import com.lochanpthakke.empee3.core.model.MetadataFixAction

/** A tag a metadata fix can change. */
enum class FixField { TITLE, ARTIST, ALBUM, YEAR, GENRE, ARTWORK }

/** The fields [MetadataFixAction] may change; empty for actions that are not supported. */
fun MetadataFixAction.fields(): Set<FixField> =
    when (this) {
        MetadataFixAction.ALL_FIELDS -> FixField.entries.toSet()
        MetadataFixAction.TITLE_ONLY -> setOf(FixField.TITLE)
        MetadataFixAction.ARTIST_ONLY -> setOf(FixField.ARTIST)
        MetadataFixAction.ALBUM_ONLY -> setOf(FixField.ALBUM)
        MetadataFixAction.YEAR_ONLY -> setOf(FixField.YEAR)
        MetadataFixAction.GENRE_ONLY -> setOf(FixField.GENRE)
        MetadataFixAction.ARTWORK_ONLY -> setOf(FixField.ARTWORK)
        MetadataFixAction.BPM_ONLY, MetadataFixAction.LYRICS_ONLY -> emptySet()
    }

/**
 * What the file has now. [tagsReadable] is false when the embedded tags could not be read: then
 * [year], [genre] and [hasArtwork] are unknown, and All Fields never fills them (it could overwrite).
 * [canStoreYearMonth] is false for tag formats that need a day with a month (ID3v2.3).
 */
data class LocalTrackTags(
    val title: String,
    val artist: String,
    val album: String,
    val genre: String? = null,
    val hasArtwork: Boolean = false,
    val tagsReadable: Boolean = true,
    val year: String? = null,
    val canStoreYearMonth: Boolean = true,
)

/** A planned fix: [request] is null when nothing should be written; [notes] say why a field was left alone. */
data class FieldFixPlan(
    val request: TagWriteRequest?,
    val changes: List<Change>,
    val notes: List<String>,
) {
    data class Change(
        val field: FixField,
        val oldValue: String?,
        val newValue: String,
    )
}

/**
 * Turns a confident match into the tag edit for [action], and nothing more:
 * - only the action's fields are considered;
 * - a missing, blank or placeholder value ("Unknown Artist", …) is never written;
 * - an artist credit that already contains the match's artist (e.g. with "feat.") is kept;
 * - All Fields replaces the title, but fills artist, album, genre and artwork only where the file
 *   has none, so a valid artist or album (and the library's grouping by them) or cover is never
 *   replaced; the single-field actions replace explicitly;
 * - the year counts as missing when blank or 1970 (a downloader placeholder): All Fields fills it
 *   then and keeps any other year; Year Only replaces it;
 * - [genre], [artworkBytes] and [releaseDate] are what the caller found online for this match (the
 *   date already validated and unambiguous, see [releaseDateFor]), or null.
 */
@Suppress("LongParameterList", "CyclomaticComplexMethod")
fun planFieldFix(
    contentUri: String,
    filePath: String,
    action: MetadataFixAction,
    local: LocalTrackTags,
    match: MetadataCandidate,
    genre: String?,
    artworkBytes: ByteArray?,
    releaseDate: ReleaseDate? = null,
): FieldFixPlan {
    val fields = action.fields()
    val fillOnly = action == MetadataFixAction.ALL_FIELDS
    val changes = mutableListOf<FieldFixPlan.Change>()
    val notes = mutableListOf<String>()

    fun text(
        field: FixField,
        label: String,
        current: String,
        found: String?,
        keepValidLocal: Boolean,
    ): String? {
        if (field !in fields) return null
        val value = found?.trim()?.takeIf { isUsableTag(it) }
        return when {
            value == null -> null.also { notes += "No $label found online" }
            value == current.trim() -> null
            keepValidLocal && isUsableTag(current) -> null.also { notes += "Kept the existing $label \"${current.trim()}\"" }
            else -> value.also { changes += FieldFixPlan.Change(field, current.takeIf { isUsableTag(it) }, value) }
        }
    }

    val title = text(FixField.TITLE, "title", local.title, match.title, keepValidLocal = false)
    val artist =
        if (FixField.ARTIST in fields && isUsableTag(local.artist) && creditContains(local.artist, match.artist)) {
            null.also { if (!sameText(local.artist, match.artist)) notes += "Kept the existing artist credit \"${local.artist.trim()}\"" }
        } else {
            text(FixField.ARTIST, "artist", local.artist, match.artist, keepValidLocal = fillOnly)
        }
    val album = text(FixField.ALBUM, "album", local.album, match.album, keepValidLocal = fillOnly)

    val year = if (FixField.YEAR in fields) planYear(local, releaseDate, fillOnly, changes, notes) else null

    val newGenre =
        when {
            FixField.GENRE !in fields -> null
            fillOnly && !local.tagsReadable -> null.also { notes += "Genre not filled: the file's tags couldn't be read" }
            else -> text(FixField.GENRE, "genre", local.genre.orEmpty(), genre, keepValidLocal = fillOnly)
        }

    val artwork =
        when {
            FixField.ARTWORK !in fields -> null
            artworkBytes == null || artworkBytes.isEmpty() -> null.also { notes += "No artwork found online" }
            fillOnly && (local.hasArtwork || !local.tagsReadable) -> null.also { notes += "Kept the existing artwork" }
            else ->
                artworkBytes.also {
                    changes +=
                        FieldFixPlan.Change(FixField.ARTWORK, if (local.hasArtwork) "embedded" else null, "from ${match.sourceName}")
                }
        }

    val request =
        TagWriteRequest(
            contentUri = contentUri,
            filePath = filePath,
            title = title,
            artist = artist,
            album = album,
            releaseDate = year,
            genre = newGenre,
            artworkBytes = artwork,
        ).takeIf { it.hasChanges }
    return FieldFixPlan(request, changes, notes)
}

private val PLACEHOLDERS = setOf("unknown", "unknown artist", "unknown album", "unknown title", "<unknown>", "unknown genre")

/** A value worth writing: not blank and not a placeholder some app or provider uses for "no value". */
fun isUsableTag(value: String?): Boolean = value != null && value.isNotBlank() && value.trim().lowercase() !in PLACEHOLDERS

private fun sameText(
    a: String,
    b: String,
): Boolean = a.trim().equals(b.trim(), ignoreCase = true)

/** True when [credit] already names [artist] as a whole word sequence, e.g. "A feat. B" contains "A". */
private fun creditContains(
    credit: String,
    artist: String,
): Boolean {
    val words = { s: String -> s.lowercase().split(Regex("""[^\p{L}\p{N}$]+""")).filter { it.isNotEmpty() } }
    val c = words(credit)
    val a = words(artist)
    if (a.isEmpty() || a.size > c.size) return false
    return (0..c.size - a.size).any { start -> c.subList(start, start + a.size) == a }
}

/**
 * The year tag value to write, or null. The date keeps its precision unless the file's tag format
 * can't hold it: a year and month without a day is then saved as the year alone, and a note says so.
 */
private fun planYear(
    local: LocalTrackTags,
    found: ReleaseDate?,
    fillOnly: Boolean,
    changes: MutableList<FieldFixPlan.Change>,
    notes: MutableList<String>,
): String? {
    val current = local.year?.trim().orEmpty()
    val (date, note) = yearToWrite(local, current, found, fillOnly)
    note?.let { notes += it }
    if (date == null || date.tagValue == current) return null
    changes += FieldFixPlan.Change(FixField.YEAR, current.ifEmpty { null }, date.tagValue)
    return date.tagValue
}

/** The date to write (null to keep the file's) and a note explaining any choice. */
private fun yearToWrite(
    local: LocalTrackTags,
    current: String,
    found: ReleaseDate?,
    fillOnly: Boolean,
): Pair<ReleaseDate?, String?> =
    when {
        found == null -> null to "No release year found online"
        fillOnly && !local.tagsReadable -> null to "Year not filled: the file's tags couldn't be read"
        fillOnly && isUsableYearTag(current) ->
            null to "Kept the existing year \"$current\"".takeUnless { current.startsWith(found.year.toString()) }
        current.length > found.tagValue.length && current.startsWith(found.tagValue) ->
            null to "Kept the existing, more precise date \"$current\""
        found.hasMonthOnly && !local.canStoreYearMonth ->
            found.yearOnly() to "Saved the year only: this file's tag format (ID3v2.3) can't store a month without a day"
        else -> found to null
    }
