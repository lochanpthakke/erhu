package com.lochanpthakke.empee3.core.model

/**
 * What the recommendations know about a song in the library: its identity, grouping and the
 * tags that are actually stored (genre and BPM are often missing, and are null then).
 */
data class TrackFacts(
    val songId: Long,
    val title: String,
    val artistId: Long,
    val artist: String,
    val albumId: Long,
    val album: String,
    val genre: String?,
    /** The file's BPM tag when plausible; may be half- or double-time. */
    val bpm: Int?,
    val durationMs: Long,
    val isFavorite: Boolean,
    /** MediaStore modification time of the file, seconds (when it was added or last changed). */
    val addedAt: Long,
    /** The year tag as stored: null, 0 or a placeholder (1970) when the file has no real year. */
    val year: Int? = null,
)

/**
 * A song's counted plays in one local calendar month (a play counts once half of the song was
 * actually heard), and the time of the month's latest play.
 */
data class MonthlyPlays(
    val songId: Long,
    /** `YYYY-MM`. */
    val month: String,
    val plays: Int,
    val lastPlayedAt: Long,
)
