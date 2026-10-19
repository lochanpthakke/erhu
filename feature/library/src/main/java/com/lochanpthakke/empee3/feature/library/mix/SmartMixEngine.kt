package com.lochanpthakke.empee3.feature.library.mix

import com.lochanpthakke.empee3.core.model.MonthlyPlays
import com.lochanpthakke.empee3.core.model.TrackFacts
import kotlin.math.ln
import kotlin.math.pow

/**
 * What the listening history says about each song, artist, album and genre. Built once per data
 * change ([SmartMixEngine.profile]) and shared by every mix.
 *
 * Signals actually stored by the app: counted plays per song and calendar month with the time of
 * the month's latest play, favourites, and the genre / BPM tags. There is no skip history, so
 * nothing is inferred from skips.
 */
class ListeningProfile internal constructor(
    val tracks: Map<Long, TrackFacts>,
    internal val songs: Map<Long, SongHistory>,
    internal val artistAffinity: Map<Long, Float>,
    internal val albumAffinity: Map<Long, Float>,
    internal val genreWeight: Map<String, Float>,
    /** Artists by affinity, strongest first. */
    internal val artistRanking: List<Long>,
    val now: Long,
    /** Songs the listener hid from Smart Playlists: never offered, though their plays still shape the taste. */
    internal val hidden: Set<Long> = emptySet(),
) {
    /** Enough history for mixes that claim to know the listener. */
    val isPersonal: Boolean
        get() = songs.count { it.value.plays > 0 } >= MIN_PLAYED_SONGS && songs.values.sumOf { it.plays } >= MIN_TOTAL_PLAYS

    internal fun history(songId: Long): SongHistory = songs[songId] ?: SongHistory.NONE

    internal fun artist(track: TrackFacts): Float = artistAffinity[track.artistId] ?: 0f

    internal fun album(track: TrackFacts): Float = albumAffinity[track.albumId] ?: 0f

    /** How well [track]'s genres match the listener's (0..1); 0 without genre tags. */
    internal fun genre(track: TrackFacts): Float = genresOf(track).maxOfOrNull { genreWeight[it] ?: 0f } ?: 0f

    internal companion object {
        const val MIN_PLAYED_SONGS = 5
        const val MIN_TOTAL_PLAYS = 10
    }
}

/** A song's play history: total counted plays, latest play, and plays weighted by how recent they are. */
internal data class SongHistory(
    val plays: Int,
    val lastPlayedAt: Long,
    /** Plays decayed with a short half-life: what is being played now. */
    val recent: Float,
    /** Plays decayed with a long half-life: lasting taste. */
    val lasting: Float,
) {
    companion object {
        val NONE = SongHistory(0, 0L, 0f, 0f)
    }
}

/**
 * Builds the smart mixes from local data only. Deterministic: the same library, history, time
 * and taste neighbours always give the same mixes, in the same order (ties broken by a fixed
 * hash of the song and mix), so results are stable between launches and change only as the
 * listening does.
 */
object SmartMixEngine {
    private const val DAY_MS = 86_400_000L

    /** "In rotation" fades with a three-week half-life; lasting taste with a four-month one. */
    private const val RECENT_HALF_LIFE_DAYS = 21.0
    private const val LASTING_HALF_LIFE_DAYS = 120.0

    /** A favourite counts like this many lasting plays toward its artist and album. */
    private const val FAVOURITE_WEIGHT = 2f

    private const val MIX_LIMIT = 40

    /** Mixes of the listener's own plays show what they played, with only a loose artist cap. */
    private const val LOOSE_ARTIST_CAP = 8

    /** Seeds for VYBE's taste neighbours: the listener's most lasting songs. */
    private const val TASTE_SEEDS = 15

