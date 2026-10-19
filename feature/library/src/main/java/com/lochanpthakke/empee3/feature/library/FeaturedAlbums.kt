package com.lochanpthakke.empee3.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lochanpthakke.empee3.core.database.repository.LibraryRepository
import com.lochanpthakke.empee3.core.database.repository.ListeningHistoryRepository
import com.lochanpthakke.empee3.core.database.repository.RecentlyPlayedRecorder
import com.lochanpthakke.empee3.core.model.Album
import com.lochanpthakke.empee3.core.model.MonthlyPlays
import com.lochanpthakke.empee3.core.model.TrackFacts
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

/**
 * The home screen's Featured Albums: albums already in the library, chosen locally and
 * deterministically from what the app actually knows, never from a server:
 *
 * 1. albums the listener has played the most (counted plays, all months), then
 * 2. albums holding their favourite songs,
 * 3. then, to fill the shelf, a rotation of the library's other albums that changes once a day
 *    (seeded by the date, so the shelf is stable through the day, not reshuffled on every visit):
 *    fuller albums first, then singles and EPs.
 *
 * Albums already on the Recently Played shelf are left out, so the two shelves don't repeat each
 * other, unless the library has too few other albums to fill the shelf: then those come last.
 * Every album appears once. With no history and no favourites the shelf is the daily rotation
 * only: nothing claims to be personal when there is nothing personal behind it.
 */
internal object FeaturedAlbums {
    const val LIMIT = 5

    /** Below this many songs an album is likely a single; rotation prefers fuller albums. */
    private const val MIN_ROTATION_SONGS = 3

    fun select(
        albums: List<Album>,
        tracks: List<TrackFacts>,
        plays: List<MonthlyPlays>,
        exclude: Set<Long>,
        epochDay: Long,
        limit: Int = LIMIT,
    ): List<Album> {
        val inLibrary = albums.filter { it.songCount > 0 }.distinctBy { it.id }
        val candidates = inLibrary.filter { it.id !in exclude }
        val albumOfSong = tracks.associate { it.songId to it.albumId }
        val playsByAlbum = HashMap<Long, Int>()
        plays.forEach { p -> albumOfSong[p.songId]?.let { playsByAlbum.merge(it, p.plays, Int::plus) } }
        val favouritesByAlbum = tracks.filter { it.isFavorite }.groupingBy { it.albumId }.eachCount()

        val played =
            candidates
                .filter { (playsByAlbum[it.id] ?: 0) > 0 }
                .sortedWith(
                    compareByDescending<Album> { playsByAlbum[it.id] ?: 0 }
                        .thenByDescending { favouritesByAlbum[it.id] ?: 0 }
                        .thenBy { it.id },
                )
        val loved =
            candidates
                .filter { (playsByAlbum[it.id] ?: 0) == 0 && (favouritesByAlbum[it.id] ?: 0) > 0 }
                .sortedWith(compareByDescending<Album> { favouritesByAlbum[it.id] ?: 0 }.thenBy { it.id })
        val chosen = (played + loved).take(limit)
        val taken = chosen.mapTo(HashSet()) { it.id }
        val daily = compareBy<Album> { dailyRank(it.id, epochDay) }.thenBy { it.id }
        val (fuller, smaller) = candidates.filter { it.id !in taken }.partition { it.songCount >= MIN_ROTATION_SONGS }
        // Only to fill a shelf the rest of the library cannot: albums from the Recently Played shelf.
        val recent = inLibrary.filter { it.id in exclude && it.id !in taken }
        val rotation = fuller.sortedWith(daily) + smaller.sortedWith(daily) + recent.sortedWith(daily)
        return chosen + rotation.take(limit - chosen.size)
    }

    /** A stable pseudo-random rank of [albumId] for [epochDay] (SplitMix64 mixing). */
    internal fun dailyRank(
        albumId: Long,
        epochDay: Long,
    ): Long {
        var z = albumId * GOLDEN + epochDay
        z = (z xor (z ushr SHIFT_A)) * MIX_A
        z = (z xor (z ushr SHIFT_B)) * MIX_B
        return z xor (z ushr SHIFT_C)
    }

    private const val GOLDEN = -0x61c8864680b583ebL
    private const val MIX_A = -0x40a7b892e31b1a47L
    private const val MIX_B = -0x6b2fb644ecceee15L
    private const val SHIFT_A = 30
    private const val SHIFT_B = 27
    private const val SHIFT_C = 31
}

/**
 * Supplies [FeaturedAlbums] to the home screen from the library, the local listening history and
 * the Recently Played shelf (all Room flows), rebuilt off the main thread when any of them change.
 */
@HiltViewModel
@OptIn(FlowPreview::class)
class FeaturedAlbumsViewModel
    @Inject
    constructor(
        library: LibraryRepository,
        history: ListeningHistoryRepository,
        recentlyPlayed: RecentlyPlayedRecorder,
    ) : ViewModel() {
        val albums: StateFlow<List<Album>> =
            combine(
                library.getAllAlbums(),
                history.observeTracks(),
                history.observePlays(),
                recentlyPlayed.observeRecentAlbums(RECENT_SHELF_LIMIT),
            ) { albums, tracks, plays, recent ->
                FeaturedAlbums.select(albums, tracks, plays, recent.mapTo(HashSet()) { it.id }, LocalDate.now().toEpochDay())
            }.debounce(REBUILD_DEBOUNCE_MS)
                .distinctUntilChanged()
                .flowOn(Dispatchers.Default)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

        private companion object {
            /** The Recently Played shelf's length (LibraryViewModel's limit). */
            const val RECENT_SHELF_LIMIT = 12
            const val REBUILD_DEBOUNCE_MS = 300L
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
