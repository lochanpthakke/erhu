package com.erhu.erhu.feature.library

import com.erhu.erhu.core.common.result.getOrNull
import com.erhu.erhu.core.database.repository.ArtistArtworkRepository
import com.erhu.erhu.core.database.repository.AutomaticArtwork
import com.erhu.erhu.core.model.Artist
import com.erhu.erhu.core.network.provider.ArtistArtworkLookup
import com.erhu.erhu.core.network.provider.DeezerProvider
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Automatic artist photos from Deezer. Looks up the artists that need a decision (never looked up,
 * decided by an older matcher, or not found 30+ days ago) and records Found / NotFound. A failed
 * lookup records nothing, so existing artwork stays and a later launch retries. Custom images are
 * never selected and never overwritten (enforced by [ArtistArtworkRepository]).
 */
@Singleton
class ArtistArtworkUpdater
    @Inject
    constructor(
        private val deezerProvider: DeezerProvider,
        private val repository: ArtistArtworkRepository,
    ) {
        /** Each artist is looked up at most once per process, so a failure is not retried in a loop. */
        private val attempted = mutableSetOf<Long>()

        suspend fun updateDueArtists(now: Long) {
            val due = repository.artistsNeedingAutomaticLookup(now).getOrNull().orEmpty()
            for (artist in due) {
                if (!synchronized(attempted) { attempted.add(artist.id) }) continue
                lookUp(artist)
                delay(LOOKUP_SPACING_MS)
            }
        }

        suspend fun lookUp(artist: Artist) {
            val decision =
                when (val result = deezerProvider.lookupArtistArtwork(artist.name)) {
                    is ArtistArtworkLookup.Found -> AutomaticArtwork.Found(result.url, result.deezerArtistId)
                    ArtistArtworkLookup.NotFound -> AutomaticArtwork.NotFound
                    // Offline, rate-limited or unreadable: nothing is stored (existing artwork stays).
                    ArtistArtworkLookup.Failed -> return
                }
            repository.recordAutomaticArtwork(artist.artistKey, decision)
        }

        private companion object {
            /** Deezer allows 50 requests per 5 s; keep the artwork lookups well under that. */
            const val LOOKUP_SPACING_MS = 120L
        }
    }
