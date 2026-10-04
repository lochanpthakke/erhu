package com.lochanpthakke.empee3.core.network.provider

import com.lochanpthakke.empee3.core.network.model.DeezerArtistDto
import java.text.Normalizer
import java.util.Locale

/** The top same-name profile must have at least this many times the fans of the next one. */
internal const val DOMINANCE_FACTOR = 10L

/** How [selectDeezerArtist] decided. */
sealed interface DeezerArtistMatch {
    data class Selected(
        val artistId: Long,
        val pictureUrl: String,
    ) : DeezerArtistMatch

    data class Rejected(
        val reason: String,
    ) : DeezerArtistMatch
}

/**
 * Picks the Deezer profile that is safely [artistName], or rejects all of them.
 * A wrong photo is worse than none, so every doubtful case is [DeezerArtistMatch.Rejected]:
 *
 * 1. Candidates are only profiles whose [normalizeArtistName] equals the artist's; never partial,
 *    fuzzy or "first result" matches.
 * 2. One candidate: accepted if it has a real photo.
 * 3. Several (Deezer has many same-name duplicates): fan count only disambiguates. The top profile
 *    is accepted if it has at least [DOMINANCE_FACTOR]× the fans of the next one (0 fans counts as 1)
 *    and a real photo. A dominant profile without a photo is a rejection, never a fallback to a
 *    smaller duplicate that has one.
 */
fun selectDeezerArtist(
    artistName: String,
    candidates: List<DeezerArtistDto>,
): DeezerArtistMatch {
    val key = normalizeArtistName(artistName)
    val exact = candidates.filter { normalizeArtistName(it.name) == key }.sortedByDescending { it.nbFan }
    val top = exact.firstOrNull()
    val second = exact.getOrNull(1)
    val picture = top?.let(::realPicture)
    return when {
        top == null -> DeezerArtistMatch.Rejected("no exact-name profile")
        second != null && top.nbFan < DOMINANCE_FACTOR * maxOf(second.nbFan, 1L) ->
            DeezerArtistMatch.Rejected("ambiguous: ${exact.size} exact-name profiles, ${top.nbFan} vs ${second.nbFan} fans")
        picture == null -> DeezerArtistMatch.Rejected("profile ${top.id} has no photo")
        else -> DeezerArtistMatch.Selected(top.id, picture)
    }
}

/**
 * Harmless spelling differences only: Unicode NFKC, case, curly quotes and apostrophes,
 * surrounding and repeated whitespace. Characters such as `$ & , .` are kept, so
 * "A$AP Rocky", "$uicideboy$" and "Tyler, The Creator" stay distinct.
 */
fun normalizeArtistName(name: String): String {
    val nfkc = Normalizer.normalize(name, Normalizer.Form.NFKC)
    val straight = nfkc.map { STRAIGHT_QUOTES[it] ?: it }.joinToString("")
    // Upper then lower case folds more than lowercase() alone (e.g. "ß" and "SS").
    return straight
        .uppercase(Locale.ROOT)
        .lowercase(Locale.ROOT)
        .trim()
        .replace(WHITESPACE, " ")
}

private val WHITESPACE = Regex("\\s+")

private val STRAIGHT_QUOTES =
    mapOf(
        '\u2018' to '\'',
        '\u2019' to '\'',
        '\u201B' to '\'',
        '\u02BC' to '\'',
        '\u2032' to '\'',
        '\u201C' to '"',
        '\u201D' to '"',
        '\u201F' to '"',
        '\u2033' to '"',
    )

/** Deezer returns `…/images/artist//…` (no image hash) for a profile without a photo. */
private const val BLANK_PICTURE_MARKER = "/artist//"

private fun realPicture(artist: DeezerArtistDto): String? =
    listOf(artist.pictureBig, artist.pictureXl, artist.pictureMedium)
        .firstOrNull { !it.isNullOrBlank() }
        ?.takeUnless { BLANK_PICTURE_MARKER in it }
