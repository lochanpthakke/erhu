package com.erhu.erhu.core.network.provider

import com.erhu.erhu.core.common.logging.Logger
import com.erhu.erhu.core.network.api.WikidataApi
import com.erhu.erhu.core.network.api.WikipediaApi
import kotlinx.coroutines.CancellationException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WikipediaProvider
    @Inject
    constructor(
        private val wikipediaApi: WikipediaApi,
        private val wikidataApi: WikidataApi,
        private val musicBrainzProvider: MusicBrainzProvider,
        private val logger: Logger,
    ) {
        suspend fun fetchArtistPortrait(artistName: String): String? = fetchArtistPortraits(artistName).firstOrNull()

        suspend fun fetchArtistPortraits(
            artistName: String,
            preResolvedMbid: String? = null,
        ): List<String> {
            val trimmed = artistName.trim()
            if (trimmed.isBlank() || trimmed.equals("Unknown Artist", ignoreCase = true)) return emptyList()

            val urls = mutableListOf<String>()

            return try {
                // 1. Try MBID / Wikidata resolution in isolated try block
                val mbids =
                    if (!preResolvedMbid.isNullOrBlank()) {
                        (listOf(preResolvedMbid) + musicBrainzProvider.resolveArtistMbids(trimmed)).distinct().take(3)
                    } else {
                        musicBrainzProvider.resolveArtistMbids(trimmed).take(3)
                    }

                for (mbid in mbids) {
                    try {
                        val artistDetails = musicBrainzProvider.lookupArtistUrlRels(mbid) ?: continue

                        // Check direct Wikipedia URL relation
                        val wpRel = artistDetails.relations.firstOrNull { it.type.equals("wikipedia", ignoreCase = true) }
                        val wpArticle =
                            wpRel
                                ?.url
                                ?.resource
                                .orEmpty()
                                .substringAfterLast("/")
                        if (wpArticle.isNotBlank()) {
                            try {
                                val wpPageResponse = wikipediaApi.getPageImage(titles = wpArticle)
                                val page =
                                    wpPageResponse.query
                                        ?.pages
                                        ?.values
                                        ?.firstOrNull()
                                val img = page?.thumbnail?.source
                                if (!img.isNullOrBlank()) urls.add(normalizeImageUrl(img))
                            } catch (_: Exception) {
                                // Direct page image lookup failed
                            }
                        }

                        // Check Wikidata QID relation
                        val wikidataRel = artistDetails.relations.firstOrNull { it.type.equals("wikidata", ignoreCase = true) }
                        val qid =
                            wikidataRel
                                ?.url
                                ?.resource
                                .orEmpty()
                                .substringAfterLast(
                                    "/",
                                ).takeIf { it.startsWith("Q", ignoreCase = true) }

                        if (!qid.isNullOrBlank()) {
                            val entityData = wikidataApi.getEntityData(qid = qid)
                            val entity = entityData.entities[qid]
                            if (entity != null) {
                                val p18Claims = entity.claims["P18"].orEmpty().sortedByDescending { if (it.rank == "preferred") 1 else 0 }
                                p18Claims.forEach { claim ->
                                    val filename = claim.mainsnak?.datavalue?.stringValue
                                    if (!filename.isNullOrBlank()) {
                                        val encoded = URLEncoder.encode(filename.replace(" ", "_"), StandardCharsets.UTF_8.name())
                                        urls.add("https://commons.wikimedia.org/wiki/Special:FilePath/$encoded?width=1000")
                                    }
                                }

                                val p373Claims = entity.claims["P373"].orEmpty()
                                p373Claims.forEach { claim ->
                                    val categoryName = claim.mainsnak?.datavalue?.stringValue
                                    if (!categoryName.isNullOrBlank()) {
                                        try {
                                            val encodedCat = URLEncoder.encode("Category:$categoryName", StandardCharsets.UTF_8.name())
                                            val commonsUrl = "https://commons.wikimedia.org/w/api.php?action=query&generator=categorymembers&gcmtitle=$encodedCat&gcmnamespace=6&gcmlimit=15&prop=imageinfo&iiprop=url|size|mime&iiurlwidth=1000&format=json"
                                            val catResponse = wikipediaApi.listCommonsCategoryMembers(url = commonsUrl)
                                            catResponse.query?.pages?.values?.forEach { page ->
                                                val info = page.imageinfo?.firstOrNull()
                                                val mime = info?.mime?.lowercase().orEmpty()
                                                val imgUrl = info?.url ?: info?.thumbUrl ?: page.thumbnail?.source
                                                if (!imgUrl.isNullOrBlank() &&
                                                    (
                                                        mime == "image/jpeg" ||
                                                            mime == "image/png" ||
                                                            imgUrl.endsWith(".jpg", ignoreCase = true) ||
                                                            imgUrl.endsWith(".png", ignoreCase = true)
                                                    )
                                                ) {
                                                    urls.add(normalizeImageUrl(imgUrl))
                                                }
                                            }
                                        } catch (_: Exception) {
                                            // Category member list failed
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        logger.d(TAG, "Wikidata/MBID lookup failed for $mbid: ${e.message}")
                    }
                }

                // 2. Direct Wikipedia page & search fallbacks (always execute to supplement candidates)
                try {
                    val pageResp = wikipediaApi.getPageImage(titles = trimmed)
                    pageResp.query
                        ?.pages
                        ?.values
                        ?.mapNotNull { it.thumbnail?.source }
                        ?.forEach { urls.add(normalizeImageUrl(it)) }
                } catch (_: Exception) {
                    // Direct page image failed
                }

                try {
                    val searchResponse = wikipediaApi.searchPageImages(query = "$trimmed musician")
                    searchResponse.query
                        ?.pages
                        ?.values
                        ?.mapNotNull { it.thumbnail?.source }
                        ?.forEach { urls.add(normalizeImageUrl(it)) }
                } catch (_: Exception) {
                    // Search failed
                }

                try {
                    val searchResponse = wikipediaApi.searchPageImages(query = trimmed)
                    searchResponse.query
                        ?.pages
                        ?.values
                        ?.mapNotNull { it.thumbnail?.source }
                        ?.forEach { urls.add(normalizeImageUrl(it)) }
                } catch (_: Exception) {
                    // Search failed
                }

                urls.distinct()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.w(TAG, "Wikimedia/Wikidata resolution failed for $artistName: ${e.message}", e)
                emptyList()
            }
        }

        companion object {
            private const val TAG = "WikipediaProvider"
        }
    }

private fun normalizeImageUrl(url: String): String {
    val trimmed = url.trim()
    return when {
        trimmed.startsWith("//") -> "https:$trimmed"
        trimmed.startsWith("http://") -> "https://" + trimmed.substring(7)
        else -> trimmed
    }
}
