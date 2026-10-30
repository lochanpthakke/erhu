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
import java.io.IOException

/** A track to find lyrics for, by its library id. */
data class LyricsTrack(
    val songId: Long,
    val query: LyricsQuery,
    val sourceUri: String? = null,
)

private fun CachedLyrics.matches(track: LyricsTrack): Boolean =
    songId == track.songId && fingerprint == fingerprint(track.query) && (sourceUri == null || sourceUri == track.sourceUri)

/** The outcome of a lookup. */
sealed interface OnlineLyricsResult {
    data class Found(
        val candidate: LyricsCandidate,
        val fromCache: Boolean,
        val userChosen: Boolean,
        val fetchedAtMs: Long,
        val saved: CachedLyrics? = null,
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
 * Finds lyrics online without saving downloads. Order: existing saved content / session response (a match, an instrumental,
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
    private val inFlight = mutableMapOf<LyricsTrack, Deferred<OnlineLyricsResult>>()
    private val storageLock = Mutex()

    // One most-recent response only, for this process. No transient response is written to disk.
    private var response: Pair<LyricsTrack, CachedLyrics>? = null
    private var preferFile: LyricsTrack? = null

    /** Existing downloads and legacy saved content; metadata mismatches never authorize deletion. */
    suspend fun cached(track: LyricsTrack): CachedLyrics? =
        withContext(io) {
            storageLock.withLock { savedEntry(track)?.takeIf { it.status != CachedStatus.NOT_FOUND } }
        }

    suspend fun prefersEmbedded(track: LyricsTrack): Boolean = lock.withLock { preferFile == track }

    private suspend fun savedEntry(track: LyricsTrack): CachedLyrics? {
        val entry = cache.get(track.songId) ?: return null
        return if (entry.status == CachedStatus.NOT_FOUND &&
            !entry.explicitDownload &&
            nowMs() - entry.fetchedAtMs >= NOT_FOUND_TTL_MS
        ) {
            // Only an accessed, positively identified negative entry is disposable. Never scan saved lyrics.
            if (entry.songId == track.songId && entry.candidate == null) {
                try {
                    cache.remove(track.songId)
                } catch (_: IOException) {
                    // retry on the next access
                }
            }
            null
        } else {
            entry.takeIf { it.matches(track) }
        }
    }

    /** Only explicit user actions write positive results; the old JSON shape remains readable. */
    suspend fun download(
        track: LyricsTrack,
        candidate: LyricsCandidate,
        userChosen: Boolean,
        expected: CachedLyrics?,
    ): CachedLyrics =
        withContext(io) {
            storageLock.withLock {
                require(!candidate.bestText.isNullOrBlank()) { "There are no lyrics to download" }
                val existing =
                    cache.get(track.songId)?.takeUnless {
                        it.status == CachedStatus.NOT_FOUND && it.candidate == null && !it.explicitDownload
                    }
                val sameSelection = existing?.let { it.candidate == candidate && it.userChosen == userChosen } == true
                if (existing?.explicitDownload == true && sameSelection && existing.matches(track)) {
                    return@withLock existing
                }
                if (existing != expected || (existing != null && !existing.matches(track))) {
                    throw IOException("Saved lyrics changed or belong to different song metadata. Reopen lyrics before saving.")
                }
                val entry =
                    CachedLyrics(
                        songId = track.songId,
                        fingerprint = fingerprint(track.query),
                        status = CachedStatus.FOUND,
                        candidate = candidate,
                        fetchedAtMs = nowMs(),
                        userChosen = userChosen,
                        explicitDownload = true,
                        sourceUri = track.sourceUri,
                    )
                cache.put(entry)
                entry
            }
        }

    /** Compare the exact record shown to the user before removing it, including its identity. */
    suspend fun removeDownload(
        track: LyricsTrack,
        expected: CachedLyrics,
    ) = withContext(io) {
        storageLock.withLock {
            val existing = cache.get(track.songId)
            if (existing == null && cache.contains(track.songId)) throw IOException("Saved lyrics could not be read")
            if (existing != null) {
                if (existing != expected || !existing.matches(track)) {
                    throw IOException("Saved lyrics changed. Reopen lyrics before removing them.")
                }
                cache.remove(track.songId)
            }
        }
    }

    /** Shared requests use the full track identity; closing the screen cannot create a download. */
    suspend fun lookup(
        track: LyricsTrack,
        refresh: Boolean = false,
    ): OnlineLyricsResult {
        val job =
            lock.withLock {
                inFlight[track]?.takeUnless { it.isCompleted } ?: scope
                    .async(start = CoroutineStart.LAZY) {
                        resolve(track, refresh)
                    }.also { deferred ->
                        inFlight[track] = deferred
                        deferred.invokeOnCompletion {
                            scope.launch { lock.withLock { if (inFlight[track] === deferred) inFlight.remove(track) } }
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

    /** Selecting a match changes the session preference, not offline storage. */
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
        lock.withLock {
            response = track to entry
            preferFile = null
        }
        return OnlineLyricsResult.Found(candidate, false, true, entry.fetchedAtMs, cached(track))
    }

    /** Prefer embedded text this session without deleting any existing offline lyrics. */
    suspend fun forget(track: LyricsTrack) {
        lock.withLock {
            if (response?.first == track) response = null
            preferFile = track
        }
    }

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
        if (best != null) return remember(track, best)
        val options = LyricsMatcher.plausible(query, found)
        if (options.isNotEmpty()) return OnlineLyricsResult.NeedsChoice(options)
        lock.withLock {
            response = track to
                CachedLyrics(
                    songId = track.songId,
                    fingerprint = fingerprint(query),
                    status = CachedStatus.NOT_FOUND,
                    fetchedAtMs = nowMs(),
                )
        }
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
        val stored = withContext(io) { storageLock.withLock { savedEntry(track) } }
        val saved = stored?.takeIf { it.status != CachedStatus.NOT_FOUND }
        val memory = lock.withLock { response?.takeIf { it.first == track }?.second }
        val entry = memory?.takeIf { it.userChosen } ?: saved ?: memory ?: stored ?: return null
        val candidate = entry.candidate
        return when {
            entry.status == CachedStatus.INSTRUMENTAL -> OnlineLyricsResult.Instrumental
            entry.status == CachedStatus.FOUND && candidate != null ->
                OnlineLyricsResult.Found(candidate, true, entry.userChosen && !prefersEmbedded(track), entry.fetchedAtMs, saved)
            entry.status == CachedStatus.NOT_FOUND && nowMs() - entry.fetchedAtMs < NOT_FOUND_TTL_MS -> OnlineLyricsResult.NotFound
            else -> {
                lock.withLock { if (response?.second === entry) response = null }
                null
            }
        }
    }

    private suspend fun remember(
        track: LyricsTrack,
        best: LyricsCandidate,
    ): OnlineLyricsResult {
        val entry =
            CachedLyrics(
                songId = track.songId,
                fingerprint = fingerprint(track.query),
                status = if (best.instrumental) CachedStatus.INSTRUMENTAL else CachedStatus.FOUND,
                candidate = best,
                fetchedAtMs = nowMs(),
            )
        lock.withLock { response = track to entry }
        return if (best.instrumental) {
            OnlineLyricsResult.Instrumental
        } else {
            OnlineLyricsResult.Found(best, false, false, entry.fetchedAtMs, cached(track))
        }
    }

    companion object {
        /** How long "no lyrics online" is believed before asking again. */
        const val NOT_FOUND_TTL_MS = 7L * 24 * 60 * 60 * 1_000
    }
}
