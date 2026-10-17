package com.lochanpthakke.empee3.core.network.provider

import com.lochanpthakke.empee3.core.common.logging.Logger
import com.lochanpthakke.empee3.core.network.api.DuckDuckGoApi
import kotlinx.coroutines.CancellationException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@Suppress("TooGenericExceptionCaught")
class DuckDuckGoImageSearchProvider
    @Inject
    constructor(
        private val duckDuckGoApi: DuckDuckGoApi,
        private val logger: Logger,
    ) {
        suspend fun fetchArtistPortraits(artistName: String): List<String> {
            val trimmed = artistName.trim()
            if (trimmed.isBlank() || trimmed.equals("Unknown Artist", ignoreCase = true)) return emptyList()

            try {
                // 1. Try DuckDuckGo vqd + i.js search
                val searchUrl = "https://duckduckgo.com/?q=${URLEncoder.encode("$trimmed artist portrait", StandardCharsets.UTF_8.name())}"
                val htmlPage = duckDuckGoApi.getSearchPage(searchUrl).use { it.string() }
                val vqdRegex = Regex("""vqd=([0-9-]+)""")
                val vqd = vqdRegex.find(htmlPage)?.groupValues?.get(1)

                if (!vqd.isNullOrBlank()) {
                    val res = duckDuckGoApi.searchImages(
                        query = "$trimmed artist portrait",
                        vqd = vqd,
                    )
                    val urls = res.results.orEmpty().mapNotNull { it.image }.filter { it.isNotBlank() }.distinct()
                    if (urls.isNotEmpty()) {
                        return urls.take(15)
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                logger.w(TAG, "DuckDuckGo i.js search failed for $artistName, trying Bing async fallback: ${e.message}")
            }

            // 2. Immediate fallback: Bing Images async HTML fragment extraction
            return try {
                val encodedQuery = URLEncoder.encode("$trimmed musician artist portrait", StandardCharsets.UTF_8.name())
                val bingUrl = "https://www.bing.com/images/async?q=$encodedQuery&first=1&count=20&mmasync=1"
                val html = duckDuckGoApi.fetchHtml(bingUrl).use { it.string() }
                val murlRegex = Regex("""murl(?:&quot;|"):(?:&quot;|")(https?://[^"&]+?\.(?:jpg|jpeg|png))""", RegexOption.IGNORE_CASE)
                murlRegex.findAll(html)
                    .map { it.groupValues[1].replace("&amp;", "&") }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .take(15)
                    .toList()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                logger.w(TAG, "Bing async fallback search failed for $artistName: ${e.message}", e)
                emptyList()
            }
        }

        companion object {
            private const val TAG = "DuckDuckGoImageSearchProvider"
        }
    }
