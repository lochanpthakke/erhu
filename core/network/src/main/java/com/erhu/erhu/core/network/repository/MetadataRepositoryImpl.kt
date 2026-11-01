package com.erhu.erhu.core.network.repository

import com.erhu.erhu.core.common.logging.Logger
import com.erhu.erhu.core.common.result.AppResult
import com.erhu.erhu.core.common.result.appResultOf
import com.erhu.erhu.core.common.result.getOrDefault
import com.erhu.erhu.core.model.Genres
import com.erhu.erhu.core.model.LyricsResult
import com.erhu.erhu.core.model.MetadataCandidate
import com.erhu.erhu.core.model.MetadataProvider
import com.erhu.erhu.core.model.ProviderOutcome
import com.erhu.erhu.core.model.ProviderStatus
import com.erhu.erhu.core.network.cleaner.CleanedFilename
import com.erhu.erhu.core.network.cleaner.FilenameCleaner
import com.erhu.erhu.core.network.model.MusicBrainzGenreDto
import com.erhu.erhu.core.network.provider.DeezerProvider
import com.erhu.erhu.core.network.provider.LastFmProvider
import com.erhu.erhu.core.network.provider.LrclibProvider
import com.erhu.erhu.core.network.provider.MusicBrainzProvider
import com.erhu.erhu.core.network.retry.RetryPolicy
import com.erhu.erhu.core.network.retry.classifyProviderFailure
import com.erhu.erhu.core.network.scorer.HIGH_CONFIDENCE_THRESHOLD
import com.erhu.erhu.core.network.scorer.MetadataScorer
import com.erhu.erhu.core.network.tagger.JAudioTaggerWriter
import com.erhu.erhu.core.network.tagger.TagWriteAccessChecker
import com.erhu.erhu.core.network.tagger.TagWriteRequest
import com.erhu.erhu.core.network.tagger.WriteAuthorization
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@Suppress("LongParameterList", "TooGenericExceptionCaught", "LongMethod", "CyclomaticComplexMethod", "NestedBlockDepth")
class MetadataRepositoryImpl
    @Inject
    constructor(
        private val musicBrainzProvider: MusicBrainzProvider,
        private val deezerProvider: DeezerProvider,
        private val lastFmProvider: LastFmProvider,
        private val lrclibProvider: LrclibProvider,
        private val iTunesProvider: com.erhu.erhu.core.network.provider.ITunesProvider,
        private val wikipediaProvider: com.erhu.erhu.core.network.provider.WikipediaProvider,
        private val geniusProvider: com.erhu.erhu.core.network.provider.GeniusProvider,
        private val duckDuckGoImageSearchProvider: com.erhu.erhu.core.network.provider.DuckDuckGoImageSearchProvider,
        private val fanartTvProvider: com.erhu.erhu.core.network.provider.FanartTvProvider,
        private val discogsProvider: com.erhu.erhu.core.network.provider.DiscogsProvider,
        private val filenameCleaner: FilenameCleaner,
        private val scorer: MetadataScorer,
        private val tagWriter: JAudioTaggerWriter,
        private val tagWriteAccessChecker: TagWriteAccessChecker,
        private val okHttpClient: OkHttpClient,
        private val providerHealth: ProviderHealth,
        private val logger: Logger,
    ) : MetadataRepository {
        private val retryPolicy = RetryPolicy()

        private val providers: List<MetadataProvider> by lazy {
            listOf(iTunesProvider, musicBrainzProvider, deezerProvider, lastFmProvider)
        }

        override fun cleanFilename(
            rawFilename: String,
            customBlocklist: List<String>,
        ): CleanedFilename = filenameCleaner.cleanFilename(rawFilename, customBlocklist)

        override suspend fun searchTrack(
            title: String,
            artist: String?,
            album: String?,
            sourceProvider: String,
        ): AppResult<List<MetadataCandidate>> = searchAndScoreTrack(title, artist, album, null, sourceProvider)

        override suspend fun searchAndScoreTrack(
            title: String,
            artist: String?,
            album: String?,
            durationMs: Long?,
            sourceProvider: String,
        ): AppResult<List<MetadataCandidate>> =
            appResultOf {
                searchAndScoreTrackDetailed(title, artist, album, durationMs, sourceProvider)
                    .getOrDefault(
                        DetailedSearchResponse(emptyList(), emptyList()),
                    ).candidates
            }

        override suspend fun searchAndScoreTrackDetailed(
            title: String,
            artist: String?,
            album: String?,
            durationMs: Long?,
            sourceProvider: String,
        ): AppResult<DetailedSearchResponse> =
            appResultOf {
                withContext(Dispatchers.IO) {
                    val results =
                        providers
                            .map { provider ->
                                async { searchOne(provider, sourceProvider, title, artist, album, durationMs) }
                            }.awaitAll()
                    DetailedSearchResponse(
                        candidates = results.flatMap { it.first }.sortedByDescending { it.confidenceScore },
                        providerOutcomes = results.map { it.second },
                    )
                }
            }

        @Suppress("LongParameterList")
        private suspend fun searchOne(
            provider: MetadataProvider,
            selection: String,
            title: String,
            artist: String?,
            album: String?,
            durationMs: Long?,
        ): Pair<List<MetadataCandidate>, ProviderOutcome> =
            searchProviderScored(
                provider = provider,
                selection = selection,
                availability = availabilityOf(provider),
                scorer = scorer,
                title = title,
                artist = artist,
                album = album,
                durationMs = durationMs,
                retry = retryPolicy,
                log = { message -> logger.i(TAG, message) },
                onSessionFailure = { status, reason -> providerHealth.markUnusable(provider.providerName, status, reason) },
            )

        private fun availabilityOf(provider: MetadataProvider): ProviderAvailability =
            when {
                provider === lastFmProvider && !lastFmProvider.isConfigured ->
                    ProviderAvailability.NotConfigured(LastFmProvider.NOT_CONFIGURED_MESSAGE)
                else -> providerHealth.availabilityOf(provider.providerName)
            }

        override suspend fun fetchAlbumArt(
            albumTitle: String,
            artist: String?,
            sourceProvider: String,
        ): AppResult<String?> =
            appResultOf {
                withContext(Dispatchers.IO) {
                    val candidates = fetchAlbumArtCandidates(albumTitle, artist, sourceProvider).getOrDefault(emptyList())
                    candidates.firstOrNull()
                }
            }

        override suspend fun fetchAlbumArtCandidates(
            albumTitle: String,
            artist: String?,
            sourceProvider: String,
        ): AppResult<List<String>> =
            appResultOf {
                withContext(Dispatchers.IO) {
                    val iTunesList =
                        if (MetadataSources.matches(MetadataSources.ITUNES, sourceProvider)) {
                            iTunesProvider.fetchAlbumArtList(albumTitle, artist)
                        } else {
                            emptyList()
                        }

                    val musicBrainzList =
                        if (MetadataSources.matches(MetadataSources.MUSICBRAINZ, sourceProvider)) {
                            musicBrainzProvider.fetchReleaseGroupArtList(albumTitle, artist)
                        } else {
                            emptyList()
                        }

                    val deezerList =
                        if (MetadataSources.matches(MetadataSources.DEEZER, sourceProvider)) {
                            deezerProvider.fetchAlbumArtList(albumTitle, artist)
                        } else {
                            emptyList()
                        }

                    val trackCandidates =
                        searchTrack(title = albumTitle, artist = artist, album = albumTitle, sourceProvider = sourceProvider)
                            .getOrDefault(emptyList())
                    val candidateUrls = trackCandidates.mapNotNull { it.coverArtUrl }.filter { it.isNotBlank() }

                    (iTunesList + musicBrainzList + deezerList + candidateUrls).distinct()
                }
            }

        override suspend fun fetchArtistArtworkCandidates(
            artistName: String,
            sourceProvider: String,
        ): AppResult<List<String>> =
            appResultOf {
                when (val result = fetchArtistArtworkCandidatesDetailed(artistName, sourceProvider)) {
                    is com.erhu.erhu.core.common.result.AppResult.Success -> result.data.candidates
                    is com.erhu.erhu.core.common.result.AppResult.Error -> emptyList()
                }
            }

        override suspend fun fetchArtistArtworkCandidatesDetailed(
            artistName: String,
            sourceProvider: String,
        ): AppResult<ArtistArtworkSearchResponse> =
            appResultOf {
                withContext(Dispatchers.IO) {
                    // 1. Resolve MBID once up front
                    val mbid = musicBrainzProvider.resolveArtistMbid(artistName)

                    // 2. Fan out provider calls in parallel
                    val map = mutableMapOf<String, List<String>>()
                    coroutineScope {
                        val geniusDeferred =
                            if (MetadataSources.matches(MetadataSources.GENIUS, sourceProvider)) {
                                async { geniusProvider.fetchArtistPortraits(artistName).filter { isArtistPhotoUrl(it) } }
                            } else {
                                null
                            }

                        val duckDeferred =
                            if (MetadataSources.matches(MetadataSources.DUCKDUCKGO, sourceProvider)) {
                                async { duckDuckGoImageSearchProvider.fetchArtistPortraits(artistName).filter { isArtistPhotoUrl(it) } }
                            } else {
                                null
                            }

                        val fanartDeferred =
                            if (MetadataSources.matches(MetadataSources.FANART_TV, sourceProvider) && fanartTvProvider.isConfigured) {
                                async {
                                    fanartTvProvider
                                        .fetchArtistPortraits(
                                            artistName,
                                            preResolvedMbid = mbid,
                                        ).filter { isArtistPhotoUrl(it) }
                                }
                            } else {
                                null
                            }

                        val discogsDeferred =
                            if (MetadataSources.matches(MetadataSources.DISCOGS, sourceProvider)) {
                                async {
                                    discogsProvider
                                        .fetchArtistPortraits(
                                            artistName,
                                            preResolvedMbid = mbid,
                                        ).filter { isArtistPhotoUrl(it) }
                                }
                            } else {
                                null
                            }

                        val deezerDeferred =
                            if (MetadataSources.matches(MetadataSources.DEEZER, sourceProvider)) {
                                async { deezerProvider.fetchArtistPortraitList(artistName).filter { isArtistPhotoUrl(it) } }
                            } else {
                                null
                            }

                        val wikiDeferred =
                            if (MetadataSources.matches(MetadataSources.WIKIPEDIA, sourceProvider)) {
                                async {
                                    wikipediaProvider
                                        .fetchArtistPortraits(
                                            artistName,
                                            preResolvedMbid = mbid,
                                        ).filter { isArtistPhotoUrl(it) }
                                }
                            } else {
                                null
                            }

                        val iTunesArtistDeferred =
                            if (MetadataSources.matches(MetadataSources.ITUNES, sourceProvider)) {
                                async { iTunesProvider.fetchArtistArtworkList(artistName).filter { isArtistPhotoUrl(it) } }
                            } else {
                                null
                            }

                        geniusDeferred?.await()?.let { map["Genius"] = it }
                        duckDeferred?.await()?.let { map["DuckDuckGo Web Search"] = it }
                        fanartDeferred?.await()?.let { map["Fanart.tv"] = it }
                        discogsDeferred?.await()?.let { map["Discogs"] = it }
                        deezerDeferred?.await()?.let { map["Deezer"] = it }
                        wikiDeferred?.await()?.let { map["Wikipedia / Wikimedia"] = it }
                        iTunesArtistDeferred?.await()?.let { map["iTunes / Apple Music"] = it }
                    }

                    // 3. Merge layer: Pin top Deezer candidate first, followed by others, deduplicated
                    val deezerTop = map["Deezer"]?.firstOrNull()
                    val allFlattened = map.values.flatten().distinct()
                    val allCandidates =
                        if (deezerTop != null) {
                            listOf(deezerTop) + (allFlattened - deezerTop)
                        } else {
                            allFlattened
                        }

                    ArtistArtworkSearchResponse(candidates = allCandidates, providerCandidates = map, resolvedMbid = mbid)
                }
            }

        override suspend fun lookupGenre(match: MetadataCandidate): GenreLookup =
            withContext(Dispatchers.IO) {
                try {
                    val releaseGroupId = match.releaseGroupId
                    val deezerAlbumId = match.deezerAlbumId
                    when {
                        releaseGroupId != null ->
                            genreFromMusicBrainz(musicBrainzProvider.fetchReleaseGroupGenres(releaseGroupId))
                        deezerAlbumId != null ->
                            Genres
                                .normalize(deezerProvider.fetchAlbumGenres(deezerAlbumId))
                                .takeIf { it.isNotEmpty() }
                                ?.let { GenreLookup.Found(it, "Deezer album") }
                                ?: GenreLookup.NotFound("Deezer lists no genre for this album")
                        else -> GenreLookup.NotFound("${match.sourceName} doesn't provide genres")
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.w(TAG, "Genre lookup failed: ${e.message}", e)
                    GenreLookup.Failed(e.message ?: "Genre lookup failed")
                }
            }

        override suspend fun downloadArtwork(url: String): AppResult<ByteArray> =
            appResultOf {
                withContext(Dispatchers.IO) {
                    val request =
                        Request
                            .Builder()
                            .url(url)
                            .header("User-Agent", USER_AGENT)
                            .build()
                    okHttpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) throw java.io.IOException("Artwork download failed: HTTP ${response.code}")
                        val body = response.body ?: throw java.io.IOException("Artwork download returned no data")
                        val type = body.contentType()
                        if (type != null && type.type != "image") throw java.io.IOException("Artwork URL is not an image ($type)")
                        if (body.contentLength() > MAX_ARTWORK_BYTES) throw java.io.IOException("Artwork is too large")
                        val bytes = body.bytes()
                        if (bytes.isEmpty() || bytes.size > MAX_ARTWORK_BYTES) throw java.io.IOException("Artwork has an invalid size")
                        bytes
                    }
                }
            }

        override suspend fun fetchLyrics(
            title: String,
            artist: String,
            album: String?,
            durationSec: Int?,
        ): AppResult<LyricsResult?> =
            appResultOf {
                lrclibProvider.fetchLyrics(title, artist, album, durationSec)
            }

        override suspend fun writeTagsToFile(request: TagWriteRequest): AppResult<Unit> = tagWriter.writeTags(request)

        override fun checkTagWriteAccess(contentUris: List<String>): WriteAuthorization = tagWriteAccessChecker.check(contentUris)

        companion object {
            private const val TAG = "MetadataRepositoryImpl"
            private const val USER_AGENT = "erhu/1.0.0 (https://github.com/lochanpthakke/empee3)"
            private const val MAX_ARTWORK_BYTES = 10L * 1024 * 1024
        }
    }

