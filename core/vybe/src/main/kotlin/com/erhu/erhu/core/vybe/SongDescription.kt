package com.erhu.erhu.core.vybe

import com.erhu.erhu.core.model.Genres

/**
 * The text VYBE knows about a song, taken from the library as it is (no fetching):
 * blank or unknown fields are simply left out of its description. [genre] is the library's stored
 * genre value: one genre or several ("Electronic; Synth-pop; Alternative", see [Genres]), read from
 * the file's tags; the metadata fixer writes them only from a provider, never a guess.
 */
data class SongText(
    val songId: Long,
    val title: String,
    val artist: String,
    val album: String,
    val genre: String? = null,
    val year: Int = 0,
    /** Lyrics already stored for the song (embedded tags), if any; only an excerpt is used. */
    val lyrics: String? = null,
    /** The library's BPM (the file's tag), if it has a plausible one. */
    val bpm: Int? = null,
) {
    /** Each genre on its own: "Electronic; Synth-pop" is two genres, not one. */
    val genres: List<String> get() = Genres.parse(genre)
}

/**
 * Builds the one-paragraph description of a song that VYBE embeds. Pure and deterministic.
 *
 * The album name is left out on purpose: on the real library its words dominated mood queries
 * (Experiment 3, 2026-10-09: "late night music" put 7 *Late Registration* songs in the top 10;
 * without album names, 1). Albums are still found exactly through [VybeQueryRouter].
 */
object SongDescriber {
    /** Lyrics beyond this many characters are cut (at a word boundary). */
    const val LYRICS_EXCERPT_CHARS = 240

    private const val MIN_YEAR = 1900
    private const val MAX_YEAR = 2100
    private const val MIN_BPM = 20
    private const val MAX_BPM = 400

    /** LRC line / word timestamps and `[tag:value]` lines: timing, not words. */
    private val LRC_MARKUP = Regex("""\[[^\]]*]|<\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?>""")
    private val WHITESPACE = Regex("""\s+""")
    private val UNKNOWN = setOf("<unknown>", "unknown", "unknown artist", "unknown album")

    fun describe(song: SongText): String =
        listOfNotNull(
            field("Title", song.title),
            field("Artist", song.artist),
            genreField(song.genres),
            song.year.takeIf { it in MIN_YEAR..MAX_YEAR }?.let { "Year: $it" },
            song.bpm?.takeIf { it in MIN_BPM..MAX_BPM }?.let { "Tempo: $it BPM" },
            lyricsExcerpt(song.lyrics)?.let { "Lyrics: $it" },
        ).joinToString(". ")

    /** The first words of the lyrics, timestamps removed, on one line; null when there are none. */
    fun lyricsExcerpt(lyrics: String?): String? =
        lyrics
            ?.replace(LRC_MARKUP, " ")
            ?.replace(WHITESPACE, " ")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { words ->
                if (words.length <= LYRICS_EXCERPT_CHARS) {
                    words
                } else {
                    words.substring(0, words.lastIndexOf(' ', LYRICS_EXCERPT_CHARS).takeIf { it > 0 } ?: LYRICS_EXCERPT_CHARS)
                }
            }

    /** "Genre: Hip-Hop", or "Genres: Electronic, Synth-pop, Alternative" for several. */
    private fun genreField(genres: List<String>): String? =
        genres
            .filter { it.lowercase() !in UNKNOWN }
            .takeIf { it.isNotEmpty() }
            ?.let { (if (it.size == 1) "Genre: " else "Genres: ") + it.joinToString(", ") }

    private fun field(
        label: String,
        value: String?,
    ): String? = value?.trim()?.takeIf { it.isNotEmpty() && it.lowercase() !in UNKNOWN }?.let { "$label: $it" }
}
