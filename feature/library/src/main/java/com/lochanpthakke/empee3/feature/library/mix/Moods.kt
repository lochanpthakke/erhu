package com.lochanpthakke.empee3.feature.library.mix

import com.lochanpthakke.empee3.core.model.TrackFacts

/**
 * Conservative mood matching for the mood mixes. A song's genre tag decides; its BPM only
 * supports or vetoes a genre, never places a song on its own (no genre, no mood).
 *
 * BPM tags are often half- or double-time (a 140 BPM track tagged 70, or the reverse), so only
 * values from [TRUSTED_MIN] to [TRUSTED_MAX] are read as a tempo at all; outside that range the
 * tempo counts as unknown.
 */
internal object Moods {
    private const val TRUSTED_MIN = 70
    private const val TRUSTED_MAX = 175

    /** Fast enough to make a song of a non-mood genre upbeat. */
    private const val FAST_MIN = 125

    /** Upbeat: vetoes calm and late-night matches. */
    private const val UPBEAT_MIN = 118
    private const val SLOW_MAX = 110

    private val ENERGY =
        listOf(
            "metal",
            "punk",
            "hardcore",
            "drum and bass",
            "drum & bass",
            "dnb",
            "edm",
            "dance",
            "house",
            "techno",
            "trance",
            "dubstep",
            "hardstyle",
            "electro house",
            "trap",
            "drill",
            "grime",
            "rock",
            "hyperpop",
            "phonk",
            "jungle",
            "breakbeat",
            "garage",
            "workout",
            "rage",
        )

    /** Clearly relaxed genres: the Chill mix. */
    private val CALM =
        listOf(
            "ambient",
            "chill",
            "lo-fi",
            "lofi",
            "acoustic",
            "folk",
            "bossa",
            "downtempo",
            "easy listening",
            "lounge",
            "new age",
            "dream pop",
            "bedroom pop",
            "soft rock",
            "piano",
            "classical",
            "singer-songwriter",
            "meditation",
            "sleep",
            "jazz",
        )

    /** Calm genres that name themselves so: the tempo never vetoes these. */
    private val EXPLICITLY_CALM = listOf("ambient", "chill", "lo-fi", "lofi", "sleep", "meditation")

    /** Night-time listening: smooth, moody, atmospheric. */
    private val NIGHT =
        listOf(
            "r&b",
            "rnb",
            "soul",
            "jazz",
            "trip hop",
            "trip-hop",
            "downtempo",
            "ambient",
            "synthwave",
            "darkwave",
            "chillwave",
            "lo-fi",
            "lofi",
            "dream pop",
            "slowcore",
            "blues",
            "neo soul",
            "alternative r&b",
            "shoegaze",
            "nocturn",
        )

    fun isHighEnergy(track: TrackFacts): Boolean {
        val genres = genresOf(track)
        val tempo = tempo(track)
        return when {
            genres.isEmpty() || genres.anyOf(CALM) -> false
            genres.anyOf(ENERGY) -> tempo == null || tempo >= UPBEAT_MIN
            // A known genre that is neither: only a clearly fast, trusted tempo makes it upbeat.
            else -> tempo != null && tempo >= FAST_MIN && !genres.anyOf(NIGHT)
        }
    }

    fun isChill(track: TrackFacts): Boolean {
        val genres = genresOf(track)
        val tempo = tempo(track)
        return when {
            !genres.anyOf(CALM) -> false
            genres.any { g -> ENERGY.any(g::contains) && !g.contains("soft rock") } -> false
            genres.anyOf(EXPLICITLY_CALM) -> true
            // An upbeat trusted tempo vetoes a calm-ish genre (folk-punk energy, uptempo jazz).
            else -> tempo == null || tempo < UPBEAT_MIN
        }
    }

    fun isLateNight(track: TrackFacts): Boolean {
        val genres = genresOf(track)
        val tempo = tempo(track)
        return genres.anyOf(NIGHT) && !genres.anyOf(ENERGY) && (tempo == null || tempo <= SLOW_MAX)
    }

    /** The BPM when it can be trusted as the song's tempo; null when missing or possibly half / double time. */
    fun tempo(track: TrackFacts): Int? = track.bpm?.takeIf { it in TRUSTED_MIN..TRUSTED_MAX }

    private fun List<String>.anyOf(keywords: List<String>): Boolean = any { genre -> keywords.any(genre::contains) }
}