    fun profile(
        tracks: List<TrackFacts>,
        plays: List<MonthlyPlays>,
        now: Long,
        hidden: Set<Long> = emptySet(),
    ): ListeningProfile {
        val byId = tracks.associateBy { it.songId }
        val songs = HashMap<Long, SongHistory>()
        for ((songId, rows) in plays.filter { it.plays > 0 && it.songId in byId }.groupBy { it.songId }) {
            var recent = 0.0
            var lasting = 0.0
            for (row in rows) {
                // Each month's plays are dated by that month's latest play (the finest date stored).
                val ageDays = ((now - row.lastPlayedAt).coerceAtLeast(0L)).toDouble() / DAY_MS
                recent += row.plays * 0.5.pow(ageDays / RECENT_HALF_LIFE_DAYS)
                lasting += row.plays * 0.5.pow(ageDays / LASTING_HALF_LIFE_DAYS)
            }
            songs[songId] = SongHistory(rows.sumOf { it.plays }, rows.maxOf { it.lastPlayedAt }, recent.toFloat(), lasting.toFloat())
        }

        val artistRaw = HashMap<Long, Float>()
        val albumRaw = HashMap<Long, Float>()
        val genreRaw = HashMap<String, Float>()
        for (track in tracks) {
            val weight = (songs[track.songId]?.lasting ?: 0f) + if (track.isFavorite) FAVOURITE_WEIGHT else 0f
            if (weight <= 0f) continue
            artistRaw.merge(track.artistId, weight, Float::plus)
            albumRaw.merge(track.albumId, weight, Float::plus)
            genresOf(track).forEach { genreRaw.merge(it, weight, Float::plus) }
        }
        return ListeningProfile(
            tracks = byId,
            songs = songs,
            artistAffinity = normalised(artistRaw),
            albumAffinity = normalised(albumRaw),
            genreWeight = normalised(genreRaw),
            hidden = hidden,
            artistRanking =
                artistRaw.entries
                    .sortedWith(
                        compareByDescending<Map.Entry<Long, Float>> { it.value }.thenBy { it.key },
                    ).map { it.key },
            now = now,
        )
    }

    /** The songs (with weights) that define the listener's taste, for VYBE's taste neighbours. */
    fun tasteSeeds(profile: ListeningProfile): Map<Long, Float> =
        profile.songs.entries
            .filter { it.value.lasting > 0f }
            .sortedWith(compareByDescending<Map.Entry<Long, SongHistory>> { it.value.lasting }.thenBy { it.key })
            .take(TASTE_SEEDS)
            .associate { it.key to it.value.lasting }

    /**
     * Every mix with enough songs to be worth offering. [taste]: VYBE's similarity of songs to the
     * listener's taste (song id -> cosine), or null when VYBE has no index yet.
     */
    fun build(
        profile: ListeningProfile,
        taste: Map<Long, Float>?,
    ): SmartMixes {
        val personal = profile.isPersonal
        val built = mutableListOf<SmartMix>()
        if (personal) {
            rotation(profile)?.let(built::add)
            yourSound(profile, taste)?.let(built::add)
            forgotten(profile)?.let(built::add)
        }
        val deepCuts = if (personal) deepCuts(profile) else null
        deepCuts?.let(built::add)
        mood(profile, MixKind.HIGH_ENERGY, Moods::isHighEnergy)?.let(built::add)
        mood(profile, MixKind.CHILL, Moods::isChill)?.let(built::add)
        mood(profile, MixKind.LATE_NIGHT, Moods::isLateNight)?.let(built::add)
        if (personal) aLittleDifferent(profile, taste, exclude = deepCuts?.songIds.orEmpty().toSet())?.let(built::add)
        built += LibraryMixes.build(profile)
        if (built.count { it.kind.personal } < MIN_PERSONAL_BEFORE_FALLBACKS) {
            favourites(profile)?.let(built::add)
            fresh(profile)?.let(built::add)
        }
        return SmartMixes(built.sortedBy { it.kind.ordinal }, needsMoreListening = !personal)
    }

    private const val MIN_PERSONAL_BEFORE_FALLBACKS = 3

    // ---- In Your Rotation: played often, lately. Recent plays dominate; all-time plays only nudge.

    private const val ROTATION_WINDOW_DAYS = 45
    private const val ROTATION_MIN = 5

    internal fun rotation(profile: ListeningProfile): SmartMix? {
        val since = profile.now - ROTATION_WINDOW_DAYS * DAY_MS
        val ranked =
            profile.songs.entries
                .filter { (_, h) -> h.plays >= 2 && h.lastPlayedAt >= since }
                .map { (id, h) -> id to (h.recent + ALL_TIME_NUDGE * ln(1f + h.plays)) }
        // What is actually on repeat, even if it is mostly one artist: only a loose artist cap.
        return finish(profile, MixKind.ROTATION, ranked, ROTATION_MIN, "What you've been playing most lately", perArtist = LOOSE_ARTIST_CAP)
    }

