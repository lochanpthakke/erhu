package com.lochanpthakke.empee3.core.network.provider

import com.lochanpthakke.empee3.core.common.logging.Logger
import com.lochanpthakke.empee3.core.network.api.FanartTvApi
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FanartTvProvider
    @Inject
    constructor(
        private val fanartTvApi: FanartTvApi,
        private val musicBrainzProvider: MusicBrainzProvider,
        private val logger: Logger,
    ) {
        suspend fun fetchArtistPortraits(
            artistName: String,
            preResolvedMbid: String? = null,
        ): List<String> {
            val trimmed = artistName.trim()
            if (trimmed.isBlank() || trimmed.equals("Unknown Artist", ignoreCase = true)) return emptyList()

            return try {
                val mbids = if (!preResolvedMbid.isNullOrBlank()) {
                    (listOf(preResolvedMbid) + musicBrainzProvider.resolveArtistMbids(trimmed)).distinct().take(5)
                } else {
                    musicBrainzProvider.resolveArtistMbids(trimmed).take(5)
                }

                for (mbid in mbids) {
                    try {
                        val response = fanartTvApi.getArtistImages(musicBrainzId = mbid)
                        val thumbs = response.artistthumb.orEmpty()
                            .sortedByDescending { it.likes?.toIntOrNull() ?: 0 }
                            .mapNotNull { it.url }
                        val backgrounds = response.artistbackground.orEmpty()
                            .sortedByDescending { it.likes?.toIntOrNull() ?: 0 }
                            .mapNotNull { it.url }
                        val images = (thumbs + backgrounds).filter { it.isNotBlank() }.distinct()
                        if (images.isNotEmpty()) return images
                    } catch (e: HttpException) {
                        if (e.code() == 404) continue else throw e
                    }
                }
                emptyList()
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                if (e.code() == 404) {
                    logger.d(TAG, "No Fanart.tv artwork found for $artistName (404)")
                } else {
                    logger.w(TAG, "Fanart.tv HTTP ${e.code()} for $artistName: ${e.message}")
                }
                emptyList()
            } catch (e: Exception) {
                logger.w(TAG, "Fanart.tv artist portrait search failed for $artistName: ${e.message}", e)
                emptyList()
            }
        }

        companion object {
            private const val TAG = "FanartTvProvider"
        }
    }
