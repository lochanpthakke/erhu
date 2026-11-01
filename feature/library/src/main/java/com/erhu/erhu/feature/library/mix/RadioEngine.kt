package com.erhu.erhu.feature.library.mix

import com.erhu.erhu.core.model.TrackFacts
import kotlin.math.abs

/** What a radio starts from. [key] keeps tie-breaking stable per seed. */
sealed interface RadioSeed {
    val key: String

    data class Song(
        val songId: Long,
    ) : RadioSeed {
        override val key: String get() = "song:$songId"
    }

    data class Album(
        val albumId: Long,
    ) : RadioSeed {
        override val key: String get() = "album:$albumId"
    }

    data class Artist(
        val artistId: Long,
    ) : RadioSeed {
        override val key: String get() = "artist:$artistId"
    }
}

/** A signal that actually shaped at least one picked song (only these are named to the user). */
enum class RadioSignal(
    val label: String,
) {
    SOUND("VYBE sound match"),
    GENRE("shared genres"),
    TEMPO("similar tempo"),
    SAME_ARTIST("the same artist"),
    LISTENING("your listening"),
}

/**
 * A built radio: [startSongId] plays first (the seed song, or the album's / artist's most played
 * track), then [songIds] in order. [basedOn]: the signals that shaped the picks, nothing assumed.
 */
data class RadioPlan(
    val seed: RadioSeed,
    val name: String,
    val startSongId: Long,
    val songIds: List<Long>,
    val basedOn: List<RadioSignal>,
)

/**
 * Smart Radio: a queue from one song, album or artist, built from the listener's own library.
 *
 * Each other song is scored only by what is known about it: VYBE's similarity to the seed songs'
 * stored vectors (when there is an index), genre overlap, tempo closeness (only BPM tags trusted
 * as a tempo, see [Moods.tempo]), being by the seed's artist or on its album, and familiarity
 * (lasting plays, favourites, a liked artist) balanced by a small bonus for songs never played.
 * Missing data scores nothing; it is never guessed. The order is then spread out: a few songs per
 * artist and album at most, and never the same artist twice in a row while another fits.
 *
 * Deterministic: the same library, history, similarity and seed give the same radio; equal scores
 * fall back to a fixed hash of song and seed.
 */
object RadioEngine {
    const val LENGTH = 30

    private const val W_SOUND = 1f
    private const val W_GENRE = 0.6f
    private const val W_TEMPO = 0.2f
    private const val W_FAMILIAR = 0.25f
    private const val W_LIKED_ARTIST = 0.15f
    private const val W_UNHEARD = 0.1f
    private const val FAVOURITE_FAMILIARITY = 0.5f

    /** BPM difference at which tempo stops counting. */
    private const val TEMPO_SPAN = 30f

    /** Fewest unsuggested songs a refresh draws from before earlier picks may return. */
    private const val REFRESH_MIN = 10

    private const val PER_ARTIST = 3
    private const val PER_ALBUM = 2

    /** An artist radio is mostly that artist's world: their own songs may fill a third of it. */
    private const val SEED_ARTIST_SHARE = 3

    /** The seed's own songs (one for a song seed), in a fixed order; empty when the seed is unknown. */
    fun anchors(
        profile: ListeningProfile,
        seed: RadioSeed,
    ): List<TrackFacts> =
        when (seed) {
            is RadioSeed.Song -> listOfNotNull(profile.tracks[seed.songId])
            is RadioSeed.Album -> profile.tracks.values.filter { it.albumId == seed.albumId }
            is RadioSeed.Artist -> profile.tracks.values.filter { it.artistId == seed.artistId }
        }.sortedBy { it.songId }

    /**
     * The radio for [seed], or null when the seed has no songs. [similarity]: VYBE's cosine of each
     * song to the anchors' centroid (null without an index). [exclude]: songs not to pick (earlier
     * picks, when refreshing); they come back only if too few other songs are left.
     */
    @Suppress("LongParameterList")
    fun build(
        profile: ListeningProfile,
        seed: RadioSeed,
        similarity: Map<Long, Float>?,
        exclude: Set<Long> = emptySet(),
        startSongId: Long? = null,
        length: Int = LENGTH,
    ): RadioPlan? {
        val anchors = anchors(profile, seed)
        if (anchors.isEmpty()) return null
        val start = startSongId?.takeIf { it in profile.tracks } ?: startOf(profile, seed, anchors)
        val reference = Reference.of(profile, seed, anchors)
        val open = profile.tracks.values.filter { it.songId != start && it.songId !in profile.hidden }
        val fresh = open.filter { it.songId !in exclude }
        // Fresh picks while a fair number remain (a refresh may come out a little shorter).
        val pool = if (fresh.size >= minOf(length, REFRESH_MIN) || fresh.size == open.size) fresh else open
        val poolIds = pool.mapTo(HashSet()) { it.songId }
        val sound = similarity?.filterKeys { it in poolIds }?.let(::rankNormalised).orEmpty()
        val scored = pool.map { score(profile, reference, sound, it) }
        val picked = spread(scored, seed, reference, length)
        return RadioPlan(
            seed = seed,
            name = nameOf(seed, anchors),
            startSongId = start,
            songIds = picked.map { it.track.songId },
            basedOn = RadioSignal.entries.filter { signal -> picked.any { signal in it.signals } },
        )
    }

