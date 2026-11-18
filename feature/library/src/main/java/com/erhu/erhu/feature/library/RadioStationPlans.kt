package com.erhu.erhu.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erhu.erhu.core.database.repository.LibraryRepository
import com.erhu.erhu.core.database.repository.TimeSource
import com.erhu.erhu.feature.library.mix.RadioPlan
import com.erhu.erhu.feature.library.mix.RadioRepository
import com.erhu.erhu.feature.library.mix.RadioSeed
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One generated station per seed, shared by its Home tile and its station page, so both show the
 * same artists and the page plays what the tile promised. Regenerated after [TTL_MS].
 */
@Singleton
class RadioStationPlans
    @Inject
    constructor(
        private val radio: RadioRepository,
        private val clock: TimeSource,
    ) {
        private class Entry(
            val plan: RadioPlan?,
            val atMs: Long,
        )

        private val lock = Mutex()
        private val plans = HashMap<RadioSeed, Entry>()

        suspend fun build(seed: RadioSeed): RadioPlan? =
            lock.withLock {
                val now = clock.now()
                plans[seed]?.takeIf { now - it.atMs < TTL_MS }?.let { return@withLock it.plan }
                radio.build(seed).also { plans[seed] = Entry(it, now) }
            }

        private companion object {
            const val TTL_MS = 6L * 60 * 60 * 1000
        }
    }

/** The station portraits for Home's radio tiles: the same three faces the station page shows. */
@HiltViewModel
class RadioCoversViewModel
    @Inject
    constructor(
        private val plans: RadioStationPlans,
        private val library: LibraryRepository,
    ) : ViewModel() {
        private val _portraits = MutableStateFlow<Map<Long, List<RadioPortrait>>>(emptyMap())
        internal val portraits: StateFlow<Map<Long, List<RadioPortrait>>> = _portraits.asStateFlow()
        private val requested = HashSet<Long>()

        fun load(albumId: Long) {
            if (!requested.add(albumId)) return
            viewModelScope.launch {
                val loaded =
                    runCatching {
                        val album = library.getAlbumById(albumId).first() ?: return@runCatching null
                        val plan = plans.build(RadioSeed.Album(albumId)) ?: return@runCatching null
                        val ids = (listOf(plan.startSongId) + plan.songIds).distinct()
                        radioPortraits(
                            RadioStationContent(
                                album = album,
                                plan = plan,
                                songs = library.observeSongsByIds(ids).first(),
                                artists = library.observeArtistsForSongs(ids, plan.startSongId).first(),
                            ),
                        )
                    }.getOrNull()
                if (loaded.isNullOrEmpty()) requested.remove(albumId) else _portraits.update { it + (albumId to loaded) }
            }
        }
    }
