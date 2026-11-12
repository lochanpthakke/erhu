package com.erhu.erhu.feature.library.mix

import com.erhu.erhu.core.model.TrackFacts
import java.time.Instant
import java.time.ZoneId

/**
 * Mixes built from what the library itself says, each only when there are enough songs that
 * really fit: one artist's songs (Spotlight), songs whose year tag is old enough (Throwbacks),
 * short and long songs (Quick Hits, Long Listens) and, once there is some history, the songs not
 * played yet. No mood is guessed: a concept that the tags cannot support is simply not offered.
 */
internal object LibraryMixes {
    private const val SPOTLIGHT_MIN = 6
    private const val THROWBACK_MIN = 8
    private const val LENGTH_MIN = 8
    private const val UNHEARD_MIN = 8

    /** Year tags before this are not trusted (typos, zeros); 1970 is the usual "no date" placeholder. */
    private const val EARLIEST_YEAR = 1950
    private const val PLACEHOLDER_YEAR = 1970

    /** A throwback is at least this many years old. */
    private const val THROWBACK_AGE_YEARS = 10

    private const val QUICK_MIN_MS = 60_000L
    private const val QUICK_MAX_MS = 160_000L
    private const val LONG_MIN_MS = 300_000L
    private const val LONG_MAX_MS = 1_200_000L

    /** Weight of artist taste against freshness in the unplayed mix. */
    private const val UNHEARD_TASTE = 2f

    fun build(profile: ListeningProfile): List<SmartMix> =
        listOfNotNull(spotlight(profile), throwbacks(profile), quickHits(profile), longListens(profile), yetToHear(profile))

    /** One artist's songs: the artist the listener plays most (with enough songs here), else the one with the most songs. */
    fun spotlight(profile: ListeningProfile): SmartMix? {
        val byArtist =
            profile.tracks.values
                .filter { it.songId !in profile.hidden }
                .groupBy { it.artistId }
        val played = profile.artistRanking.firstOrNull { (byArtist[it]?.size ?: 0) >= SPOTLIGHT_MIN }
        val artistId =
            played ?: byArtist.entries
                .filter { it.value.size >= SPOTLIGHT_MIN }
                .maxWithOrNull(compareBy<Map.Entry<Long, List<TrackFacts>>> { it.value.size }.thenBy { -it.key })
                ?.key
        val songs = artistId?.let { byArtist[it] } ?: return null
        val name = songs.first().artist
        val how =
            if (played !=
                null
            ) {
                "The artist you play most, every song of theirs you have"
            } else {
                "The artist with the most songs in your library"
            }
        val ranked = songs.map { it.songId to profile.history(it.songId).plays.toFloat() }
        return SmartMixEngine
            .finish(profile, MixKind.SPOTLIGHT, ranked, SPOTLIGHT_MIN, how, perArtist = Int.MAX_VALUE, perAlbum = Int.MAX_VALUE)
            ?.copy(
                title = "Spotlight: $name",
                noteKey = if (played != null) MixNoteKey.SPOTLIGHT_PLAYS else MixNoteKey.SPOTLIGHT_SONGS,
                spotlightArtist = name,
            )
    }

    /** Songs whose year tag says they are ten years old or more (placeholder and implausible years ignored). */
    fun throwbacks(profile: ListeningProfile): SmartMix? {
        val thisYear = Instant.ofEpochMilli(profile.now).atZone(ZoneId.systemDefault()).year
        val ranked =
            profile.tracks.values
                .filter { track ->
                    track.year.let {
                        it != null &&
                            it >= EARLIEST_YEAR &&
                            it != PLACEHOLDER_YEAR &&
                            it <= thisYear - THROWBACK_AGE_YEARS
                    }
                }.map {
                    it.songId to profile.artist(it) +
                        profile
                            .history(it.songId)
                            .plays
                            .coerceAtMost(1)
                            .toFloat()
                }
        return SmartMixEngine.finish(
            profile,
            MixKind.THROWBACKS,
            ranked,
            THROWBACK_MIN,
            "Songs from ten or more years ago, by their year tags",
        )
    }

    fun quickHits(profile: ListeningProfile): SmartMix? =
        byLength(profile, MixKind.QUICK_HITS, QUICK_MIN_MS..QUICK_MAX_MS, "Short songs from your library, all under 2:40")

    fun longListens(profile: ListeningProfile): SmartMix? =
        byLength(profile, MixKind.LONG_LISTENS, LONG_MIN_MS..LONG_MAX_MS, "Songs from your library that run five minutes or more")

    private fun byLength(
        profile: ListeningProfile,
        kind: MixKind,
        range: LongRange,
        how: String,
    ): SmartMix? {
        val ranked =
            profile.tracks.values
                .filter { it.durationMs in range }
                .map { it.songId to profile.artist(it) }
        return SmartMixEngine.finish(profile, kind, ranked, LENGTH_MIN, how)
    }

    /**
     * Songs never played, artists the listener likes first, newest next. Only once there is real
     * history (otherwise every song is unplayed and the mix would just be the library).
     */
    fun yetToHear(profile: ListeningProfile): SmartMix? {
        val totalPlays = profile.songs.values.sumOf { it.plays }
        if (totalPlays < ListeningProfile.MIN_TOTAL_PLAYS) return null
        val newest =
            profile.tracks.values
                .maxOfOrNull { it.addedAt }
                ?.takeIf { it > 0 } ?: 1L
        val ranked =
            profile.tracks.values
                .filter { profile.history(it.songId).plays == 0 }
                .map { it.songId to UNHEARD_TASTE * profile.artist(it) + it.addedAt.toFloat() / newest }
        return SmartMixEngine.finish(profile, MixKind.YET_TO_HEAR, ranked, UNHEARD_MIN, "Songs in your library you haven't played yet")
    }
}
