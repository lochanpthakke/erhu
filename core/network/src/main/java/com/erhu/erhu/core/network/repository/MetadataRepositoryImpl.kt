package com.erhu.erhu.core.network.repository

import com.erhu.erhu.core.common.logging.Logger
import com.erhu.erhu.core.common.result.AppResult
import com.erhu.erhu.core.common.result.appResultOf
import com.erhu.erhu.core.common.result.getOrDefault
import com.erhu.erhu.core.model.Genres
import com.erhu.erhu.core.model.LyricsResult
import com.erhu.erhu.core.model.MetadataCandidate
import com.erhu.erhu.core.model.MetadataProvider
import com.erhu.erhu.core.model.ProviderOutcome
import com.erhu.erhu.core.model.ProviderStatus
import com.erhu.erhu.core.network.cleaner.CleanedFilename
import com.erhu.erhu.core.network.cleaner.FilenameCleaner
import com.erhu.erhu.core.network.model.MusicBrainzGenreDto
import com.erhu.erhu.core.network.provider.DeezerProvider
import com.erhu.erhu.core.network.provider.LrclibProvider
import com.erhu.erhu.core.network.provider.MusicBrainzProvider
import com.erhu.erhu.core.network.retry.RetryPolicy
import com.erhu.erhu.core.network.retry.classifyProviderFailure
import com.erhu.erhu.core.network.scorer.HIGH_CONFIDENCE_THRESHOLD
import com.erhu.erhu.core.network.scorer.MetadataScorer
import com.erhu.erhu.core.network.tagger.JAudioTaggerWriter
import com.erhu.erhu.core.network.tagger.TagWriteAccessChecker
import com.erhu.erhu.core.network.tagger.TagWriteRequest
import com.erhu.erhu.core.network.tagger.WriteAuthorization
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@Suppress("LongParameterList", "TooGenericExceptionCaught", "LongMethod", "CyclomaticComplexMethod", "NestedBlockDepth")
class MetadataRepositoryImpl
    @Inject
    constructor(
        private val musicBrainzProvider: MusicBrainzProvider,
        private val deezerProvider: DeezerProvider,
        private val lrclibProvider: LrclibProvider,
        private val iTunesProvider: com.erhu.erhu.core.network.provider.ITunesProvider,
        private val albumArtworkFinder: com.erhu.erhu.core.network.artwork.AlbumArtworkFinder,
        private val filenameCleaner: FilenameCleaner,
        private val scorer: MetadataScorer,
        private val tagWriter: JAudioTaggerWriter,
        private val tagWriteAccessChecker: TagWriteAccessChecker,
        private val okHttpClient: OkHttpClient,
        private val providerHealth: ProviderHealth,
        private val logger: Logger,
    ) : MetadataRepository {
        private val retryPolicy = RetryPolicy()

        private val providers: List<MetadataProvider> by lazy {
            listOf(iTunesProvider, musicBrainzProvider, deezerProvider)
        }

        override fun cleanFilename(
            rawFilename: String,
            customBlocklist: List<String>,
        ): CleanedFilename = filenameCleaner.cleanFilename(rawFilename, customBlocklist)

        override suspend fun searchTrack(
            title: String,
            artist: String?,
            album: String?,
            sourceProvider: String,
        ): AppResult<List<MetadataCandidate>> = searchAndScoreTrack(title, artist, album, null, sourceProvider)

        override suspend fun searchAndScoreTrack(
            title: String,
            artist: String?,
            album: String?,
            durationMs: Long?,
            sourceProvider: String,
        ): AppResult<List<MetadataCandidate>> =
            appResultOf {
                searchAndScoreTrackDetailed(title, artist, album, durationMs, sourceProvider)
                    .getOrDefault(
                        DetailedSearchResponse(emptyList(), emptyList()),
                    ).candidates
            }

        override suspend fun searchAndScoreTrackDetailed(
            title: String,
            artist: String?,
            album: String?,
            durationMs: Long?,
            sourceProvider: String,
        ): AppResult<DetailedSearchResponse> =
            appResultOf {
                withContext(Dispatchers.IO) {
                    val results =
                        providers
                            .map { provider ->
                                async { searchOne(provider, sourceProvider, title, artist, album, durationMs) }
                            }.awaitAll()
                    DetailedSearchResponse(
                        candidates = results.flatMap { it.first }.sortedByDescending { it.confidenceScore },
                        providerOutcomes = results.map { it.second },
                    )
                }
            }

        @Suppress("LongParameterList")
        private suspend fun searchOne(
            provider: MetadataProvider,
            selection: String,
            title: String,
            artist: String?,
            album: String?,
            durationMs: Long?,
        ): Pair<List<MetadataCandidate>, ProviderOutcome> =
            searchProviderScored(
                provider = provider,
                selection = selection,
                availability = availabilityOf(provider),
                scorer = scorer,
                title = title,
                artist = artist,
                album = album,
                durationMs = durationMs,
                retry = retryPolicy,
                log = { message -> logger.i(TAG, message) },
                onSessionFailure = { status, reason -> providerHealth.markUnusable(provider.providerName, status, reason) },
            )

        private fun availabilityOf(provider: MetadataProvider): ProviderAvailability = providerHealth.availabilityOf(provider.providerName)

        override suspend fun searchAlbumArtwork(
            query: com.erhu.erhu.core.network.artwork.AlbumArtworkQuery,
        ): AppResult<com.erhu.erhu.core.network.artwork.AlbumArtworkResult> =
            appResultOf {
                albumArtworkFinder.search(query)
            }

        override fun artworkPreviewUrl(url: String): String = albumArtworkFinder.cache.preview(url)

        override suspend fun fetchAlbumArt(
            albumTitle: String,
            artist: String?,
            sourceProvider: String,
        ): AppResult<String?> =
            appResultOf {
                albumArtworkFinder
                    .search(
                        com.erhu.erhu.core.network.artwork
                            .AlbumArtworkQuery(albumTitle, artist.orEmpty()),
                    ).recommended
                    ?.imageUrl
            }

        override suspend fun fetchAlbumArtCandidates(
            albumTitle: String,
            artist: String?,
            sourceProvider: String,
        ): AppResult<List<String>> =
            appResultOf {
                albumArtworkFinder
                    .search(
                        com.erhu.erhu.core.network.artwork
                            .AlbumArtworkQuery(albumTitle, artist.orEmpty()),
                    ).candidates
                    .map { it.imageUrl }
            }

        override suspend fun fetchArtistArtworkCandidates(
            artistName: String,
            sourceProvider: String,
        ): AppResult<List<String>> =
            appResultOf {
                fetchArtistArtworkCandidatesDetailed(artistName, sourceProvider)
                    .getOrDefault(ArtistArtworkSearchResponse(emptyList()))
                    .candidates
            }

        override suspend fun fetchArtistArtworkCandidatesDetailed(
            artistName: String,
            sourceProvider: String,
        ): AppResult<ArtistArtworkSearchResponse> =
            appResultOf {
                val trimmed = artistName.trim()
                if (trimmed.isBlank() || trimmed.equals("Unknown Artist", ignoreCase = true)) {
                    return@appResultOf ArtistArtworkSearchResponse(emptyList())
                }

                withContext(Dispatchers.IO) {
                    val deezerDeferred = async {
                        try { deezerProvider.fetchArtistPortraitList(trimmed) } catch (e: Exception) { emptyList() }
                    }
                    val iTunesDeferred = async {
                        try { iTunesProvider.fetchArtistArtworkList(trimmed) } catch (e: Exception) { emptyList() }
                    }
                    val mbidDeferred = async {
                        try { musicBrainzProvider.resolveArtistMbid(trimmed) } catch (e: Exception) { null }
                    }
                    val geniusDeferred = async {
                        try { fetchGeniusArtistArtwork(trimmed) } catch (e: Exception) { emptyList() }
                    }
                    val discogsDeferred = async {
                        try { fetchDiscogsArtistArtwork(trimmed) } catch (e: Exception) { emptyList() }
                    }
                    val wikipediaDeferred = async {
                        try { fetchWikipediaArtistArtwork(trimmed) } catch (e: Exception) { emptyList() }
                    }
                    val duckDuckGoDeferred = async {
                        try { fetchDuckDuckGoArtistArtwork(trimmed) } catch (e: Exception) { emptyList() }
                    }

                    val deezerUrls = deezerDeferred.await()
                    val iTunesUrls = iTunesDeferred.await()
                    val mbid = mbidDeferred.await()
                    val geniusUrls = geniusDeferred.await()
                    val discogsUrls = discogsDeferred.await()
                    val duckDuckGoUrls = duckDuckGoDeferred.await()

                    val mbWikiUrls =
                        if (mbid != null) {
                            fetchMusicBrainzWikidataArtwork(mbid)
                        } else {
                            emptyList()
                        }
                    val wikipediaUrls = (wikipediaDeferred.await() + mbWikiUrls).distinct()

                    val providerMap = linkedMapOf<String, List<String>>()
                    providerMap["DuckDuckGo Web Search"] = duckDuckGoUrls
                    providerMap["iTunes / Apple Music"] = iTunesUrls
                    providerMap["Discogs"] = discogsUrls
                    providerMap["Wikipedia / Wikimedia"] = wikipediaUrls
                    providerMap["Genius"] = geniusUrls
                    providerMap["Deezer"] = deezerUrls

                    val allCandidates = (duckDuckGoUrls + iTunesUrls + discogsUrls + wikipediaUrls + geniusUrls + deezerUrls).distinct()
                    ArtistArtworkSearchResponse(
                        candidates = allCandidates,
                        providerCandidates = providerMap,
                        resolvedMbid = mbid,
                    )
                }
            }

        private fun fetchMusicBrainzWikidataArtwork(mbid: String): List<String> {
            val urls = mutableListOf<String>()
            try {
                val dto = kotlinx.coroutines.runBlocking { musicBrainzProvider.lookupArtistUrlRels(mbid) }
                dto?.relations?.forEach { rel ->
                    val res = rel.url?.resource.orEmpty()
                    if (res.contains("commons.wikimedia.org", ignoreCase = true) || res.contains("upload.wikimedia.org", ignoreCase = true)) {
                        val fileName = res.substringAfterLast("/")
                        if (fileName.isNotBlank()) {
                            val directUrl = fetchWikimediaDirectUrl("File:$fileName")
                            if (!directUrl.isNullOrBlank()) urls.add(directUrl)
                        }
                    } else if (res.contains("wikidata.org/wiki/Q", ignoreCase = true)) {
                        val qId = res.substringAfterLast("/").trim()
                        val wikiImage = fetchWikidataImage(qId)
                        if (!wikiImage.isNullOrBlank()) {
                            urls.add(wikiImage)
                        }
                    }
                }
            } catch (e: Exception) {
                logger.w("MetadataRepository", "MusicBrainz Wikidata lookup failed for $mbid: ${e.message}", e)
            }
            return urls.distinct()
        }

        private fun fetchWikidataImage(wikidataId: String): String? {
            val url = "https://www.wikidata.org/w/api.php?action=wbgetentities&ids=$wikidataId&props=claims&format=json"
            val request = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) empee3/1.0.0").build()
            return try {
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return null
                    val bodyString = response.body?.string() ?: return null
                    val json = org.json.JSONObject(bodyString)
                    val entity = json.optJSONObject("entities")?.optJSONObject(wikidataId) ?: return null
                    val claims = entity.optJSONObject("claims") ?: return null
                    val p18Array = claims.optJSONArray("P18") ?: return null
                    if (p18Array.length() > 0) {
                        val mainSnak = p18Array.getJSONObject(0).optJSONObject("mainsnak")
                        val imageName = mainSnak?.optJSONObject("datavalue")?.optString("value")
                        if (!imageName.isNullOrBlank()) {
                            return fetchWikimediaDirectUrl("File:$imageName")
                        }
                    }
                    null
                }
            } catch (e: Exception) {
                null
            }
        }

        private fun fetchWikimediaDirectUrl(fileTitle: String): String? {
            val commUrl = "https://commons.wikimedia.org/w/api.php?action=query&titles=${java.net.URLEncoder.encode(fileTitle, "UTF-8")}&prop=imageinfo&iiprop=url&iiurlwidth=1000&format=json"
            val commReq = Request.Builder().url(commUrl).header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) empee3/1.0.0").build()
            return try {
                okHttpClient.newCall(commReq).execute().use { response ->
                    if (!response.isSuccessful) return null
                    val json = org.json.JSONObject(response.body?.string().orEmpty())
                    val pages = json.optJSONObject("query")?.optJSONObject("pages")
                    val firstKey = pages?.keys()?.next() ?: return null
                    val imgInfo = pages.optJSONObject(firstKey)?.optJSONArray("imageinfo")?.optJSONObject(0)
                    imgInfo?.optString("thumburl", "")?.ifBlank { imgInfo.optString("url", "") }?.takeIf { it.isNotBlank() }
                }
            } catch (e: Exception) {
                null
            }
        }

        private fun fetchDiscogsArtistArtwork(artistName: String): List<String> {
            val encodedName = java.net.URLEncoder.encode(artistName, "UTF-8")
            val url = "https://www.discogs.com/search/?q=$encodedName&type=artist"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept", "text/html")
                .build()
            return try {
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return emptyList()
                    val html = response.body?.string().orEmpty()
                    val urls = mutableListOf<String>()
                    val imgRegex = Regex("""https://i\.discogs\.com/[^"'\s>?<]+\.(?:jpg|jpeg|png)""", RegexOption.IGNORE_CASE)
                    imgRegex.findAll(html).forEach { match ->
                        val imgSrc = match.value
                        if (!imgSrc.contains("spacer.gif") && !imgSrc.contains("default-artist")) {
                            urls.add(imgSrc)
                        }
                    }
                    if (urls.isEmpty()) {
                        val imgRegex2 = Regex("""data-src=["'](https://[^"'\s>]+)["']""")
                        imgRegex2.findAll(html).forEach { match ->
                            val src = match.groupValues[1]
                            if (src.contains("discogs") && (src.endsWith(".jpg") || src.endsWith(".jpeg") || src.endsWith(".png"))) {
                                urls.add(src)
                            }
                        }
                    }
                    urls.distinct().take(3)
                }
            } catch (e: Exception) {
                emptyList()
            }
        }

        private fun fetchGeniusArtistArtwork(artistName: String): List<String> {
            val url = "https://genius.com/api/search/multi?q=${java.net.URLEncoder.encode(artistName, "UTF-8")}"
            val request = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) empee3/1.0.0").build()
            return try {
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return emptyList()
                    val bodyString = response.body?.string() ?: return emptyList()
                    val json = org.json.JSONObject(bodyString)
                    val sections = json.optJSONObject("response")?.optJSONArray("sections") ?: return emptyList()
                    val urls = mutableListOf<String>()
                    for (i in 0 until sections.length()) {
                        val sec = sections.getJSONObject(i)
                        val secType = sec.optString("type", "")
                        if (secType.equals("artist", ignoreCase = true)) {
                            val hits = sec.optJSONArray("hits") ?: continue
                            for (j in 0 until hits.length()) {
                                val hit = hits.getJSONObject(j)
                                val result = hit.optJSONObject("result") ?: continue
                                val hitName = result.optString("name", "").trim()
                                if (hitName.equals(artistName, ignoreCase = true)) {
                                    val avatar = result.optString("image_url", "")
                                    val header = result.optString("header_image_url", "")
                                    val customHeader = result.optString("custom_header_image_url", "")
                                    listOf(avatar, header, customHeader).forEach { img ->
                                        if (img.isNotBlank() && !img.contains("default_avatar") && !img.contains("avatars.genius.com/default")) {
                                            urls.add(img)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    urls.distinct()
                }
            } catch (e: Exception) {
                emptyList()
            }
        }

        private fun fetchWikipediaArtistArtwork(artistName: String): List<String> {
            val formattedArtist = artistName.replace(" ", "_")
            val urls = mutableListOf<String>()
            val categoryNames = listOf("Category:${formattedArtist}_(musician)", "Category:${formattedArtist}", "Category:${formattedArtist}_(singer)")
            for (catName in categoryNames) {
                try {
                    val encodedCat = java.net.URLEncoder.encode(catName, "UTF-8")
                    val commonsUrl = "https://commons.wikimedia.org/w/api.php?action=query&generator=categorymembers&gcmtitle=$encodedCat&gcmtype=file&gcmlimit=50&prop=imageinfo&iiprop=url&iiurlwidth=1000&format=json"
                    val commonsReq = Request.Builder().url(commonsUrl).header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) empee3/1.0.0").build()
                    okHttpClient.newCall(commonsReq).execute().use { response ->
                        if (response.isSuccessful) {
                            val bodyString = response.body?.string() ?: ""
                            val json = org.json.JSONObject(bodyString)
                            val pages = json.optJSONObject("query")?.optJSONObject("pages")
                            if (pages != null) {
                                val keys = pages.keys()
                                while (keys.hasNext()) {
                                    val page = pages.optJSONObject(keys.next())
                                    val imageInfo = page?.optJSONArray("imageinfo")
                                    if (imageInfo != null && imageInfo.length() > 0) {
                                        val imgObj = imageInfo.getJSONObject(0)
                                        val imgUrl = imgObj.optString("thumburl", "").ifBlank { imgObj.optString("url", "") }
                                        if (imgUrl.isNotBlank() && (imgUrl.endsWith(".jpg", ignoreCase = true) || imgUrl.endsWith(".png", ignoreCase = true) || imgUrl.endsWith(".jpeg", ignoreCase = true))) {
                                            urls.add(imgUrl)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // ignore
                }
                if (urls.size >= 10) break
            }

            if (urls.size < 5) {
                try {
                    val searchUrl = "https://commons.wikimedia.org/w/api.php?action=query&generator=search&gsrsearch=${java.net.URLEncoder.encode(artistName, "UTF-8")}&gsrnamespace=6&gsrlimit=25&prop=imageinfo&iiprop=url&iiurlwidth=1000&format=json"
                    val searchReq = Request.Builder().url(searchUrl).header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) empee3/1.0.0").build()
                    okHttpClient.newCall(searchReq).execute().use { response ->
                        if (response.isSuccessful) {
                            val json = org.json.JSONObject(response.body?.string().orEmpty())
                            val pages = json.optJSONObject("query")?.optJSONObject("pages")
                            if (pages != null) {
                                val keys = pages.keys()
                                while (keys.hasNext()) {
                                    val page = pages.optJSONObject(keys.next())
                                    val imageInfo = page?.optJSONArray("imageinfo")
                                    if (imageInfo != null && imageInfo.length() > 0) {
                                        val imgObj = imageInfo.getJSONObject(0)
                                        val imgUrl = imgObj.optString("thumburl", "").ifBlank { imgObj.optString("url", "") }
                                        if (imgUrl.isNotBlank() && (imgUrl.endsWith(".jpg", ignoreCase = true) || imgUrl.endsWith(".png", ignoreCase = true) || imgUrl.endsWith(".jpeg", ignoreCase = true))) {
                                            urls.add(imgUrl)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // ignore
                }
            }

            return urls.distinct()
        }

        private fun fetchDuckDuckGoArtistArtwork(artistName: String): List<String> {
            val encodedName = java.net.URLEncoder.encode(artistName, "UTF-8")
            val urls = mutableListOf<String>()
            try {
                val initUrl = "https://duckduckgo.com/?q=$encodedName"
                val initReq = Request.Builder()
                    .url(initUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build()
                var vqd: String? = null
                okHttpClient.newCall(initReq).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    vqd = response.header("x-vqd-4")
                        ?: Regex("vqd=[\"']?([^\"'&]+)").find(body)?.groupValues?.get(1)
                }

                if (!vqd.isNullOrBlank()) {
                    val imgUrl = "https://duckduckgo.com/i.js?q=$encodedName&o=json&vqd=$vqd"
                    val imgReq = Request.Builder()
                        .url(imgUrl)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .header("Referer", "https://duckduckgo.com/")
                        .build()
                    okHttpClient.newCall(imgReq).execute().use { response ->
                        if (response.isSuccessful) {
                            val json = org.json.JSONObject(response.body?.string().orEmpty())
                            val results = json.optJSONArray("results")
                            if (results != null) {
                                for (i in 0 until results.length()) {
                                    val item = results.getJSONObject(i)
                                    val image = item.optString("image", "").ifBlank { item.optString("thumbnail", "") }
                                    if (image.isNotBlank() && image.startsWith("http")) {
                                        urls.add(image)
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // ignore
            }
            return urls.distinct().take(15)
        }

        override suspend fun lookupGenre(
            match: MetadataCandidate,
            candidates: List<MetadataCandidate>,
        ): GenreLookup =
            withContext(Dispatchers.IO) {
                try {
                    val releaseGroupId = match.releaseGroupId
                    val deezerAlbumId = match.deezerAlbumId

                    val mbGenre =
                        if (releaseGroupId != null) {
                            try {
                                genreFromMusicBrainz(musicBrainzProvider.fetchReleaseGroupGenres(releaseGroupId))
                            } catch (e: Exception) {
                                null
                            }
                        } else {
                            null
                        }
                    if (mbGenre is GenreLookup.Found) return@withContext mbGenre

                    val deezerGenre =
                        if (deezerAlbumId != null) {
                            try {
                                Genres
                                    .normalize(deezerProvider.fetchAlbumGenres(deezerAlbumId))
                                    .takeIf { it.isNotEmpty() }
                                    ?.let { GenreLookup.Found(it, "Deezer album") }
                            } catch (e: Exception) {
                                null
                            }
                        } else {
                            null
                        }
                    if (deezerGenre is GenreLookup.Found) return@withContext deezerGenre

                    val allCandidates = listOf(match) + candidates
                    val siblingGenre =
                        allCandidates
                            .mapNotNull { c ->
                                c.genre?.let { Genres.parse(it) }?.takeIf { it.isNotEmpty() }?.let { it to c.sourceName }
                            }
                            .firstOrNull()

                    if (siblingGenre != null) {
                        return@withContext GenreLookup.Found(siblingGenre.first, "${siblingGenre.second} genre")
                    }

                    GenreLookup.NotFound("No genre found for ${match.sourceName} candidate")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.w(TAG, "Genre lookup failed: ${e.message}", e)
                    GenreLookup.Failed(e.message ?: "Genre lookup failed")
                }
            }

        override suspend fun downloadArtwork(url: String): AppResult<ByteArray> =
            appResultOf {
                withContext(Dispatchers.IO) {
                    albumArtworkFinder.cache.bytes(url)?.let { return@withContext it }
                    val request =
                        Request
                            .Builder()
                            .url(url)
                            .header("User-Agent", USER_AGENT)
                            .build()
                    okHttpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) throw java.io.IOException("Artwork download failed: HTTP ${response.code}")
                        val body = response.body ?: throw java.io.IOException("Artwork download returned no data")
                        val type = body.contentType()
                        if (type != null && type.type != "image") throw java.io.IOException("Artwork URL is not an image ($type)")
                        if (body.contentLength() > MAX_ARTWORK_BYTES) throw java.io.IOException("Artwork is too large")
                        val bytes = body.bytes()
                        if (bytes.isEmpty() || bytes.size > MAX_ARTWORK_BYTES) throw java.io.IOException("Artwork has an invalid size")
                        bytes
                    }
                }
            }

        override suspend fun fetchLyrics(
            title: String,
            artist: String,
            album: String?,
            durationSec: Int?,
        ): AppResult<LyricsResult?> =
            appResultOf {
                lrclibProvider.fetchLyrics(title, artist, album, durationSec)
            }

        override suspend fun writeTagsToFile(request: TagWriteRequest): AppResult<Unit> = tagWriter.writeTags(request)

        override fun checkTagWriteAccess(contentUris: List<String>): WriteAuthorization = tagWriteAccessChecker.check(contentUris)

        companion object {
            private const val TAG = "MetadataRepositoryImpl"
            private const val USER_AGENT = "erhu/1.0.0 (https://github.com/lochanpthakke/empee3)"
            private const val MAX_ARTWORK_BYTES = 10L * 1024 * 1024
        }
    }

/**
 * The genres to write from MusicBrainz's curated release-group genres: every usable one, most-voted
 * first. Genres with equal votes are all kept (in MusicBrainz's order): a tie used to give no genre
 * at all, which left most albums (one vote per genre is common) without one.
 */
internal fun genreFromMusicBrainz(genres: List<MusicBrainzGenreDto>): GenreLookup {
    // A genre voted down to zero or below is not one MusicBrainz vouches for.
    val ranked = Genres.normalize(genres.filter { it.count > 0 }.sortedByDescending { it.count }.map { it.name })
    return if (ranked.isEmpty()) {
        GenreLookup.NotFound("MusicBrainz has no genre for this album")
    } else {
        GenreLookup.Found(ranked, "MusicBrainz release group")
    }
}

/** Whether a provider may be asked at all, and if not, the reason shown instead of results. */
sealed interface ProviderAvailability {
    data object Available : ProviderAvailability

    /** Access was refused earlier this session: not requested again automatically. */
    data class Rejected(
        val reason: String,
    ) : ProviderAvailability

    /** It said earlier this session that it doesn't serve this country: not called again. */
    data class Unavailable(
        val reason: String,
    ) : ProviderAvailability
}

/**
 * Providers that can't help for the rest of this session: access refused, or the service
 * withheld in this country. One request finds out; later tracks get the same truthful status
 * without asking again. Cleared by restarting the app.
 */
@Singleton
class ProviderHealth
    @Inject
    constructor() {
        private val unusable = java.util.concurrent.ConcurrentHashMap<String, ProviderAvailability>()

        fun markUnusable(
            providerName: String,
            status: ProviderStatus,
            reason: String,
        ) {
            when (status) {
                ProviderStatus.AUTH_FAILED -> unusable[providerName] = ProviderAvailability.Rejected(reason)
                ProviderStatus.UNAVAILABLE -> unusable[providerName] = ProviderAvailability.Unavailable(reason)
                else -> Unit
            }
        }

        fun availabilityOf(providerName: String): ProviderAvailability = unusable[providerName] ?: ProviderAvailability.Available
    }

/**
 * One provider's part of a search. Not selected: [ProviderStatus.SKIPPED]; access refused
 * earlier: [ProviderStatus.AUTH_FAILED], all without a
 * request. Otherwise the request runs under [retry] (transient failures only, bounded backoff,
 * Retry-After respected) and a failure keeps its real category: network, timeout, rate limit,
 * credentials or other, never "no results". The status reflects the best score, not the first.
 */
@Suppress("LongParameterList", "TooGenericExceptionCaught", "ReturnCount", "LongMethod")
internal suspend fun searchProviderScored(
    provider: MetadataProvider,
    selection: String,
    availability: ProviderAvailability,
    scorer: MetadataScorer,
    title: String,
    artist: String?,
    album: String?,
    durationMs: Long?,
    retry: RetryPolicy = RetryPolicy(),
    log: (String) -> Unit = {},
    onSessionFailure: (ProviderStatus, String) -> Unit = { _, _ -> },
): Pair<List<MetadataCandidate>, ProviderOutcome> {
    val name = provider.providerName
    if (!MetadataSources.matches(name, selection)) {
        return emptyList<MetadataCandidate>() to
            ProviderOutcome(name, status = ProviderStatus.SKIPPED, errorMessage = "Not selected (source: ${sourceLabel(selection)})")
    }
    when (availability) {
        is ProviderAvailability.Rejected -> {
            log("provider=$name decision=not-queried reason=access-refused-earlier")
            return emptyList<MetadataCandidate>() to
                ProviderOutcome(name, status = ProviderStatus.AUTH_FAILED, errorMessage = "${availability.reason} (not retried)")
        }
        is ProviderAvailability.Unavailable -> {
            log("provider=$name decision=not-queried reason=unavailable-here")
            return emptyList<MetadataCandidate>() to
                ProviderOutcome(name, status = ProviderStatus.UNAVAILABLE, errorMessage = "${availability.reason} (not asked again)")
        }
        ProviderAvailability.Available -> Unit
    }
    return try {
        val found =
            retry.run(
                onRetry = { attempt, failure, waitMs ->
                    log("provider=$name retry=$attempt cause=${failure.status} waitMs=$waitMs")
                },
            ) {
                provider.searchTrack(title, artist, album)
            }
        val scored =
            found.map { candidate ->
                scorer.scoreCandidate(
                    candidate = candidate,
                    targetTitle = title,
                    targetArtist = artist,
                    targetAlbum = album,
                    targetDurationMs = durationMs,
                )
            }
        val topScore = scored.maxOfOrNull { it.confidenceScore } ?: 0f
        val status =
            when {
                scored.isEmpty() -> ProviderStatus.NO_RESULTS
                topScore >= HIGH_CONFIDENCE_THRESHOLD -> ProviderStatus.SUCCESS
                else -> ProviderStatus.LOW_CONFIDENCE
            }
        log("provider=$name decision=queried outcome=$status candidates=${scored.size}")
        scored to ProviderOutcome(name, candidateCount = scored.size, status = status)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        val failure = classifyProviderFailure(e)
        log("provider=$name decision=queried outcome=${failure.status} error=${e.javaClass.simpleName}")
        onSessionFailure(failure.status, failure.message)
        emptyList<MetadataCandidate>() to ProviderOutcome(name, status = failure.status, errorMessage = failure.message)
    }
}

private fun sourceLabel(selection: String): String = if (selection == MetadataSources.ALL) "all providers" else selection
