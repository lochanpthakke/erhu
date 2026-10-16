package com.lochanpthakke.empee3.core.network.provider

import com.lochanpthakke.empee3.core.common.logging.Logger
import com.lochanpthakke.empee3.core.network.api.WikipediaApi
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WikipediaProvider
    @Inject
    constructor(
        private val wikipediaApi: WikipediaApi,
        private val logger: Logger,
    ) {
        suspend fun fetchArtistPortrait(artistName: String): String? {
            val trimmed = artistName.trim()
            if (trimmed.isBlank() || trimmed.equals("Unknown Artist", ignoreCase = true)) return null

            return try {
                val response = wikipediaApi.getPageImage(titles = trimmed)
                val pages = response.query?.pages.orEmpty()
                val leadImageUrl = pages.values.firstOrNull { !it.thumbnail?.source.isNullOrBlank() }?.thumbnail?.source
                leadImageUrl
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.w(TAG, "Wikipedia portrait search failed for $artistName: ${e.message}", e)
                null
            }
        }

        companion object {
            private const val TAG = "WikipediaProvider"
        }
    }