    /** The seed song; else the most-played (then first) song of the album or artist. */
    private fun startOf(
        profile: ListeningProfile,
        seed: RadioSeed,
        anchors: List<TrackFacts>,
    ): Long =
        when (seed) {
            is RadioSeed.Song -> seed.songId
            else ->
                anchors
                    .sortedWith(compareByDescending<TrackFacts> { profile.history(it.songId).lasting }.thenBy { it.songId })
                    .first()
                    .songId
        }

    private fun nameOf(
        seed: RadioSeed,
        anchors: List<TrackFacts>,
    ): String =
        when (seed) {
            is RadioSeed.Song -> anchors.first().title
            is RadioSeed.Album -> anchors.first().album
            is RadioSeed.Artist -> anchors.first().artist
        }

    /** What the seed stands for: its genres, typical trusted tempo, artists and albums. */
    private class Reference(
        val genres: Set<String>,
        val tempo: Int?,
        val artists: Set<Long>,
        val albums: Set<Long>,
        /** Weight of being by the seed's artist: strong for an artist radio, moderate otherwise. */
        val artistWeight: Float,
        val maxLasting: Float,
    ) {
        companion object {
            fun of(
                profile: ListeningProfile,
                seed: RadioSeed,
                anchors: List<TrackFacts>,
            ): Reference {
                val tempos = anchors.mapNotNull(Moods::tempo).sorted()
                return Reference(
                    genres = anchors.flatMap(::genresOf).toSet(),
                    tempo = tempos.getOrNull(tempos.size / 2),
                    artists = anchors.map { it.artistId }.toSet(),
                    // An album radio looks beyond the album; its own songs only through the artist.
                    albums = if (seed is RadioSeed.Album) emptySet() else anchors.map { it.albumId }.toSet(),
                    artistWeight = if (seed is RadioSeed.Artist) SEED_ARTIST else SAME_ARTIST,
                    maxLasting =
                        profile.songs.values
                            .maxOfOrNull { it.lasting }
                            ?.takeIf { it > 0f } ?: 1f,
                )
            }
        }
    }

    private class Scored(
        val track: TrackFacts,
        val score: Float,
        val signals: Set<RadioSignal>,
    )

    private const val SAME_ARTIST = 0.35f
    private const val SAME_ALBUM = 0.15f
    private const val SEED_ARTIST = 0.8f

    private fun score(
        profile: ListeningProfile,
        reference: Reference,
        sound: Map<Long, Float>,
        track: TrackFacts,
    ): Scored {
        val signals = mutableSetOf<RadioSignal>()
        var total = 0f

        sound[track.songId]?.let { similarity ->
            total += W_SOUND * similarity
            if (similarity > 0f) signals += RadioSignal.SOUND
        }

        genreMatch(track, reference)?.let { match ->
            total += W_GENRE * match
            signals += RadioSignal.GENRE
        }
        tempoMatch(track, reference)?.let { match ->
            total += W_TEMPO * match
            signals += RadioSignal.TEMPO
        }

        if (track.artistId in reference.artists) {
            total += reference.artistWeight
            signals += RadioSignal.SAME_ARTIST
        }
        if (track.albumId in reference.albums) total += SAME_ALBUM

        val history = profile.history(track.songId)
        val familiar = (history.lasting / reference.maxLasting + if (track.isFavorite) FAVOURITE_FAMILIARITY else 0f).coerceAtMost(1f)
        val liked = profile.artist(track)
        if (familiar > 0f || liked > 0f) {
            total += W_FAMILIAR * familiar + W_LIKED_ARTIST * liked
            signals += RadioSignal.LISTENING
        }
        if (history.plays == 0) total += W_UNHEARD

        return Scored(track, total, signals)
    }