private fun isArtistPhotoUrl(url: String): Boolean {
    if (url.isBlank()) return false
    val lower = url.lowercase()
    if (lower.contains("genius.com") || lower.contains("rapgenius") || lower.contains("amazonaws.com/rapgenius")) {
        return true
    }
    if (lower.contains("/cover/") ||
        lower.contains("/album/") ||
        lower.contains("cover_") ||
        lower.contains("coverart") ||
        lower.contains("album_art")
    ) {
        return false
    }
    return true
}

/**
 * The genres to write from MusicBrainz's curated release-group genres: every usable one, most-voted
 * first. Genres with equal votes are all kept (in MusicBrainz's order): a tie used to give no genre
 * at all, which left most albums (one vote per genre is common) without one.
 */
internal fun genreFromMusicBrainz(genres: List<MusicBrainzGenreDto>): GenreLookup {
    // A genre voted down to zero or below is not one MusicBrainz vouches for.
    val ranked = Genres.normalize(genres.filter { it.count > 0 }.sortedByDescending { it.count }.map { it.name })
    return if (ranked.isEmpty()) {
        GenreLookup.NotFound("MusicBrainz has no genre for this album")
    } else {
        GenreLookup.Found(ranked, "MusicBrainz release group")
    }
}

/** Whether a provider may be asked at all, and if not, the reason shown instead of results. */
sealed interface ProviderAvailability {
    data object Available : ProviderAvailability

