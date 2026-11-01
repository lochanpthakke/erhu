package com.erhu.erhu.feature.library.mix

import com.erhu.erhu.core.database.repository.ListeningHistoryRepository
import com.erhu.erhu.core.database.repository.TimeSource
import com.erhu.erhu.core.datastore.HiddenTracksPersistence
import com.erhu.erhu.core.model.MonthlyPlays
import com.erhu.erhu.core.model.TrackFacts
import com.erhu.erhu.feature.library.vybe.VybeEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds a radio when asked, never in the background: one read of the library and play history,
 * VYBE's similarity to the seed's songs from stored vectors (never its model; skipped without an
 * index or when it fails), then [RadioEngine] off the main thread.
 */
class RadioSource(
    private val tracks: suspend () -> List<TrackFacts>,
    private val plays: suspend () -> List<MonthlyPlays>,
    private val hidden: suspend () -> Set<Long>,
    private val clock: TimeSource,
    private val taste: TasteNeighbours,
    private val compute: CoroutineDispatcher = Dispatchers.Default,
) {
    suspend fun build(
        seed: RadioSeed,
        exclude: Set<Long> = emptySet(),
        startSongId: Long? = null,
    ): RadioPlan? {
        val library = tracks()
        val history = plays()
        val hiddenSongs = hidden()
        val profile = withContext(compute) { SmartMixEngine.profile(library, history, clock.now(), hiddenSongs) }
        val anchors = RadioEngine.anchors(profile, seed)
        if (anchors.isEmpty()) return null
        val similarity =
            try {
                taste.near(anchors.associate { it.songId to 1f }, SIMILAR_LIMIT)
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception,
            ) {
                // VYBE only refines the radio: an unreadable index means metadata and history alone.
                null
            }
        return withContext(compute) { RadioEngine.build(profile, seed, similarity, exclude, startSongId) }
    }

    private companion object {
        const val SIMILAR_LIMIT = 500
    }
}

/** The app's [RadioSource], over Room, the hidden-songs setting and VYBE. */
@Singleton
class RadioRepository
    @Inject
    constructor(
        history: ListeningHistoryRepository,
        vybe: VybeEngine,
        clock: TimeSource,
        hiddenTracks: HiddenTracksPersistence,
    ) {
        private val source =
            RadioSource(
                tracks = { history.observeTracks().first() },
                plays = { history.observePlays().first() },
                hidden = { hiddenTracks.observe().first() },
                clock = clock,
                taste = TasteNeighbours { seeds, limit -> vybe.tasteNeighbours(seeds, limit) },
            )

        suspend fun build(
            seed: RadioSeed,
            exclude: Set<Long> = emptySet(),
            startSongId: Long? = null,
        ): RadioPlan? = source.build(seed, exclude, startSongId)
    }
