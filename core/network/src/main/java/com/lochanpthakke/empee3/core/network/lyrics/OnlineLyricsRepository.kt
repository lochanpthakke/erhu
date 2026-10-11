package com.lochanpthakke.empee3.core.network.lyrics

import com.lochanpthakke.empee3.core.common.dispatcher.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** A track to find lyrics for, by its library id. */
data class LyricsTrack(
    val songId: Long,
    val query: LyricsQuery,
)

/** The outcome of a lookup. */
sealed interface OnlineLyricsResult {
    data class Found(
        val candidate: LyricsCandidate,
        val fromCache: Boolean,
        val userChosen: Boolean,
        val fetchedAtMs: Long,
    ) : OnlineLyricsResult

    data object NotFound : OnlineLyricsResult

    data object Instrumental : OnlineLyricsResult

    /** Results that might match but none confidently: the user chooses (nothing is shown silently). */
    data class NeedsChoice(
        val candidates: List<LyricsCandidate>,
    ) : OnlineLyricsResult

    data object Offline : OnlineLyricsResult

    data class RateLimited(
        val retryAtMs: Long,
    ) : OnlineLyricsResult

    data class Failed(
        val reason: String,
    ) : OnlineLyricsResult
}

/**
 * Finds lyrics online for a track, with a local cache. Order: the cache (a match, an instrumental,
 * or a recent "not found"), then each provider: its exact-match lookup, and a search when that is
 * not already a confident word-timed match. Only a confident match is used automatically, word
 * timing first; otherwise plausible results are offered to choose from. Concurrent lookups of the
 * same track share one request; failures are not cached, so a retry can succeed.
 */