    /** No credentials: never called. */
    data class NotConfigured(
        val reason: String,
    ) : ProviderAvailability

    /** Its credentials were rejected earlier this session: not called again with the same key. */
    data class Rejected(
        val reason: String,
    ) : ProviderAvailability

    /** It said earlier this session that it doesn't serve this country: not called again. */
    data class Unavailable(
        val reason: String,
    ) : ProviderAvailability
}

/**
 * Providers that can't help for the rest of this session: credentials rejected, or the service
 * withheld in this country. One request finds out; later tracks get the same truthful status
 * without asking again. Cleared by restarting the app.
 */
@Singleton
class ProviderHealth
    @Inject
    constructor() {
        private val unusable = java.util.concurrent.ConcurrentHashMap<String, ProviderAvailability>()

        fun markUnusable(
            providerName: String,
            status: ProviderStatus,
            reason: String,
        ) {
            when (status) {
                ProviderStatus.AUTH_FAILED -> unusable[providerName] = ProviderAvailability.Rejected(reason)
                ProviderStatus.UNAVAILABLE -> unusable[providerName] = ProviderAvailability.Unavailable(reason)
                else -> Unit
            }
        }

        fun availabilityOf(providerName: String): ProviderAvailability = unusable[providerName] ?: ProviderAvailability.Available
    }