    private const val ALL_TIME_NUDGE = 0.1f

    // ---- Forgotten Favourites: played meaningfully before, not for a while.

    private const val FORGOTTEN_AFTER_DAYS = 60
    private const val FORGOTTEN_MIN_PLAYS = 3
    private const val FORGOTTEN_MIN = 5

    internal fun forgotten(profile: ListeningProfile): SmartMix? {
        val before = profile.now - FORGOTTEN_AFTER_DAYS * DAY_MS
        val ranked =
            profile.songs.entries
                .filter { (_, h) -> h.plays >= FORGOTTEN_MIN_PLAYS && h.lastPlayedAt < before }
                .map { (id, h) ->
                    val favourite = if (profile.tracks[id]?.isFavorite == true) FAVOURITE_WEIGHT else 0f
                    id to (h.plays + favourite)
                }
        return finish(
            profile,
            MixKind.FORGOTTEN,
            ranked,
            FORGOTTEN_MIN,
            "Songs you played a lot, not heard for two months or more",
            perArtist = LOOSE_ARTIST_CAP,
        )
    }

    // ---- Deep Cuts: rarely or never played songs by the artists and albums the listener enjoys.

    private const val DEEP_CUT_MAX_PLAYS = 1
    private const val ENJOYED_ARTIST = 0.15f
    private const val DEEP_CUTS_MIN = 6

    internal fun deepCuts(profile: ListeningProfile): SmartMix? {
        val ranked =
            profile.tracks.values
                .filter { profile.history(it.songId).plays <= DEEP_CUT_MAX_PLAYS && profile.artist(it) >= ENJOYED_ARTIST }
                .map { track ->
                    val unheard = if (profile.history(track.songId).plays == 0) UNHEARD_BONUS else 0f
                    track.songId to (ARTIST_SHARE * profile.artist(track) + ALBUM_SHARE * profile.album(track) + unheard)
                }
        return finish(
            profile,
            MixKind.DEEP_CUTS,
            ranked,
            DEEP_CUTS_MIN,
            "Less-played songs by artists you love",
            perArtist = 4,
            perAlbum = 3,
        )
    }

    private const val ARTIST_SHARE = 0.6f
    private const val ALBUM_SHARE = 0.4f
    private const val UNHEARD_BONUS = 0.1f

    // ---- Your Kind of Sound: songs that match the established taste, not the ones on repeat.

    private const val YOUR_SOUND_REST_DAYS = 14
    private const val YOUR_SOUND_MIN = 10

    internal fun yourSound(
        profile: ListeningProfile,
        taste: Map<Long, Float>?,
    ): SmartMix? {
        val vybe = taste?.takeIf { it.isNotEmpty() }
        val hasGenres = profile.genreWeight.isNotEmpty()
        // Without VYBE or genre tags this would just be "more of the same artists": leave it out.
        if (vybe == null && !hasGenres) return null
        val restSince = profile.now - YOUR_SOUND_REST_DAYS * DAY_MS
        val seeds = tasteSeeds(profile).keys
        val similarity = vybe?.let(::rankNormalised).orEmpty()
        val ranked =
            profile.tracks.values
                .filter { it.songId !in seeds && profile.history(it.songId).lastPlayedAt < restSince }
                .mapNotNull { track ->
                    val sound = similarity[track.songId] ?: 0f
                    val genre = profile.genre(track)
                    val artist = profile.artist(track)
                    val score =
                        if (vybe != null) {
                            SOUND_VYBE * sound + SOUND_GENRE_WITH_VYBE * genre + SOUND_ARTIST_WITH_VYBE * artist
                        } else {
                            SOUND_GENRE * genre + SOUND_ARTIST * artist
                        }
                    (track.songId to score).takeIf { sound > 0f || genre > 0f }
                }
        val how = if (vybe != null) "Close to the songs you play most, matched by VYBE" else "From the genres and artists you play most"
        return finish(profile, MixKind.YOUR_SOUND, ranked, YOUR_SOUND_MIN, how, perArtist = 3)
    }