class OnlineLyricsRepository(
    private val providers: List<OnlineLyricsProvider>,
    private val cache: OnlineLyricsCache,
    dispatchers: DispatcherProvider,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    private val io = dispatchers.io
    private val scope = CoroutineScope(SupervisorJob() + io)
    private val lock = Mutex()
    private val inFlight = mutableMapOf<Long, Deferred<OnlineLyricsResult>>()

    /** The cached result for [track] if it still fits its metadata; never touches the network. */
    suspend fun cached(track: LyricsTrack): CachedLyrics? =
        withContext(io) { cache.get(track.songId)?.takeIf { it.fingerprint == fingerprint(track.query) } }

    /**
     * Lyrics for [track]: from the cache, or looked up online. [refresh] ignores the cache. Callers
     * that stop waiting (the track changed) do not cancel the shared lookup; its result is cached.
     */
    suspend fun lookup(
        track: LyricsTrack,
        refresh: Boolean = false,
    ): OnlineLyricsResult {
        val job =
            lock.withLock {
                inFlight[track.songId] ?: scope.async(start = CoroutineStart.LAZY) { resolve(track, refresh) }.also { deferred ->
                    inFlight[track.songId] = deferred
                    deferred.invokeOnCompletion {
                        scope.launch {
                            lock.withLock {
                                if (inFlight[track.songId] ===
                                    deferred
                                ) {
                                    inFlight.remove(track.songId)
                                }
                            }
                        }
                    }
                    deferred.start()
                }
            }
        return job.await()
    }

    /** Manual search: everything a provider returns for [freeText] (or the track's own fields), not filtered. */
    suspend fun search(
        track: LyricsTrack,
        freeText: String?,
    ): ProviderResponse<List<LyricsCandidate>> =
        providers.firstOrNull()?.search(track.query, freeText?.takeIf { it.isNotBlank() }) ?: ProviderResponse.NotFound

    /** The user picked [candidate] for [track]: it is kept and preferred (also over the file's own lyrics). */
    suspend fun choose(
        track: LyricsTrack,
        candidate: LyricsCandidate,
    ): OnlineLyricsResult.Found {
        val entry =
            CachedLyrics(
                songId = track.songId,
                fingerprint = fingerprint(track.query),
                status = if (candidate.instrumental) CachedStatus.INSTRUMENTAL else CachedStatus.FOUND,
                candidate = candidate,
                fetchedAtMs = nowMs(),
                userChosen = true,
            )
        withContext(io) { cache.put(entry) }
        return OnlineLyricsResult.Found(candidate, fromCache = false, userChosen = true, fetchedAtMs = entry.fetchedAtMs)
    }

    /** Forgets what was fetched for [track] (the file's own lyrics, or a new lookup, take over). */
    suspend fun forget(track: LyricsTrack) = withContext(io) { cache.remove(track.songId) }

    @Suppress("ReturnCount")
    private suspend fun resolve(
        track: LyricsTrack,
        refresh: Boolean,
    ): OnlineLyricsResult {
        if (!refresh) fromCache(track)?.let { return it }
        val query = track.query
        if (query.title.isBlank()) return OnlineLyricsResult.NotFound
        val found = mutableListOf<LyricsCandidate>()
        for (provider in providers) {
            val outcome = ask(provider, query, found)
            if (outcome != null) return outcome
        }
        val best = LyricsMatcher.best(query, found)
        if (best != null) return store(track, best)
        val options = LyricsMatcher.plausible(query, found)
        if (options.isNotEmpty()) return OnlineLyricsResult.NeedsChoice(options)
        cache.put(
            CachedLyrics(songId = track.songId, fingerprint = fingerprint(query), status = CachedStatus.NOT_FOUND, fetchedAtMs = nowMs()),
        )
        return OnlineLyricsResult.NotFound
    }

    /**
     * Collects [provider]'s results into [found]; returns an outcome only when the lookup must stop
     * (offline, rate limited, or failed with nothing usable). A confident word-timed exact match
     * needs no search.
     */
    @Suppress("ReturnCount")
    private suspend fun ask(
        provider: OnlineLyricsProvider,
        query: LyricsQuery,
        found: MutableList<LyricsCandidate>,
    ): OnlineLyricsResult? {
        if (query.durationMs > 0 && query.artist.isNotBlank()) {
            when (val exact = provider.exact(query)) {
                is ProviderResponse.Success -> found += exact.value
                ProviderResponse.NotFound, is ProviderResponse.Failed -> Unit
                else -> return stop(exact)
            }
            val exactMatch = found.lastOrNull()?.takeIf { LyricsMatcher.score(query, it).confident }
            if (exactMatch?.timing == LyricsTiming.WORD) return null
        }
        when (val search = provider.search(query)) {
            is ProviderResponse.Success ->
                found +=
                    search.value.filter { candidate -> found.none { it.providerId == candidate.providerId } }
            ProviderResponse.NotFound -> Unit
            else -> if (LyricsMatcher.best(query, found) == null) return stop(search)
        }
        return null
    }

    private fun stop(response: ProviderResponse<*>): OnlineLyricsResult =
        when (response) {
            is ProviderResponse.RateLimited -> OnlineLyricsResult.RateLimited(response.retryAtMs)
            ProviderResponse.Offline -> OnlineLyricsResult.Offline
            is ProviderResponse.Failed -> OnlineLyricsResult.Failed(response.reason)
            else -> OnlineLyricsResult.NotFound
        }

    private suspend fun fromCache(track: LyricsTrack): OnlineLyricsResult? {
        val entry = cache.get(track.songId)?.takeIf { it.fingerprint == fingerprint(track.query) } ?: return null
        val candidate = entry.candidate
        return when {
            entry.status == CachedStatus.INSTRUMENTAL -> OnlineLyricsResult.Instrumental
            entry.status == CachedStatus.FOUND && candidate != null ->
                OnlineLyricsResult.Found(candidate, fromCache = true, userChosen = entry.userChosen, fetchedAtMs = entry.fetchedAtMs)
            // "Not found" is remembered for a while, then asked again (lyrics get added over time).
            entry.status == CachedStatus.NOT_FOUND && nowMs() - entry.fetchedAtMs < NOT_FOUND_TTL_MS -> OnlineLyricsResult.NotFound
            else -> null
        }
    }

    private suspend fun store(
        track: LyricsTrack,
        best: LyricsCandidate,
    ): OnlineLyricsResult {
        val status = if (best.instrumental) CachedStatus.INSTRUMENTAL else CachedStatus.FOUND
        val entry =
            CachedLyrics(
                songId = track.songId,
                fingerprint = fingerprint(track.query),
                status = status,
                candidate = best,
                fetchedAtMs = nowMs(),
            )
        cache.put(entry)
        return if (best.instrumental) {
            OnlineLyricsResult.Instrumental
        } else {
            OnlineLyricsResult.Found(best, fromCache = false, userChosen = false, fetchedAtMs = entry.fetchedAtMs)
        }
    }

    companion object {
        /** How long "no lyrics online" is believed before asking again. */
        const val NOT_FOUND_TTL_MS = 7L * 24 * 60 * 60 * 1_000
    }
}