/**
 * One provider's part of a search. Not selected: [ProviderStatus.SKIPPED]; not configured or
 * rejected earlier: [ProviderStatus.NOT_CONFIGURED] / [ProviderStatus.AUTH_FAILED], all without a
 * request. Otherwise the request runs under [retry] (transient failures only, bounded backoff,
 * Retry-After respected) and a failure keeps its real category: network, timeout, rate limit,
 * credentials or other, never "no results". The status reflects the best score, not the first.
 */
@Suppress("LongParameterList", "TooGenericExceptionCaught", "ReturnCount", "LongMethod")
internal suspend fun searchProviderScored(
    provider: MetadataProvider,
    selection: String,
    availability: ProviderAvailability,
    scorer: MetadataScorer,
    title: String,
    artist: String?,
    album: String?,
    durationMs: Long?,
    retry: RetryPolicy = RetryPolicy(),
    log: (String) -> Unit = {},
    onSessionFailure: (ProviderStatus, String) -> Unit = { _, _ -> },
): Pair<List<MetadataCandidate>, ProviderOutcome> {
    val name = provider.providerName
    if (!MetadataSources.matches(name, selection)) {
        return emptyList<MetadataCandidate>() to
            ProviderOutcome(name, status = ProviderStatus.SKIPPED, errorMessage = "Not selected (source: ${sourceLabel(selection)})")
    }
    when (availability) {
        is ProviderAvailability.NotConfigured -> {
            log("provider=$name decision=not-queried reason=not-configured")
            return emptyList<MetadataCandidate>() to
                ProviderOutcome(name, status = ProviderStatus.NOT_CONFIGURED, errorMessage = availability.reason)
        }
        is ProviderAvailability.Rejected -> {
            log("provider=$name decision=not-queried reason=credentials-rejected-earlier")
            return emptyList<MetadataCandidate>() to
                ProviderOutcome(name, status = ProviderStatus.AUTH_FAILED, errorMessage = "${availability.reason} (not retried)")
        }
        is ProviderAvailability.Unavailable -> {
            log("provider=$name decision=not-queried reason=unavailable-here")
            return emptyList<MetadataCandidate>() to
                ProviderOutcome(name, status = ProviderStatus.UNAVAILABLE, errorMessage = "${availability.reason} (not asked again)")
        }
        ProviderAvailability.Available -> Unit
    }
    return try {
        val found =
            retry.run(
                onRetry = { attempt, failure, waitMs ->
                    log("provider=$name retry=$attempt cause=${failure.status} waitMs=$waitMs")
                },
            ) {
                provider.searchTrack(title, artist, album)
            }
        val scored =
            found.map { candidate ->
                scorer.scoreCandidate(
                    candidate = candidate,
                    targetTitle = title,
                    targetArtist = artist,
                    targetAlbum = album,
                    targetDurationMs = durationMs,
                )
            }
        val topScore = scored.maxOfOrNull { it.confidenceScore } ?: 0f
        val status =
            when {
                scored.isEmpty() -> ProviderStatus.NO_RESULTS
                topScore >= HIGH_CONFIDENCE_THRESHOLD -> ProviderStatus.SUCCESS
                else -> ProviderStatus.LOW_CONFIDENCE
            }
        log("provider=$name decision=queried outcome=$status candidates=${scored.size}")
        scored to ProviderOutcome(name, candidateCount = scored.size, status = status)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        val failure = classifyProviderFailure(e)
        log("provider=$name decision=queried outcome=${failure.status} error=${e.javaClass.simpleName}")
        onSessionFailure(failure.status, failure.message)
        emptyList<MetadataCandidate>() to ProviderOutcome(name, status = failure.status, errorMessage = failure.message)
    }
}

private fun sourceLabel(selection: String): String = if (selection == MetadataSources.ALL) "all providers" else selection
