package com.erhu.erhu.core.network.tagger

import com.erhu.erhu.core.model.Genres
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.flac.FlacTag
import org.jaudiotagger.tag.id3.ID3v24Tag
import org.jaudiotagger.tag.reference.GenreTypes
import org.jaudiotagger.tag.vorbiscomment.VorbisCommentTag

/**
 * Whether this tag format keeps several genres as separate values that other players read as such:
 * ID3v2.4 (NUL-separated TCON) and Vorbis comments (FLAC, Ogg; one GENRE field each).
 *
 * ID3v2.3 has no multi-value text frames: jaudiotagger 3.0.1 still writes NUL-separated values
 * there, but turns known genre names into "(52)" references, and the result reads back corrupted
 * ("Electronic", "", "Synth-pop17"). WAV stores an ID3v2.3 chunk and an INFO chunk (single value),
 * and MP4 has one genre atom. Those formats get the genres as one "; "-joined string, which this app
 * splits again on reading (other players may show it as one genre).
 */
fun Tag.holdsSeparateGenres(): Boolean = this is ID3v24Tag || this is FlacTag || this is VorbisCommentTag

/** Replaces the tag's genres with [genres] in the form [holdsSeparateGenres] allows. */
fun Tag.writeGenres(genres: List<String>) {
    require(genres.isNotEmpty()) { "No genre to write" }
    deleteField(FieldKey.GENRE)
    when {
        genres.size == 1 -> setField(FieldKey.GENRE, genres.single())
        holdsSeparateGenres() -> genres.forEach { addField(FieldKey.GENRE, it) }
        else -> setField(FieldKey.GENRE, genres.joinToString(Genres.SEPARATOR))
    }
}

/**
 * The tag's genres, whichever way they were stored: separate values, one "; "-joined string, or
 * ID3 numeric references ("(17)", "17" -> "Rock"). Empty when there are none.
 */
fun Tag.readGenres(): List<String> {
    val values = getAll(FieldKey.GENRE).orEmpty()
    // getFirst resolves a lone ID3 genre reference itself; getAll returns the values as stored.
    val raw = if (values.size <= 1) listOf(getFirst(FieldKey.GENRE)) else values.map(::genreName)
    return Genres.normalize(raw.flatMap { Genres.parse(it) })
}

private val ID3_GENRE_REFERENCE = Regex("""^\(?(\d{1,3})\)?$""")

/** An ID3v1 genre reference ("(17)" or "17") as its name; any other text as it is. */
private fun genreName(value: String): String =
    ID3_GENRE_REFERENCE
        .find(value.trim())
        ?.groupValues
        ?.get(1)
        ?.toIntOrNull()
        ?.let { GenreTypes.getInstanceOf().getValueForId(it) }
        ?: value
