package com.lochanpthakke.empee3.core.network.tagger

/**
 * A tag edit for one audio file. Null fields are left exactly as they are in
 * the file; only non-null fields are written.
 *
 * [contentUri] is the MediaStore URI the file is read from and written back
 * through (required under scoped storage). [filePath] is only used to pick
 * the file format (extension) and to ask MediaStore to rescan afterwards.
 */
data class TagWriteRequest(
    val contentUri: String,
    val filePath: String,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val year: Int? = null,
    /**
     * A full or partial release date ("2010-11-19", "2010-11", "2010") written as the file's year
     * tag; takes the place of [year]. Only ever a value [ReleaseDate.tagValue] produced.
     */
    val releaseDate: String? = null,
    val genre: String? = null,
    val trackNumber: Int? = null,
    val bpm: Int? = null,
    val lyrics: String? = null,
    val artworkBytes: ByteArray? = null,
    val clearYear: Boolean = false,
    val clearGenre: Boolean = false,
) {
    val hasChanges: Boolean
        get() =
            title != null ||
                artist != null ||
                album != null ||
                year != null ||
                releaseDate != null ||
                genre != null ||
                trackNumber != null ||
                bpm != null ||
                lyrics != null ||
                artworkBytes != null ||
                clearYear ||
                clearGenre

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TagWriteRequest) return false

        return contentUri == other.contentUri &&
            filePath == other.filePath &&
            title == other.title &&
            artist == other.artist &&
            album == other.album &&
            year == other.year &&
            releaseDate == other.releaseDate &&
            genre == other.genre &&
            trackNumber == other.trackNumber &&
            lyrics == other.lyrics &&
            artworkBytes.contentEquals(other.artworkBytes)
    }

    @Suppress("MagicNumber")
    override fun hashCode(): Int {
        var result = contentUri.hashCode()
        result = 31 * result + filePath.hashCode()
        result = 31 * result + (title?.hashCode() ?: 0)
        result = 31 * result + (artist?.hashCode() ?: 0)
        result = 31 * result + (album?.hashCode() ?: 0)
        result = 31 * result + (year ?: 0)
        result = 31 * result + (releaseDate?.hashCode() ?: 0)
        result = 31 * result + (genre?.hashCode() ?: 0)
        result = 31 * result + (trackNumber ?: 0)
        result = 31 * result + (lyrics?.hashCode() ?: 0)
        result = 31 * result + (artworkBytes?.contentHashCode() ?: 0)
        return result
    }
}

/** Current values shown in the editor, used to send only what the user actually changed. */
data class EditableTags(
    val title: String,
    val artist: String,
    val album: String,
)

/**
 * Builds a request containing only real changes: unchanged or blank text and
 * a year of 0 mean "keep what the file has", so existing tags are never
 * overwritten with empty values. Returns null when nothing changed.
 */
@Suppress("LongParameterList")
fun buildTagWriteRequest(
    contentUri: String,
    filePath: String,
    current: EditableTags,
    title: String,
    artist: String,
    album: String,
    year: Int,
    genre: String,
    bpm: Int = 0,
): TagWriteRequest? {
    fun changed(
        edited: String,
        original: String,
    ): String? = edited.trim().takeIf { it.isNotEmpty() && it != original.trim() }

    val request =
        TagWriteRequest(
            contentUri = contentUri,
            filePath = filePath,
            title = changed(title, current.title),
            artist = changed(artist, current.artist),
            album = changed(album, current.album),
            year = year.takeIf { it in MIN_YEAR..MAX_YEAR },
            genre = genre.trim().takeIf { it.isNotEmpty() },
            bpm = bpm.takeIf { it in 1..999 },
        )
    return request.takeIf { it.hasChanges }
}

private const val MIN_YEAR = 1
private const val MAX_YEAR = 9999

private val MIME_EXTENSIONS =
    mapOf(
        "audio/mpeg" to "mp3",
        "audio/mp3" to "mp3",
        "audio/flac" to "flac",
        "audio/x-flac" to "flac",
        "audio/mp4" to "m4a",
        "audio/x-m4a" to "m4a",
        "audio/aac" to "m4a",
        "audio/ogg" to "ogg",
        "audio/opus" to "opus",
        "audio/wav" to "wav",
        "audio/x-wav" to "wav",
        "audio/aiff" to "aif",
        "audio/x-aiff" to "aif",
        "audio/x-ms-wma" to "wma",
    )

/**
 * The extension JAudioTagger needs to pick a format reader. Taken from the
 * file name, falling back to the MIME type; null when neither is usable.
 */
fun audioFileExtension(
    filePath: String,
    mimeType: String? = null,
): String? {
    val fromName =
        filePath
            .substringAfterLast('/')
            .substringAfterLast('.', missingDelimiterValue = "")
            .lowercase()
            .takeIf { it.isNotEmpty() && it.length <= MAX_EXTENSION_LENGTH && it.all(Char::isLetterOrDigit) }
    return fromName ?: mimeType?.lowercase()?.let { MIME_EXTENSIONS[it] }
}

private const val MAX_EXTENSION_LENGTH = 5
