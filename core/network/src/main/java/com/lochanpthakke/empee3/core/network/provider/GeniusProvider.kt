package com.lochanpthakke.empee3.core.network.provider

import com.lochanpthakke.empee3.core.common.logging.Logger
import com.lochanpthakke.empee3.core.network.api.GeniusApi
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeniusProvider
    @Inject
    constructor(
        private val geniusApi: GeniusApi,
        private val logger: Logger,
    ) {
        suspend fun fetchArtistPortraits(artistName: String): List<String> {
            val trimmed = artistName.trim()
            if (trimmed.isBlank() || trimmed.equals("Unknown Artist", ignoreCase = true)) return emptyList()

            return try {
                val urls = mutableListOf<String>()
                val queries = listOf(trimmed, "$trimmed musician", "$trimmed artist")

                for (q in queries) {
                    try {
                        val response = try {
                            geniusApi.searchMulti(query = q)
                        } catch (_: Exception) {
                            geniusApi.searchArtist(query = q)
                        }

                        val hits = (response.response?.hits.orEmpty() + response.response?.sections.orEmpty().flatMap { it.hits.orEmpty() })

                        for (hit in hits) {
                            val artistObj = hit.artist
                            if (artistObj != null) {
                                listOfNotNull(artistObj.imageUrl, artistObj.headerImageUrl).forEach { urls.add(it) }
                            }
                            val res = hit.result
                            if (res != null) {
                                if (res.name.trim().contains(trimmed, ignoreCase = true) || res.title.trim().contains(trimmed, ignoreCase = true)) {
                                    listOfNotNull(res.imageUrl, res.headerImageUrl).forEach { urls.add(it) }
                                }
                                val pa = res.primaryArtist
                                if (pa != null && (pa.name.trim().contains(trimmed, ignoreCase = true) || trimmed.contains(pa.name.trim(), ignoreCase = true))) {
                                    listOfNotNull(pa.imageUrl, pa.headerImageUrl).forEach { urls.add(it) }
                                }
                            }
                        }

                        val validUrls = urls.filter { url ->
                            url.isNotBlank() &&
                                !url.contains("/assets/") &&
                                !url.contains("default_avatar") &&
                                !url.contains("assets.genius.com/images/default")
                        }
                        if (validUrls.isNotEmpty()) {
                            return validUrls.distinct()
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        logger.w(TAG, "Genius query failed for '$q': ${e.message}")
                    }
                }

                urls.distinct()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.w(TAG, "Genius artist portrait search failed for $artistName: ${e.message}", e)
                emptyList()
            }
        }

        companion object {
            private const val TAG = "GeniusProvider"
        }
    }