    /** Share of genres in common (0..1]; null when either side has no genre tag or none is shared. */
    private fun genreMatch(
        track: TrackFacts,
        reference: Reference,
    ): Float? {
        val genres = genresOf(track)
        val shared = genres.count { it in reference.genres }
        return if (shared > 0) shared.toFloat() / minOf(genres.size, reference.genres.size) else null
    }

    /** Closeness of trusted tempos (0..1]; null when either is unknown or they are too far apart. */
    private fun tempoMatch(
        track: TrackFacts,
        reference: Reference,
    ): Float? {
        val tempo = Moods.tempo(track)
        val target = reference.tempo
        return if (tempo == null || target == null) null else (1f - abs(tempo - target) / TEMPO_SPAN).takeIf { it > 0f }
    }

    /**
     * Best first within caps (a few per artist and album; the seed artist may fill a third of an
     * artist radio), never the same artist twice in a row while another song fits. When the caps
     * leave the radio short (a library of few artists), they are relaxed step by step to fill it.
     */
    private fun spread(
        scored: List<Scored>,
        seed: RadioSeed,
        reference: Reference,
        length: Int,
    ): List<Scored> {
        val remaining =
            scored
                .sortedWith(
                    compareByDescending<Scored> { it.score }
                        .thenBy { radioHash(it.track.songId, seed.key) }
                        .thenBy { it.track.songId },
                ).toMutableList()
        val seedArtistCap = if (seed is RadioSeed.Artist) (length / SEED_ARTIST_SHARE).coerceAtLeast(PER_ARTIST) else PER_ARTIST
        val artists = HashMap<Long, Int>()
        val albums = HashMap<Long, Int>()
        val picked = mutableListOf<Scored>()
        for (relax in CAP_STEPS) {
            while (picked.size < length) {
                val previous = picked.lastOrNull()?.track?.artistId
                val fits =
                    remaining.filter { candidate ->
                        val track = candidate.track
                        (artists[track.artistId] ?: 0) < artistCap(track, seed, reference, seedArtistCap, relax) &&
                            (albums[track.albumId] ?: 0) < albumCap(track, seed, relax)
                    }
                // A repeat of the previous artist only once even the loosest caps offer nothing else.
                val next =
                    fits.firstOrNull { it.track.artistId != previous }
                        ?: fits.firstOrNull()?.takeIf { relax == CAP_STEPS.last() }
                        ?: break
                remaining.remove(next)
                picked += next
                artists.merge(next.track.artistId, 1, Int::plus)
                albums.merge(next.track.albumId, 1, Int::plus)
            }
        }
        return picked
    }

    private fun artistCap(
        track: TrackFacts,
        seed: RadioSeed,
        reference: Reference,
        seedArtistCap: Int,
        relax: Int,
    ): Int =
        when {
            // An artist radio stays at most a third that artist, even when short.
            seed is RadioSeed.Artist && track.artistId in reference.artists -> seedArtistCap
            track.artistId in reference.artists -> seedArtistCap * relax
            else -> PER_ARTIST * relax
        }

    /** An album radio keeps at most two more of that album, even when short. */
    private fun albumCap(
        track: TrackFacts,
        seed: RadioSeed,
        relax: Int,
    ): Int = if (seed is RadioSeed.Album && track.albumId == seed.albumId) PER_ALBUM else PER_ALBUM * relax

    /** Cap multipliers: the strict caps first, then looser ones only if the radio is still short. */
    private val CAP_STEPS = listOf(1, 2, UNCAPPED)
    private const val UNCAPPED = 1_000

    /** Scales similarities to 0..1 across the candidates (the closest is 1). */
    private fun rankNormalised(similarity: Map<Long, Float>): Map<Long, Float> {
        val finite = similarity.filterValues { it.isFinite() }
        val min = finite.values.minOrNull() ?: 0f
        val span = (finite.values.maxOrNull() ?: 0f) - min
        return finite.mapValues { if (span > 0f) (it.value - min) / span else 1f }
    }

    /** A fixed scramble of song and seed (SplitMix64 finaliser; String.hashCode is specified, so stable). */
    private fun radioHash(
        songId: Long,
        key: String,
    ): Long {
        var h = songId * GOLDEN_GAMMA + key.hashCode()
        h = (h xor (h ushr MIX_SHIFT)) * MIX_MULTIPLIER
        return h xor (h ushr MIX_SHIFT)
    }

    private const val GOLDEN_GAMMA = -0x61c8864680b583ebL
    private const val MIX_MULTIPLIER = -0xae502812aa7333L
    private const val MIX_SHIFT = 33
}