    private const val SOUND_VYBE = 0.55f
    private const val SOUND_GENRE_WITH_VYBE = 0.25f
    private const val SOUND_ARTIST_WITH_VYBE = 0.2f
    private const val SOUND_GENRE = 0.6f
    private const val SOUND_ARTIST = 0.4f

    // ---- A Little Different: lesser-heard songs by artists just outside the favourites, and by
    // unfamiliar artists in familiar genres (or close by VYBE), alternating.

    private const val TOP_ARTISTS = 5
    private const val FAMILIAR_ARTISTS = 30
    private const val DIFFERENT_MIN = 8
    private const val DIFFERENT_GENRE = 0.3f
    private const val DIFFERENT_FAMILIAR_GENRE = 0.5f
    private const val DIFFERENT_PER_ARTIST = 2
    private const val HALVES = 2

    internal fun aLittleDifferent(
        profile: ListeningProfile,
        taste: Map<Long, Float>?,
        exclude: Set<Long>,
    ): SmartMix? {
        val outerArtists =
            profile.artistRanking
                .drop(TOP_ARTISTS)
                .take(FAMILIAR_ARTISTS - TOP_ARTISTS)
                .toSet()
        val similarity = taste?.let(::rankNormalised).orEmpty()
        val open = profile.tracks.values.filter { it.songId !in exclude && profile.history(it.songId).plays <= 1 }
        val familiar =
            open
                .filter { it.artistId in outerArtists }
                .map { it.songId to (profile.artist(it) + DIFFERENT_FAMILIAR_GENRE * profile.genre(it)) }
        val unfamiliar =
            open
                .filter { profile.artist(it) == 0f }
                .mapNotNull { track ->
                    val near = similarity[track.songId] ?: 0f
                    val genre = profile.genre(track)
                    (track.songId to (near + genre)).takeIf { near > 0f || genre >= DIFFERENT_GENRE }
                }
        val half = MIX_LIMIT / HALVES
        val a = diverse(profile, MixKind.A_LITTLE_DIFFERENT, familiar, half, DIFFERENT_PER_ARTIST, DIFFERENT_PER_ARTIST)
        val b = diverse(profile, MixKind.A_LITTLE_DIFFERENT, unfamiliar, half, DIFFERENT_PER_ARTIST, DIFFERENT_PER_ARTIST)
        val interleaved = LinkedHashSet<Long>()
        for (i in 0 until maxOf(a.size, b.size)) {
            a.getOrNull(i)?.let(interleaved::add)
            b.getOrNull(i)?.let(interleaved::add)
        }
        // Both halves are needed: all-familiar is Deep Cuts, all-unfamiliar is a guess.
        if (a.isEmpty() || b.isEmpty() || interleaved.size < DIFFERENT_MIN) return null
        return SmartMix(MixKind.A_LITTLE_DIFFERENT, interleaved.toList(), "Lesser-heard songs near your taste")
    }

    // ---- Moods: by genre first; BPM only supports a genre, never decides on its own.

    private const val MOOD_MIN = 8
    private const val MOOD_ARTIST = 0.5f
    private const val MOOD_GENRE = 0.3f
    private const val MOOD_PLAYS = 0.2f
    private const val MOOD_PLAYS_SATURATE = 5f

    internal fun mood(
        profile: ListeningProfile,
        kind: MixKind,
        matches: (TrackFacts) -> Boolean,
    ): SmartMix? {
        val ranked =
            profile.tracks.values.filter(matches).map { track ->
                val plays = (profile.history(track.songId).plays / MOOD_PLAYS_SATURATE).coerceAtMost(1f)
                track.songId to (MOOD_ARTIST * profile.artist(track) + MOOD_GENRE * profile.genre(track) + MOOD_PLAYS * plays)
            }
        val how =
            when (kind) {
                MixKind.HIGH_ENERGY -> "Upbeat songs from your library, by genre and tempo"
                MixKind.CHILL -> "Easy-going songs from your library, by genre"
                else -> "After-dark songs from your library, by genre"
            }
        return finish(profile, kind, ranked, MOOD_MIN, how, perArtist = 3)
    }

    // ---- Fallbacks: honest collections that need no history.

    private const val FALLBACK_MIN = 3
    private const val FRESH_MIN_LIBRARY = 10

