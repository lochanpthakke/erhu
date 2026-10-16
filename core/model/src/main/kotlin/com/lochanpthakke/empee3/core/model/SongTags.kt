package com.lochanpthakke.empee3.core.model

/**
 * The text a song carries for discovery (VYBE), read from the library as it is: [genre] in the
 * stored form ("Electronic; Synth-pop", see [Genres]), [bpm] the file's BPM tag, [lyricsExcerpt]
 * the start of any lyrics already stored. Nothing is fetched for it.
 */
data class SongTags(
    val songId: Long,
    val title: String,
    val artist: String,
    val album: String,
    val genre: String?,
    val year: Int,
    val bpm: Int?,
    val lyricsExcerpt: String?,
)
