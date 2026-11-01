package com.erhu.erhu.core.network.provider

import com.erhu.erhu.core.common.logging.Logger
import com.erhu.erhu.core.network.api.GeniusApi
import com.erhu.erhu.core.network.model.GeniusHitDto
import kotlinx.coroutines.CancellationException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@Suppress("TooGenericExceptionCaught", "CyclomaticComplexMethod", "NestedBlockDepth", "LongMethod")
class GeniusProvider
    @Inject
    constructor(
        private val geniusApi: GeniusApi,
        private val musicBrainzProvider: MusicBrainzProvider,
        private val logger: Logger,
    ) {
        suspend fun fetchArtistPortraits(artistName: String): List<String> {
            val trimmed = artistName.trim()
            if (trimmed.isBlank() || trimmed.equals("Unknown Artist", ignoreCase = true)) return emptyList()

            return try {
                val validNames = mutableSetOf(trimmed.lowercase())
                val mbid = musicBrainzProvider.resolveArtistMbid(trimmed)
                if (!mbid.isNullOrBlank()) {
                    musicBrainzProvider.lookupArtistUrlRels(mbid)?.name?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }?.let {
                        validNames.add(it)
                    }
                }

                val urls = mutableListOf<String>()

                // Tier 1: Genius JSON Search API
                try {
                    val allHits = mutableListOf<GeniusHitDto>()

                    try {
                        val searchRes = geniusApi.search(trimmed)
                        allHits.addAll(searchRes.response?.hits.orEmpty())
                        allHits.addAll(
                            searchRes.response
                                ?.sections
                                .orEmpty()
                                .flatMap { it.hits.orEmpty() },
                        )
                    } catch (e: Exception) {
                        logger.w(TAG, "Genius /api/search failed for $trimmed: ${e.message}")
                    }

                    try {
                        val artistRes = geniusApi.searchArtist(trimmed)
                        allHits.addAll(
                            artistRes.response
                                ?.sections
                                .orEmpty()
                                .flatMap { it.hits.orEmpty() },
                        )
                        allHits.addAll(artistRes.response?.hits.orEmpty())
                    } catch (e: Exception) {
                        logger.w(TAG, "Genius /api/search/artist failed for $trimmed: ${e.message}")
                    }

                    if (allHits.isEmpty()) {
                        try {
                            val multiRes = geniusApi.searchMulti(trimmed)
                            allHits.addAll(
                                multiRes.response
                                    ?.sections
                                    .orEmpty()
                                    .flatMap { it.hits.orEmpty() },
                            )
                            allHits.addAll(multiRes.response?.hits.orEmpty())
                        } catch (e: Exception) {
                            logger.w(TAG, "Genius /api/search/multi failed for $trimmed: ${e.message}")
                        }
                    }

                    fun isNameMatch(candidate: String?): Boolean {
                        val clean = candidate?.trim()?.lowercase().orEmpty()
                        if (clean.isEmpty()) return false
                        return validNames.any { valid ->
                            valid == clean || clean.contains(valid) || (clean.length >= 2 && valid.contains(clean))
                        }
                    }

                    // 1. Direct artist hits
                    for (hit in allHits) {
                        val res = hit.result ?: continue
                        val isArtistHit = hit.type.equals("artist", ignoreCase = true) || (res.title == null && !res.name.isNullOrBlank())
                        if (isArtistHit && isNameMatch(res.name)) {
                            res.imageUrl?.let { urls.add(it) }
                            res.headerImageUrl?.let { urls.add(it) }
                        }
                    }

                    // 2. Song/lyric hits primaryArtist
                    for (hit in allHits) {
                        val res = hit.result ?: continue
                        val songMatchesArtist = isNameMatch(res.artistNames)

                        res.primaryArtist?.let { pa ->
                            if (songMatchesArtist || isNameMatch(pa.name)) {
                                pa.imageUrl?.let { urls.add(it) }
                                pa.headerImageUrl?.let { urls.add(it) }
                            }
                        }

                        res.primaryArtists.orEmpty().forEach { artist ->
                            if (isNameMatch(artist.name)) {
                                artist.imageUrl?.let { urls.add(it) }
                                artist.headerImageUrl?.let { urls.add(it) }
                            }
                        }
                    }

                    // 3. Fallback: top 5 hits primaryArtist
                    if (urls.isEmpty()) {
                        for (hit in allHits.take(5)) {
                            val res = hit.result ?: continue
                            res.primaryArtist?.let { pa ->
                                pa.imageUrl?.let { urls.add(it) }
                                pa.headerImageUrl?.let { urls.add(it) }
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    logger.w(TAG, "Tier 1 Genius JSON API failed for $trimmed: ${e.message}")
                }

                // Tier 2: Direct Genius Artist Page HTML / Meta Tag Extraction
                if (urls.isEmpty()) {
                    val candidateSlugs =
                        validNames
                            .map { name ->
                                name.replace(Regex("""[^a-zA-Z0-9]+"""), "-").trim('-')
                            }.filter { it.isNotBlank() }
                            .distinct()

                    for (slug in candidateSlugs) {
                        try {
                            val pageUrl = "https://genius.com/artists/$slug"
                            val html = geniusApi.fetchRawPage(pageUrl).use { it.string() }

                            val ogImageRegex =
                                Regex("""<meta\s+property=["']og:image["']\s+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                            val imgMatch = ogImageRegex.find(html)?.groupValues?.get(1)
                            if (!imgMatch.isNullOrBlank()) {
                                urls.add(imgMatch)
                            }

                            val jsonImageRegex = Regex(""""(?:image_url|header_image_url)"\s*:\s*"([^"]+)"""", RegexOption.IGNORE_CASE)
                            jsonImageRegex.findAll(html).forEach { match ->
                                val rawUrl =
                                    match.groupValues[1]
                                        .replace("\\u0026", "&")
                                        .replace("\\/", "/")
                                if (rawUrl.isNotBlank() && rawUrl.contains("images.genius.com")) {
                                    urls.add(rawUrl)
                                }
                            }

                            if (urls.isNotEmpty()) break
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            logger.w(TAG, "Tier 2 Genius artist page extraction failed for $slug: ${e.message}")
                        }
                    }
                }

                // Tier 3: Direct Web Search Fallback for Genius Artist Portraits
                if (urls.isEmpty()) {
                    try {
                        val encodedQuery = URLEncoder.encode("$trimmed genius artist portrait", StandardCharsets.UTF_8.name())
                        val bingUrl = "https://www.bing.com/images/async?q=$encodedQuery&first=1&count=20&mmasync=1"
                        val html = geniusApi.fetchRawPage(bingUrl).use { it.string() }
                        val murlRegex =
                            Regex("""murl(?:&quot;|"):(?:&quot;|")(https?://[^"&]+?\.(?:jpg|jpeg|png))""", RegexOption.IGNORE_CASE)
                        val webUrls =
                            murlRegex
                                .findAll(html)
                                .map { it.groupValues[1].replace("&amp;", "&") }
                                .filter { it.isNotBlank() }
                                .distinct()
                                .toList()

                        val geniusHostUrls = webUrls.filter { it.contains("genius.com") || it.contains("rapgenius") }
                        if (geniusHostUrls.isNotEmpty()) {
                            urls.addAll(geniusHostUrls)
                        } else {
                            urls.addAll(webUrls.take(10))
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        logger.w(TAG, "Tier 3 Genius web search fallback failed for $trimmed: ${e.message}")
                    }
                }

                urls
                    .map { it.trim() }
                    .filter { url ->
                        url.isNotBlank() &&
                            !url.contains("default_avatar", ignoreCase = true) &&
                            !url.contains("default_cover", ignoreCase = true) &&
                            !url.contains("assets.genius.com/images/default", ignoreCase = true)
                    }.distinct()
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