    internal fun favourites(profile: ListeningProfile): SmartMix? {
        val ranked =
            profile.tracks.values
                .filter { it.isFavorite }
                .map { it.songId to profile.history(it.songId).plays.toFloat() }
        return finish(profile, MixKind.FAVOURITES, ranked, FALLBACK_MIN, "Songs you marked with a heart", perArtist = Int.MAX_VALUE)
    }

    internal fun fresh(profile: ListeningProfile): SmartMix? {
        if (profile.tracks.size < FRESH_MIN_LIBRARY) return null
        val ranked = profile.tracks.values.map { it.songId to it.addedAt.toFloat() }
        return finish(profile, MixKind.FRESH, ranked, FALLBACK_MIN, "The newest additions to your library", perArtist = Int.MAX_VALUE)
    }

    // ---- Shared ordering and diversity.

    internal fun finish(
        profile: ListeningProfile,
        kind: MixKind,
        ranked: List<Pair<Long, Float>>,
        minimum: Int,
        description: String,
        perArtist: Int = 3,
        perAlbum: Int = Int.MAX_VALUE,
    ): SmartMix? {
        val picked = diverse(profile, kind, ranked, MIX_LIMIT, perArtist, perAlbum)
        return if (picked.size >= minimum) SmartMix(kind, picked, description) else null
    }

    /**
     * Best first, at most [perArtist] songs per artist and [perAlbum] per album, no duplicates.
     * Equal scores fall back to a fixed hash of song and mix, so ties never reshuffle.
     */
    private fun diverse(
        profile: ListeningProfile,
        kind: MixKind,
        ranked: List<Pair<Long, Float>>,
        limit: Int,
        perArtist: Int,
        perAlbum: Int,
    ): List<Long> {
        val order =
            compareByDescending<Pair<Long, Float>> { it.second }
                .thenBy { stableHash(it.first, kind) }
                .thenBy { it.first }
        val artists = HashMap<Long, Int>()
        val albums = HashMap<Long, Int>()
        val picked = LinkedHashSet<Long>()
        for ((id, score) in ranked.sortedWith(order)) {
            if (picked.size >= limit) break
            val track = profile.tracks[id]?.takeIf { score.isFinite() && id !in picked && id !in profile.hidden }
            val fits = track != null && (artists[track.artistId] ?: 0) < perArtist && (albums[track.albumId] ?: 0) < perAlbum
            if (track != null && fits) {
                artists.merge(track.artistId, 1, Int::plus)
                albums.merge(track.albumId, 1, Int::plus)
                picked += id
            }
        }
        return picked.toList()
    }

    /** A fixed scramble of song and mix (SplitMix64 finaliser; String.hashCode is specified, so stable). */
    internal fun stableHash(
        songId: Long,
        kind: MixKind,
    ): Long {
        var h = songId * GOLDEN_GAMMA + kind.key.hashCode()
        h = (h xor (h ushr MIX_SHIFT)) * MIX_MULTIPLIER
        return h xor (h ushr MIX_SHIFT)
    }

    private const val GOLDEN_GAMMA = -0x61c8864680b583ebL
    private const val MIX_MULTIPLIER = -0xae502812aa7333L
    private const val MIX_SHIFT = 33

    /** Scales values to 0..1 by the largest (empty stays empty). */
    private fun <K> normalised(raw: Map<K, Float>): Map<K, Float> {
        val max = raw.values.maxOrNull()?.takeIf { it > 0f } ?: return emptyMap()
        return raw.mapValues { it.value / max }
    }

    /** Cosine scores to 0..1 across the returned neighbours (the closest is 1). */
    private fun rankNormalised(similarity: Map<Long, Float>): Map<Long, Float> {
        val max = similarity.values.maxOrNull() ?: 0f
        val min = similarity.values.minOrNull() ?: 0f
        val span = max - min
        return similarity.mapValues { if (span > 0f) (it.value - min) / span else 1f }
    }
}

/** A track's genres, lower-cased (a tag may hold several: "Hip-Hop; R&B"). */
internal fun genresOf(track: TrackFacts): List<String> =
    track.genre
        ?.split(';', ',', '/', '|')
        ?.map { it.trim().lowercase() }
        ?.filter { it.isNotEmpty() }
        .orEmpty()
