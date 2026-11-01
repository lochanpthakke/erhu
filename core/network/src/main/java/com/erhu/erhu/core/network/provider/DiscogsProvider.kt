package com.erhu.erhu.core.network.provider

import com.erhu.erhu.core.common.logging.Logger
import com.erhu.erhu.core.network.api.DiscogsApi
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DiscogsProvider
    @Inject
    constructor(
        private val discogsApi: DiscogsApi,
        private val musicBrainzProvider: MusicBrainzProvider,
        private val logger: Logger,
    ) {
        suspend fun fetchArtistPortraits(
            artistName: String,
            preResolvedMbid: String? = null,
        ): List<String> {
            val trimmed = artistName.trim()
            if (trimmed.isBlank() || trimmed.equals("Unknown Artist", ignoreCase = true)) return emptyList()

            val urls = mutableListOf<String>()

            // 1. Try Discogs artist ID extracted from MusicBrainz relations
            try {
                val mbids =
                    if (!preResolvedMbid.isNullOrBlank()) {
                        (listOf(preResolvedMbid) + musicBrainzProvider.resolveArtistMbids(trimmed)).distinct().take(3)
                    } else {
                        musicBrainzProvider.resolveArtistMbids(trimmed).take(3)
                    }

                val discogsIdRegex = Regex("""/artist/(\d+)""")
                for (mbid in mbids) {
                    val artistDetails = musicBrainzProvider.lookupArtistUrlRels(mbid) ?: continue
                    val discogsRel = artistDetails.relations.firstOrNull { it.type.equals("discogs", ignoreCase = true) }
                    val discogsUrl = discogsRel?.url?.resource.orEmpty()
                    val discogsId =
                        discogsIdRegex
                            .find(discogsUrl)
                            ?.groupValues
                            ?.get(1)
                            ?.toLongOrNull()

                    if (discogsId != null && discogsId > 0) {
                        val detail = discogsApi.getArtistDetail(discogsId)
                        val images = detail.images.orEmpty().mapNotNull { it.uri ?: it.uri150 }
                        if (images.isNotEmpty()) {
                            urls.addAll(images)
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                logger.d(TAG, "Discogs ID lookup via MBID failed: ${e.message}")
            }

            // 2. Fall back to Discogs search if ID lookup yielded no images
            if (urls.isEmpty()) {
                try {
                    val response = discogsApi.searchArtist(query = trimmed)
                    val results = response.results.orEmpty()
                    val found =
                        results
                            .mapNotNull { it.coverImage ?: it.thumb }
                            .filter { it.isNotBlank() && !it.endsWith(".gif", ignoreCase = true) }
                    urls.addAll(found)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    logger.w(TAG, "Discogs artist portrait search failed for $artistName: ${e.message}", e)
                }
            }

            return urls.filter { it.isNotBlank() && !it.endsWith(".gif", ignoreCase = true) }.distinct()
        }

        companion object {
            private const val TAG = "DiscogsProvider"
        }
    }
