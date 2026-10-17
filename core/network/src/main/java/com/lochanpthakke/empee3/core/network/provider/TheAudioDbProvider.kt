package com.lochanpthakke.empee3.core.network.provider

import com.lochanpthakke.empee3.core.common.logging.Logger
import com.lochanpthakke.empee3.core.network.api.TheAudioDbApi
import com.lochanpthakke.empee3.core.network.model.TheAudioDbArtistDto
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TheAudioDbProvider
    @Inject
    constructor(
        private val theAudioDbApi: TheAudioDbApi,
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
                var images = emptyList<String>()

                // 1. Try search.php?s={artistName} first (free-tier working endpoint)
                try {
                    val searchResponse = theAudioDbApi.searchArtist(artistName = trimmed)
                    val candidates = searchResponse.artists.orEmpty()
                    images = candidates.flatMap(::extractImages).distinct()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    logger.w(TAG, "TheAudioDB name search failed for $trimmed: ${e.message}")
                }

                // 2. If name search returned no images, try MBID lookup
                if (images.isEmpty()) {
                    val mbids = (listOfNotNull(preResolvedMbid?.takeIf { it.isNotBlank() }) + musicBrainzProvider.resolveArtistMbids(trimmed)).distinct().take(5)
                    for (mbid in mbids) {
                        try {
                            val mbidResponse = theAudioDbApi.getArtistByMbid(mbid = mbid)
                            val mbidImages = mbidResponse.artists.orEmpty().flatMap(::extractImages).distinct()
                            if (mbidImages.isNotEmpty()) {
                                images = mbidImages
                                break
                            }
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            logger.w(TAG, "TheAudioDB MBID lookup failed for $mbid: ${e.message}")
                        }
                    }
                }

                images
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.w(TAG, "TheAudioDB artist portrait search failed for $artistName: ${e.message}", e)
                emptyList()
            }
        }

        private fun extractImages(dto: TheAudioDbArtistDto): List<String> =
            listOfNotNull(
                dto.strArtistThumb,
                dto.strArtistFanart,
                dto.strArtistFanart2,
                dto.strArtistFanart3,
                dto.strArtistFanart4,
                dto.strArtistWideThumb,
                dto.strArtistBanner,
            ).filter { it.isNotBlank() }.distinct()

        companion object {
            private const val TAG = "TheAudioDbProvider"
        }
    }
