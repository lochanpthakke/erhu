package com.lochanpthakke.empee3.core.network.repository

import com.lochanpthakke.empee3.core.common.logging.Logger
import com.lochanpthakke.empee3.core.common.result.AppResult
import com.lochanpthakke.empee3.core.common.result.appResultOf
import com.lochanpthakke.empee3.core.common.result.getOrDefault
import com.lochanpthakke.empee3.core.model.LyricsResult
import com.lochanpthakke.empee3.core.model.MetadataCandidate
import com.lochanpthakke.empee3.core.model.MetadataProvider
import com.lochanpthakke.empee3.core.network.cleaner.CleanedFilename
import com.lochanpthakke.empee3.core.network.cleaner.FilenameCleaner
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
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
                searchAndScoreTrackDetailed(title, artist, album, durationMs, sourceProvider).getOrDefault(
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
                    val outcomes = mutableListOf<com.lochanpthakke.empee3.core.model.ProviderOutcome>()
                    val allCandidates = mutableListOf<MetadataCandidate>()

                    val deferredList =
                        providers.map { provider ->
                            val isSelected =
                                sourceProvider == "ALL" || provider.providerName.equals(sourceProvider, ignoreCase = true)
                            async {
                                if (!isSelected) {
                                    provider.providerName to Pair(
                                        emptyList<MetadataCandidate>(),
                                        com.lochanpthakke.empee3.core.model.ProviderOutcome(
                                            providerName = provider.providerName,
                                            candidateCount = 0,
                                            status = com.lochanpthakke.empee3.core.model.ProviderStatus.SKIPPED,
                                        ),
                                    )
                                } else {
                                    try {
                                        val rawCandidates = provider.searchTrack(title, artist, album)
                                        val scored =
                                            rawCandidates.map { candidate ->
                                                scorer.scoreCandidate(
                                                    candidate = candidate,
                                                    targetTitle = title,
                                                    targetArtist = artist,
                                                    targetAlbum = album,
                                                    targetDurationMs = durationMs,
                                                )
                                            }
                                        val topScore = scored.firstOrNull()?.confidenceScore ?: 0f
                                        val status =
                                            when {
                                                scored.isEmpty() -> com.lochanpthakke.empee3.core.model.ProviderStatus.NO_RESULTS
                                                topScore >= HIGH_CONFIDENCE_THRESHOLD -> com.lochanpthakke.empee3.core.model.ProviderStatus.SUCCESS
                                                else -> com.lochanpthakke.empee3.core.model.ProviderStatus.LOW_CONFIDENCE
                                            }
                                        val outcome =
                                            com.lochanpthakke.empee3.core.model.ProviderOutcome(
                                                providerName = provider.providerName,
                                                candidateCount = scored.size,
                                                status = status,
                                            )
                                        provider.providerName to Pair(scored, outcome)
                                    } catch (e: java.net.SocketTimeoutException) {
                                        logger.w(TAG, "Search timed out for ${provider.providerName}: ${e.message}", e)
                                        provider.providerName to Pair(
                                            emptyList<MetadataCandidate>(),
                                            com.lochanpthakke.empee3.core.model.ProviderOutcome(
                                                providerName = provider.providerName,
                                                candidateCount = 0,
                                                status = com.lochanpthakke.empee3.core.model.ProviderStatus.TIMED_OUT,
                                                errorMessage = "Request timed out",
                                            ),
                                        )
                                    } catch (e: Exception) {
                                        logger.w(TAG, "Search failed for ${provider.providerName}: ${e.message}", e)
                                        provider.providerName to Pair(
                                            emptyList<MetadataCandidate>(),
                                            com.lochanpthakke.empee3.core.model.ProviderOutcome(
                                                providerName = provider.providerName,
                                                candidateCount = 0,
                                                status = com.lochanpthakke.empee3.core.model.ProviderStatus.FAILED,
                                                errorMessage = e.message ?: "Search request failed",
                                            ),
                                        )
                                    }
                                }
                            }
                        }

                    val results = deferredList.awaitAll()
                    results.forEach { (_, pair) ->
                        allCandidates.addAll(pair.first)
                        outcomes.add(pair.second)
                    }

                    val sortedCandidates = allCandidates.sortedByDescending { it.confidenceScore }
                    DetailedSearchResponse(
                        candidates = sortedCandidates,
                        providerOutcomes = outcomes,
                    )
                }
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
                    val deezerList = deezerProvider.fetchAlbumArtList(albumTitle, artist)
                    val trackCandidates = searchTrack(albumTitle, artist, sourceProvider).getOrDefault(emptyList())
                    val candidateUrls = trackCandidates.mapNotNull { it.coverArtUrl }.filter { it.isNotBlank() }

                    (deezerList + candidateUrls).distinct()
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
        }
    }
