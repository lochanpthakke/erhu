package com.lochanpthakke.empee3.core.network.provider

import com.lochanpthakke.empee3.core.common.logging.Logger
import com.lochanpthakke.empee3.core.network.api.FanartTvApi
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FanartTvCredentials
    @Inject
    constructor() {
        @Volatile
        var apiKey: String = ""

        val isConfigured: Boolean get() = apiKey.isNotBlank()
    }

@Singleton
@Suppress("TooGenericExceptionCaught", "CyclomaticComplexMethod")
class FanartTvProvider
    @Inject
    constructor(
        private val fanartTvApi: FanartTvApi,
        private val musicBrainzProvider: MusicBrainzProvider,
        val credentials: FanartTvCredentials,
        private val logger: Logger,
    ) {
        val isConfigured: Boolean
            get() = credentials.isConfigured

        suspend fun fetchArtistPortraits(
            artistName: String,
            preResolvedMbid: String? = null,
        ): List<String> {
            val apiKey = credentials.apiKey.trim()
            if (apiKey.isBlank()) return emptyList()

            val trimmed = artistName.trim()
            if (trimmed.isBlank() || trimmed.equals("Unknown Artist", ignoreCase = true)) return emptyList()

            return try {
                val candidateMbids = mutableListOf<String>()
                if (!preResolvedMbid.isNullOrBlank()) candidateMbids.add(preResolvedMbid)
                candidateMbids.addAll(musicBrainzProvider.resolveArtistMbids(trimmed))

                val distinctMbids = candidateMbids.filter { it.isNotBlank() }.distinct().take(5)
                val urls = mutableListOf<String>()

                for (mbid in distinctMbids) {
                    try {
                        val response = fanartTvApi.getArtistImages(musicBrainzId = mbid, apiKey = apiKey)
                        val thumbs =
                            response.artistthumb
                                .orEmpty()
                                .sortedByDescending { it.likes?.toIntOrNull() ?: 0 }
                                .mapNotNull { it.url }
                        val backgrounds =
                            response.artistbackground
                                .orEmpty()
                                .sortedByDescending { it.likes?.toIntOrNull() ?: 0 }
                                .mapNotNull { it.url }

                        (thumbs + backgrounds).forEach { rawUrl ->
                            if (rawUrl.isNotBlank()) {
                                urls.add(rawUrl.replace("http://", "https://"))
                            }
                        }

                        if (urls.isNotEmpty()) break
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        logger.w(TAG, "Fanart.tv query failed for MBID $mbid: ${e.message}")
                    }
                }

                urls.distinct()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.w(TAG, "Fanart.tv artist search failed for $artistName: ${e.message}", e)
                emptyList()
            }
        }

        companion object {
            private const val TAG = "FanartTvProvider"
        }
    }
