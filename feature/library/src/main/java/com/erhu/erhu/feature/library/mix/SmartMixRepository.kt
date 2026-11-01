package com.erhu.erhu.feature.library.mix

import com.erhu.erhu.core.database.repository.ListeningHistoryRepository
import com.erhu.erhu.core.database.repository.TimeSource
import com.erhu.erhu.core.datastore.HiddenTracksPersistence
import com.erhu.erhu.core.model.MonthlyPlays
import com.erhu.erhu.core.model.TrackFacts
import com.erhu.erhu.feature.library.vybe.VybeEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** VYBE's view of the listener's taste: indexed songs near the [seeds] (id -> weight), or null without an index. */
fun interface TasteNeighbours {
    suspend fun near(
        seeds: Map<Long, Float>,
        limit: Int,
    ): Map<Long, Float>?
}

/**
 * The smart mixes, kept up to date: rebuilt off the main thread when the library or a counted
 * play changes (debounced), never on recomposition or screen entry. One instance for the app,
 * so returning to the page shows the cached mixes at once; it stops listening a minute after
 * nothing shows them and catches up when they are shown again.
 *
 * Everything is local: Room data, the clock, and VYBE's stored vectors (never its model).
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class SmartMixSource(
    tracks: Flow<List<TrackFacts>>,
    plays: Flow<List<MonthlyPlays>>,
    hidden: Flow<Set<Long>>,
    private val clock: TimeSource,
    private val taste: TasteNeighbours,
    scope: CoroutineScope,
    private val compute: CoroutineDispatcher = Dispatchers.Default,
    debounceMs: Long = CHANGE_DEBOUNCE_MS,
) {
    /** Null until the first build. */
    val mixes: StateFlow<SmartMixes?> =
        combine(tracks, plays, hidden) { t, p, h -> Triple(t, p, h) }
            // The first emission goes through at once; bursts of changes (a rescan) settle first.
            .debounceAfterFirst(debounceMs)
            .mapLatest { (t, p, h) -> build(t, p, h) }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.WhileSubscribed(KEEP_ALIVE_MS), null)

    /** The mix with [key], live; null when it isn't offered (yet). */
    fun mix(key: String): Flow<SmartMix?> = mixes.map { all -> all?.mixes?.firstOrNull { it.kind.key == key } }.distinctUntilChanged()

    internal suspend fun build(
        tracks: List<TrackFacts>,
        plays: List<MonthlyPlays>,
        hidden: Set<Long> = emptySet(),
    ): SmartMixes {
        val profile = withContext(compute) { SmartMixEngine.profile(tracks, plays, clock.now(), hidden) }
        val neighbours =
            if (profile.isPersonal) {
                try {
                    taste.near(SmartMixEngine.tasteSeeds(profile), TASTE_LIMIT)
                } catch (e: CancellationException) {
                    throw e
                } catch (
                    @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception,
                ) {
                    // VYBE is an enhancement: the mixes stand on their own without it (an
                    // unreadable index just means no taste neighbours this time).
                    null
                }
            } else {
                null
            }
        return withContext(compute) { SmartMixEngine.build(profile, neighbours) }
    }

    companion object {
        const val CHANGE_DEBOUNCE_MS = 1_500L
        private const val KEEP_ALIVE_MS = 60_000L
        private const val TASTE_LIMIT = 300
    }
}

/** Debounces every value but the first of each collection (which shows at once). */
@OptIn(FlowPreview::class)
private fun <T> Flow<T>.debounceAfterFirst(ms: Long): Flow<T> =
    flow {
        var first = true
        emitAll(
            debounce {
                if (first) {
                    first = false
                    0L
                } else {
                    ms
                }
            },
        )
    }

/** The app's [SmartMixSource], over Room and VYBE. */
@Singleton
class SmartMixRepository
    @Inject
    constructor(
        history: ListeningHistoryRepository,
        vybe: VybeEngine,
        clock: TimeSource,
        private val hiddenTracks: HiddenTracksPersistence,
    ) {
        private val source =
            SmartMixSource(
                tracks = history.observeTracks(),
                plays = history.observePlays(),
                hidden = hiddenTracks.observe(),
                clock = clock,
                taste = TasteNeighbours { seeds, limit -> vybe.tasteNeighbours(seeds, limit) },
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            )

        val mixes: StateFlow<SmartMixes?> get() = source.mixes

        fun mix(key: String): Flow<SmartMix?> = source.mix(key)

        /** Songs hidden from Smart Playlists. */
        val hidden: Flow<Set<Long>> get() = hiddenTracks.observe()

        /** Hides [songId] from (or returns it to) Smart Playlists; nothing else about the song changes. */
        suspend fun setHidden(
            songId: Long,
            hidden: Boolean,
        ) {
            hiddenTracks.setHidden(songId, hidden)
        }
    }
