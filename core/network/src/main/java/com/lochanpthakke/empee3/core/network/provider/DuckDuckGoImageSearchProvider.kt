package com.lochanpthakke.empee3.core.network.provider

import com.lochanpthakke.empee3.core.common.logging.Logger
import com.lochanpthakke.empee3.core.network.api.DuckDuckGoApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Unofficial / best-effort keyless web image search provider using DuckDuckGo's public image API.
 */
@Singleton
class DuckDuckGoImageSearchProvider
    @Inject
    constructor(
        private val duckDuckGoApi: DuckDuckGoApi,
        private val logger: Logger,
    ) {
        suspend fun fetchArtistPortraits(artistName: String): List<String> {
            val trimmed = artistName.trim()
            if (trimmed.isBlank() || trimmed.equals("Unknown Artist", ignoreCase = true)) return emptyList()

            val query = "$trimmed artist"

            var attempt = 0
            while (attempt < MAX_RETRIES) {
                try {
                    // 1. Obtain dynamic vqd security token from search landing page
                    val responseBody = duckDuckGoApi.getSearchPage(query = query)
                    val html = responseBody.string()
                    var vqdMatch = VQD_REGEXES.firstNotNullOfOrNull { it.find(html)?.groupValues?.get(1) }

                    if (vqdMatch == null) {
                        try {
                            val encodedQ = java.net.URLEncoder.encode(query, "UTF-8")
                            val htmlBody = duckDuckGoApi.getHtmlPage("https://html.duckduckgo.com/html/?q=$encodedQ")
                            val fallbackHtml = htmlBody.string()
                            vqdMatch = VQD_REGEXES.firstNotNullOfOrNull { it.find(fallbackHtml)?.groupValues?.get(1) }
                        } catch (_: Exception) {
                            // Fallback failed
                        }
                    }

                    if (vqdMatch == null) {
                        logger.w(TAG, "DuckDuckGo vqd token extraction failed for $artistName")
                        return emptyList()
                    }

                    // 2. Query image search endpoint with valid vqd token and browser Referer
                    val response = duckDuckGoApi.searchImages(query = query, vqdToken = vqdMatch)
                    val results = response.results.orEmpty()
                    return results
                        .mapNotNull { it.image ?: it.thumbnail }
                        .filter { it.isNotBlank() }
                        .distinct()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: HttpException) {
                    val code = e.code()
                    if ((code == 202 || code == 403 || code == 429) && attempt < MAX_RETRIES - 1) {
                        attempt++
                        delay(BACKOFF_DELAY_MS * attempt)
                    } else {
                        logger.w(TAG, "DuckDuckGo HTTP $code for $artistName: ${e.message}")
                        return emptyList()
                    }
                } catch (e: Exception) {
                    logger.w(TAG, "DuckDuckGo image search failed for $artistName: ${e.message}", e)
                    return emptyList()
                }
            }
            return emptyList()
        }

        companion object {
            private const val TAG = "DuckDuckGoImageSearchProvider"
            private const val MAX_RETRIES = 3
            private const val BACKOFF_DELAY_MS = 500L
            private val VQD_REGEXES = listOf(
                Regex("""vqd=["']?([0-9a-zA-Z_-]+)["']?"""),
                Regex("""vqd=([0-9-]+)&"""),
                Regex("""["']vqd["']\s*:\s*["']([^"']+)["']"""),
            )
        }
    }
