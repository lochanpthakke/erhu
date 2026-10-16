package com.lochanpthakke.empee3.core.vybe

import com.lochanpthakke.empee3.core.model.SongTags

/** What VYBE embeds for a song in discovery, and how a library row becomes a [SongText]. */
object VybeText {
    /** A BPM tag outside this is junk, not a tempo (the same range the metadata fixer writes). */
    @Suppress("MagicNumber")
    val PLAUSIBLE_BPM = 20..400

    /** Bumped whenever [describe] changes, so the index re-embeds every song. */
    const val DOCUMENT_VERSION = 1

    /**
     * The song's discovery description: artist, every genre, tempo and a short lyrics excerpt;
     * whatever is known. Titles and album names are left out: their words matched mood queries
     * by accident ("Late" for "late night", "Dark Fantasy" for "dark", Experiment 3). Exact names
     * are VybeQueryRouter's job.
     */
    fun describe(song: SongText): String =
        listOfNotNull(
            song.artist.takeIf { it.isNotBlank() && it.lowercase() !in UNKNOWN }?.let { "Artist: ${it.trim()}" },
            song.genres.takeIf { it.isNotEmpty() }?.let { (if (it.size == 1) "Genre: " else "Genres: ") + it.joinToString(", ") },
            song.bpm?.takeIf { it in PLAUSIBLE_BPM }?.let { "Tempo: $it BPM" },
            SongDescriber.lyricsExcerpt(song.lyrics)?.let { "Lyrics: $it" },
        ).joinToString(". ").ifEmpty { NOTHING_KNOWN }

    fun fromTags(tags: SongTags): SongText =
        SongText(
            songId = tags.songId,
            title = tags.title,
            artist = tags.artist,
            album = tags.album,
            genre = tags.genre,
            year = tags.year,
            lyrics = tags.lyricsExcerpt,
            bpm = tags.bpm,
        )

    private const val NOTHING_KNOWN = "A song with no artist, genre or tempo information"
    private val UNKNOWN = setOf("<unknown>", "unknown", "unknown artist")
}
