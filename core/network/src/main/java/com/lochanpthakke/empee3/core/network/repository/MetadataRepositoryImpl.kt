package com.lochanpthakke.empee3.core.network.repository

import com.lochanpthakke.empee3.core.common.logging.Logger
import com.lochanpthakke.empee3.core.common.result.AppResult
import com.lochanpthakke.empee3.core.common.result.appResultOf
import com.lochanpthakke.empee3.core.model.LyricsResult
import com.lochanpthakke.empee3.core.model.MetadataCandidate
import com.lochanpthakke.empee3.core.model.MetadataProvider
import com.lochanpthakke.empee3.core.network.cleaner.CleanedFilename
import com.lochanpthakke.empee3.core.network.cleaner.FilenameCleaner
import com.lochanpthakke.empee3.core.network.provider.DeezerProvider
import com.lochanpthakke.empee3.core.network.provider.LastFmProvider
import com.lochanpthakke.empee3.core.network.provider.LrclibProvider
import com.lochanpthakke.empee3.core.network.provider.MusicBrainzProvider
import com.lochanpthakke.empee3.core.network.scorer.MetadataScorer
import com.lochanpthakke.empee3.core.network.tagger.JAudioTaggerWriter
import com.lochanpthakke.empee3.core.network.tagger.TagWriteRequest
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
            sourceProvider: String,
        ): AppResult<List<MetadataCandidate>> =
            appResultOf {
                withContext(Dispatchers.IO) {
                    val selectedProviders =
                        if (sourceProvider == "ALL") {
                            providers
                        } else {
                            providers.filter { it.providerName.equals(sourceProvider, ignoreCase = true) }
                        }

                    val deferredResults =
                        selectedProviders.map { provider ->
                            async {
                                try {
                                    provider.searchTrack(title, artist)
                                } catch (e: Exception) {
                                    logger.w(TAG, "Search failed for ${provider.providerName}: ${e.message}", e)
                                    emptyList()
                                }
                            }
                        }

                    val allCandidates = deferredResults.awaitAll().flatten()
                    val scoredCandidates =
                        allCandidates
                            .map { candidate ->
                                scorer.scoreCandidate(candidate, title, artist)
                            }.sortedByDescending { it.confidenceScore }

                    scoredCandidates
                }
            }

        override suspend fun fetchAlbumArt(
            albumTitle: String,
            artist: String?,
            sourceProvider: String,
        ): AppResult<String?> =
            appResultOf {
                withContext(Dispatchers.IO) {
                    val selectedProviders =
                        if (sourceProvider == "ALL") {
                            providers
                        } else {
                            providers.filter { it.providerName.equals(sourceProvider, ignoreCase = true) }
                        }

                    for (provider in selectedProviders) {
                        try {
                            val artUrl = provider.fetchAlbumArt(albumTitle, artist)
                            if (!artUrl.isNullOrBlank()) {
                                return@withContext artUrl
                            }
                        } catch (e: Exception) {
                            logger.d(TAG, "Album art fetch failed for ${provider.providerName}: ${e.message}")
                        }
                    }
                    null
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

        companion object {
            private const val TAG = "MetadataRepositoryImpl"
        }
    }
