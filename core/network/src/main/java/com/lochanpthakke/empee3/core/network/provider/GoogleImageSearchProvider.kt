package com.lochanpthakke.empee3.core.network.provider

import com.lochanpthakke.empee3.core.common.logging.Logger
import com.lochanpthakke.empee3.core.network.api.GoogleImageSearchApi
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleImageSearchProvider
    @Inject
    constructor(
        private val googleApi: GoogleImageSearchApi,
        private val logger: Logger,
    ) {
        suspend fun fetchArtistPortraits(
            artistName: String,
            apiKey: String? = null,
            cx: String? = null,
        ): List<String> {
            val trimmed = artistName.trim()
            if (trimmed.isBlank() || trimmed.equals("Unknown Artist", ignoreCase = true)) return emptyList()

            return try {
                val query = trimmed
                val response =
                    googleApi.searchImages(
                        query = query,
                        searchType = "image",
                        imgType = "photo",
                        num = 10,
                        apiKey = apiKey,
                        cx = cx,
                    )
                val items = response.items.orEmpty()
                items.mapNotNull { it.link }.filter { it.isNotBlank() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.w(TAG, "Google Image search failed for $artistName: ${e.message}", e)
                emptyList()
            }
        }

        companion object {
            private const val TAG = "GoogleImageSearchProvider"
        }
    }
