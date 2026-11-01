@file:Suppress("TooManyFunctions")

package com.erhu.erhu.core.network.tagger

import com.erhu.erhu.core.model.Genres
import com.erhu.erhu.core.model.MetadataCandidate
import com.erhu.erhu.core.model.MetadataField
import com.erhu.erhu.core.model.MetadataFixAction
import com.erhu.erhu.core.network.bpm.BpmLookup
import com.erhu.erhu.core.network.scorer.foldForMatch

/** A tag a metadata fix can change. */
enum class FixField { TITLE, ARTIST, ALBUM, YEAR, GENRE, ARTWORK, BPM }

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
        MetadataFixAction.BPM_ONLY -> setOf(FixField.BPM)
        MetadataFixAction.LYRICS_ONLY -> emptySet()
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
    /** The file's BPM tag as stored ("128"), if any. */
    val bpm: String? = null,
)

/**
 * A planned fix: [request] is null when nothing should be written; [notes] say why a field was left
 * alone. [unfilled] are the reasons a selected field the file needs could not be found or looked up
 * (not "kept the existing value"): a write with any of them is only a partial fix.
 */
data class FieldFixPlan(
    val request: TagWriteRequest?,
    val changes: List<Change>,
    val notes: List<String>,
    val unfilled: List<String> = emptyList(),
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
 * - All Fields replaces the title (unless it differs only in case or punctuation), but fills artist, album, genre and artwork only where the file
 *   has none, so a valid artist or album (and the library's grouping by them) or cover is never
 *   replaced; the single-field actions replace explicitly;
 * - the year counts as missing when blank or 1970 (a downloader placeholder): All Fields fills it
 *   then and keeps any other year; Year Only replaces it;
 * - [genre], [artworkBytes] and [releaseDate] are what the caller found online for this match (the
 *   date already validated and unambiguous, see [releaseDateFor]), or null. [genre] may hold several
 *   genres ("Electronic; Synth-pop", see [Genres]): all of them are written;
 * - [bpm] is the tempo found for the track (see BpmFinder), or null. It only ever fills a missing
 *   (or implausible) BPM: an estimate (AcousticBrainz or on-device, both can be half or double
 *   time) never replaces a BPM the file already has, not even with BPM Only. Correcting a tagged
 *   BPM is a manual edit (the inspector's half / double time).
 */
@Suppress("LongParameterList")
fun planFieldFix(
    contentUri: String,
    filePath: String,
    action: MetadataFixAction,
    local: LocalTrackTags,
    match: MetadataCandidate,
    genre: String?,
    artworkBytes: ByteArray?,
    releaseDate: ReleaseDate? = null,
    bpm: BpmLookup.Found? = null,
): FieldFixPlan =
    planFieldFix(
        contentUri,
        filePath,
        action.fields(),
        action == MetadataFixAction.ALL_FIELDS,
        local,
        match,
        genre,
        artworkBytes,
        releaseDate,
        bpm,
    )

/** The tag fields a field selection may change, and whether it is "All Fields" (fill missing values only). */
data class FieldSelection(
    val fields: Set<FixField>,
    val fillOnly: Boolean,
    /** Rename the file after its title ([MetadataField.FILENAME]). */
    val rename: Boolean,
)

fun fieldSelection(selected: Set<MetadataField>): FieldSelection {
    val supported = selected.filter { it.isSupported }.toSet()
    val tags =
        supported
            .mapNotNull {
                when (it) {
                    MetadataField.TITLE -> FixField.TITLE
                    MetadataField.ARTIST -> FixField.ARTIST
                    MetadataField.ALBUM -> FixField.ALBUM
                    MetadataField.YEAR -> FixField.YEAR
                    MetadataField.GENRE -> FixField.GENRE
                    MetadataField.ARTWORK -> FixField.ARTWORK
                    MetadataField.BPM -> FixField.BPM
                    MetadataField.FILENAME -> null
                }
            }.toSet()
    return FieldSelection(tags, fillOnly = supported.containsAll(MetadataField.fillOnlyTags), rename = MetadataField.FILENAME in supported)
}

/** Same as the action-based [planFieldFix], for an individual field selection. */
@Suppress("LongParameterList", "CyclomaticComplexMethod")
fun planFieldFix(
    contentUri: String,
    filePath: String,
    fields: Set<FixField>,
    fillOnly: Boolean,
    local: LocalTrackTags,
    match: MetadataCandidate,
    genre: String?,
    artworkBytes: ByteArray?,
    releaseDate: ReleaseDate? = null,
    bpm: BpmLookup.Found? = null,
): FieldFixPlan {
    // The file's "(feat. …)" note is kept when the online title has none (MusicBrainz titles never do).
    val matchTitle = withLocalFeaturing(match.title, local.title)
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

    val sameTitleSpelling = isUsableTag(local.title) && foldForMatch(local.title) == foldForMatch(matchTitle)
    val title =
        if (fillOnly && FixField.TITLE in fields && sameTitleSpelling) {
            // Same title apart from case or punctuation ("IGOR'S" vs "IGOR’S"): the file's spelling stays.
            null.also { if (local.title.trim() != matchTitle.trim()) notes += "Kept the existing title \"${local.title.trim()}\"" }
        } else {
            text(FixField.TITLE, "title", local.title, matchTitle, keepValidLocal = false)
        }
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
            else -> planGenres(local.genre, genre, fillOnly, changes, notes)
        }

    val newBpm = if (FixField.BPM in fields) planBpm(local, bpm, fillOnly, changes, notes) else null

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
            bpm = newBpm,
            artworkBytes = artwork,
        ).takeIf { it.hasChanges }
    return FieldFixPlan(request, changes, notes)
}

/**
 * The genres to write ([Genres] stored form), or null. Every genre found is kept; the comparison is
 * by genre list, so "Rock; Pop" and "rock;pop" are the same and nothing is rewritten.
 */
private fun planGenres(
    current: String?,
    found: String?,
    fillOnly: Boolean,
    changes: MutableList<FieldFixPlan.Change>,
    notes: MutableList<String>,
): String? {
    val genres = Genres.join(Genres.parse(found))
    val existing = Genres.join(Genres.parse(current))
    return when {
        genres == null -> null.also { notes += "No genre found online" }
        Genres.same(existing, genres) -> null
        fillOnly && existing != null -> null.also { notes += "Kept the existing genre \"$existing\"" }
        else -> genres.also { changes += FieldFixPlan.Change(FixField.GENRE, existing, it) }
    }
}

/** The BPM to write, or null with the reason in [notes]. */
@Suppress("CyclomaticComplexMethod")
private fun planBpm(
    local: LocalTrackTags,
    found: BpmLookup.Found?,
    fillOnly: Boolean,
    changes: MutableList<FieldFixPlan.Change>,
    notes: MutableList<String>,
): Int? {
    val current = usableBpm(local.bpm)
    return when {
        found == null -> null.also { notes += "No BPM found" }
        found.bpm !in PLAUSIBLE_BPM -> null.also { notes += "BPM ${found.bpm} from ${found.source} is not a plausible tempo" }
        fillOnly && !local.tagsReadable -> null.also { notes += "BPM not filled: the file's tags couldn't be read" }
        current == found.bpm -> null
        current != null && fillOnly -> null.also { notes += "Kept the existing BPM $current" }
        current != null && found.estimated ->
            null.also { notes += "Kept the existing BPM $current: an estimate (${found.bpm}, ${found.source}) doesn't replace it" }
        else ->
            found.bpm.also {
                changes += FieldFixPlan.Change(FixField.BPM, current?.toString(), "$it (${found.source})")
            }
    }
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

private val FEATURING_PART = Regex("""(?i)\s*[(\[]\s*(?:feat\.?|ft\.?|featuring|with)\s[^)\]]+[)\]]""")

/**
 * [online] with [local]'s featured-artist note added when only the file has one:
 * "911 / Mr. Lonely" + "… (feat. Frank Ocean & Steve Lacy)" -> "911 / Mr. Lonely (feat. Frank Ocean & Steve Lacy)".
 */
fun withLocalFeaturing(
    online: String,
    local: String,
): String {
    val note = FEATURING_PART.find(local)?.value?.trim()
    return if (note == null || FEATURING_PART.containsMatchIn(online)) online else "${online.trim()} $note"
}
