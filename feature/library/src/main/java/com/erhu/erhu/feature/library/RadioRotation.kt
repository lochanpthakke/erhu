package com.erhu.erhu.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import com.erhu.erhu.core.model.Album
import com.erhu.erhu.core.model.MonthlyPlays
import com.erhu.erhu.core.model.TrackFacts
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * When Home's radio stations and banners change. Each stays for a fixed window of the clock (the
 * same on every visit within it), then the next window brings a different set, so Home does not
 * show the same stations for days.
 */
internal object RadioRotation {
    const val RADIO_HOURS = 5
    const val BANNER_HOURS = 8

    /** Home shows this many stations at a time; a station page recommends [RECOMMENDED] more. */
    const val HOME_RADIOS = 3
    const val RECOMMENDED = 5

    /** Stations rotate through the listener's top artists (by plays), then the rest of the library. */
    private const val POOL = 15

    private const val HOUR_MS = 60L * 60 * 1000

    fun slot(
        nowMs: Long,
        hours: Int,
    ): Long = nowMs / (hours * HOUR_MS)

    /** The current window index of [hours], emitted again at each window boundary. */
    fun slots(hours: Int): Flow<Long> =
        flow {
            while (true) {
                val now = System.currentTimeMillis()
                val window = hours * HOUR_MS
                emit(now / window)
                delay(window - now % window + 1)
            }
        }

    /**
     * One album per artist that can lead a radio: the artists played most first (each by its most
     * played album), then everyone else in the library by album size. Compilations and unknown
     * artists cannot represent a station.
     */
    fun pool(
        albums: List<Album>,
        tracks: List<TrackFacts>,
        plays: List<MonthlyPlays>,
    ): List<Album> {
        val usable = albums.filter { it.songCount > 0 && !it.isCompilation && it.artistId > 0 && hasArtist(it) }
        val albumOfSong = tracks.associate { it.songId to it.albumId }
        val playsByAlbum = HashMap<Long, Int>()
        plays.forEach { p -> albumOfSong[p.songId]?.let { playsByAlbum.merge(it, p.plays, Int::plus) } }
        return usable
            .groupBy { it.artistId }
            .map { (_, own) ->
                val best = own.maxWith(compareBy<Album> { playsByAlbum[it.id] ?: 0 }.thenBy { it.songCount }.thenBy { -it.id })
                best to own.sumOf { playsByAlbum[it.id] ?: 0 }
            }.sortedWith(compareByDescending<Pair<Album, Int>> { it.second }.thenByDescending { it.first.songCount }.thenBy { it.first.id })
            .map { it.first }
    }

    /** The stations of window [slot]: a different draw from the top of the pool each window. */
    fun stations(
        pool: List<Album>,
        slot: Long,
        count: Int = HOME_RADIOS,
    ): List<Album> = pool.take(POOL).sortedBy { FeaturedAlbums.dailyRank(it.artistId, slot) }.take(count)

    /**
     * Stations to try next from the one on screen: first the other artists playing on it (in the
     * order they appear), then the current window's rotation; never the station's own artist.
     */
    fun recommendations(
        pool: List<Album>,
        current: Album,
        stationArtistIds: List<Long>,
        slot: Long,
    ): List<Album> {
        val byArtist = pool.associateBy { it.artistId }
        val related = stationArtistIds.mapNotNull { byArtist[it] }
        val rotation = stations(pool, slot, pool.size)
        return (related + rotation)
            .filter { it.artistId != current.artistId && it.id != current.id }
            .distinctBy { it.artistId }
            .take(RECOMMENDED)
    }

    private fun hasArtist(album: Album): Boolean =
        album.artist.isNotBlank() && !album.artist.equals("Unknown Artist", ignoreCase = true) && album.artist != "<unknown>"
}

/** The current window of [hours] as Compose state, updated when the window turns over. */
@Composable
internal fun rememberRotationSlot(hours: Int): State<Long> =
    produceState(RadioRotation.slot(System.currentTimeMillis(), hours), hours) {
        RadioRotation.slots(hours).collect { value = it }
    }
