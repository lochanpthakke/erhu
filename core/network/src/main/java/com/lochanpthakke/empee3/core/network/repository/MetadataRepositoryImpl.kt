package com.lochanpthakke.empee3.core.network.repository

import com.lochanpthakke.empee3.core.common.logging.Logger
import com.lochanpthakke.empee3.core.common.result.AppResult
import com.lochanpthakke.empee3.core.common.result.appResultOf
import com.lochanpthakke.empee3.core.common.result.getOrDefault
import com.lochanpthakke.empee3.core.model.LyricsResult
import com.lochanpthakke.empee3.core.model.MetadataCandidate
import com.lochanpthakke.empee3.core.model.MetadataProvider
import com.lochanpthakke.empee3.core.model.ProviderOutcome
import com.lochanpthakke.empee3.core.model.ProviderStatus
import com.lochanpthakke.empee3.core.network.cleaner.CleanedFilename
import com.lochanpthakke.empee3.core.network.cleaner.FilenameCleaner
import com.lochanpthakke.empee3.core.network.model.MusicBrainzGenreDto
import com.lochanpthakke.empee3.core.network.provider.DeezerProvider
import com.lochanpthakke.empee3.core.network.provider.LastFmProvider
import com.lochanpthakke.empee3.core.network.provider.LrclibProvider
import com.lochanpthakke.empee3.core.network.provider.MusicBrainzProvider
import com.lochanpthakke.empee3.core.network.scorer.HIGH_CONFIDENCE_THRESHOLD
import com.lochanpthakke.empee3.core.network.scorer.MetadataScorer
import com.lochanpthakke.empee3.core.network.tagger.JAudioTaggerWriter
import com.lochanpthakke.empee3.core.network.tagger.TagWriteAccessChecker
import com.lochanpthakke.empee3.core.network.tagger.TagWriteRequest
import com.lochanpthakke.empee3.core.network.tagger.WriteAuthorization
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
        private val filenameCleaner: FilenameCleaner,
        private val scorer: MetadataScorer,
        private val tagWriter: JAudioTaggerWriter,
        private val tagWriteAccessChecker: TagWriteAccessChecker,
        private val okHttpClient: OkHttpClient,
        private val logger: Logger,
    ) : MetadataRepository {
        private val providers: List<MetadataProvider> by lazy {
            listOf(musicBrainzProvider, deezerProvider, lastFmProvider)
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
                unavailableReason =
                    if (provider === lastFmProvider &&
                        !LastFmProvider.IS_CONFIGURED
                    ) {
                        "Last.fm rejects the app's API key"
                    } else {
                        null
                    },
                scorer = scorer,
                title = title,
                artist = artist,
                album = album,
                durationMs = durationMs,
                onFailure = { message, error -> logger.w(TAG, message, error) },
            )

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
                    val deezerList =
                        if (MetadataSources.matches(deezerProvider.providerName, sourceProvider)) {
                            deezerProvider.fetchAlbumArtList(albumTitle, artist)
                        } else {
                            emptyList()
                        }
                    // An album search: the album title stands in for the track title and the album.
                    val trackCandidates =
                        searchTrack(title = albumTitle, artist = artist, album = albumTitle, sourceProvider = sourceProvider)
                            .getOrDefault(emptyList())
                    val candidateUrls = trackCandidates.mapNotNull { it.coverArtUrl }.filter { it.isNotBlank() }

                    (deezerList + candidateUrls).distinct()
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
                            deezerProvider.fetchAlbumGenres(deezerAlbumId).firstOrNull()?.let { GenreLookup.Found(it, "Deezer album") }
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
            private const val USER_AGENT = "empee3/1.0.0 (https://github.com/lochanpthakke/empee3)"
            private const val MAX_ARTWORK_BYTES = 10L * 1024 * 1024
        }
    }

/**
 * The genre to write from MusicBrainz's curated release-group genres: the single most-voted one.
 * A tie at the top gives no genre rather than an arbitrary pick.
 */
internal fun genreFromMusicBrainz(genres: List<MusicBrainzGenreDto>): GenreLookup {
    val ranked = genres.filter { it.name.isNotBlank() }.sortedByDescending { it.count }
    val tied = ranked.takeWhile { it.count == ranked.first().count }
    return when {
        ranked.isEmpty() -> GenreLookup.NotFound("MusicBrainz has no genre for this album")
        tied.size > 1 -> GenreLookup.NotFound("MusicBrainz genres are tied: ${tied.joinToString { it.name }}")
        else -> GenreLookup.Found(ranked.first().name.trim(), "MusicBrainz release group")
    }
}

/**
 * One provider's part of a search: skipped when not selected or [unavailableReason] is set,
 * otherwise searched and scored. A provider that throws is FAILED / TIMED_OUT with its message,
 * never "no results"; the status reflects the best score, not the first result's.
 */
@Suppress("LongParameterList", "TooGenericExceptionCaught", "ReturnCount")
internal suspend fun searchProviderScored(
    provider: MetadataProvider,
    selection: String,
    unavailableReason: String?,
    scorer: MetadataScorer,
    title: String,
    artist: String?,
    album: String?,
    durationMs: Long?,
    onFailure: (String, Throwable) -> Unit = { _, _ -> },
): Pair<List<MetadataCandidate>, ProviderOutcome> {
    val name = provider.providerName
    if (!MetadataSources.matches(name, selection)) {
        return emptyList<MetadataCandidate>() to ProviderOutcome(name, status = ProviderStatus.SKIPPED)
    }
    if (unavailableReason != null) {
        return emptyList<MetadataCandidate>() to ProviderOutcome(name, status = ProviderStatus.SKIPPED, errorMessage = unavailableReason)
    }
    return try {
        val scored =
            provider.searchTrack(title, artist, album).map { candidate ->
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
        scored to ProviderOutcome(name, candidateCount = scored.size, status = status)
    } catch (e: CancellationException) {
        throw e
    } catch (e: java.net.SocketTimeoutException) {
        onFailure("Search timed out for $name: ${e.message}", e)
        emptyList<MetadataCandidate>() to ProviderOutcome(name, status = ProviderStatus.TIMED_OUT, errorMessage = "Request timed out")
    } catch (e: Exception) {
        onFailure("Search failed for $name: ${e.message}", e)
        emptyList<MetadataCandidate>() to
            ProviderOutcome(name, status = ProviderStatus.FAILED, errorMessage = e.message ?: "Search request failed")
    }
}
