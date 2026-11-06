package com.erhu.erhu.core.network.provider

import com.erhu.erhu.core.common.logging.Logger
import com.erhu.erhu.core.model.MetadataCandidate
import com.erhu.erhu.core.model.MetadataProvider
import com.erhu.erhu.core.network.api.ITunesApi
import com.erhu.erhu.core.network.tagger.ReleaseDate
import kotlinx.coroutines.CancellationException
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ITunesProvider
    @Inject
    constructor(
        private val iTunesApi: ITunesApi,
        private val okHttpClient: okhttp3.OkHttpClient,
        private val logger: Logger,
    ) : MetadataProvider {
        override val providerName: String = "iTunes"

        override suspend fun searchTrack(
            title: String,
            artist: String?,
            album: String?,
        ): List<MetadataCandidate> {
            val cleanArtist =
                artist?.takeIf { it.isNotBlank() && it != "Unknown Artist" }
                    ?.substringBefore('/')
                    ?.substringBefore(';')
                    ?.trim()
            val query =
                listOfNotNull(
                    cleanArtist,
                    title.takeIf { it.isNotBlank() },
                ).joinToString(" ").trim()
            if (query.isBlank()) return emptyList()

            return try {
                val response = iTunesApi.search(term = query, entity = "song", limit = 10)
                val today = java.time.LocalDate.now()
                response.results
                    .filter { !it.trackName.isNullOrBlank() }
                    .map { dto ->
                        val date =
                            dto.releaseDate
                                ?.take(
                                    10,
                                )?.let {
                                    com.erhu.erhu.core.network.tagger.ReleaseDate
                                        .parse(it, today)
                                }
                        com.erhu.erhu.core.model.MetadataCandidate(
                            id = "itunes_${dto.trackId ?: dto.collectionId ?: dto.trackName.hashCode()}",
                            title = dto.trackName.orEmpty(),
                            artist = dto.artistName.orEmpty(),
                            album = dto.collectionName.orEmpty(),
                            genre = dto.primaryGenreName?.trim()?.takeIf { it.isNotEmpty() },
                            durationMs = dto.trackTimeMillis,
                            coverArtUrl = (dto.artworkUrl100 ?: dto.artworkUrl60)?.let { upgradeArtworkUrl(it) },
                            sourceName = "iTunes",
                            year = date?.year ?: 0,
                            releaseDate = date?.tagValue,
                        )
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.w(TAG, "iTunes song search failed for $query: ${e.message}", e)
                emptyList()
            }
        }

        override suspend fun fetchAlbumArt(
            albumTitle: String,
            artist: String?,
        ): String? = fetchAlbumArtList(albumTitle, artist).firstOrNull()

        suspend fun fetchAlbumArtList(
            albumTitle: String,
            artist: String?,
        ): List<String> {
            val query =
                listOfNotNull(artist?.takeIf { it.isNotBlank() && it != "Unknown Artist" }, albumTitle)
                    .joinToString(" ")
                    .trim()
            if (query.isBlank()) return emptyList()

            val urls = mutableListOf<String>()
            try {
                val response = iTunesApi.search(term = query, entity = "album", limit = 10)
                val highResUrls =
                    response.results
                        .mapNotNull { it.artworkUrl100 ?: it.artworkUrl60 }
                        .map { upgradeArtworkUrl(it) }
                urls.addAll(highResUrls)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.w(TAG, "iTunes album art search failed for $query: ${e.message}", e)
            }

            if (urls.isEmpty() && albumTitle.isNotBlank()) {
                try {
                    val fallbackResponse = iTunesApi.search(term = albumTitle, entity = "album", limit = 10)
                    val fallbackUrls =
                        fallbackResponse.results
                            .mapNotNull { it.artworkUrl100 ?: it.artworkUrl60 }
                            .map { upgradeArtworkUrl(it) }
                    urls.addAll(fallbackUrls)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.w(TAG, "iTunes fallback album art search failed: ${e.message}", e)
                }
            }

            return urls.distinct()
        }

        suspend fun fetchArtistArtworkList(artistName: String): List<String> {
            val trimmed = artistName.trim()
            if (trimmed.isBlank() || trimmed.equals("Unknown Artist", ignoreCase = true)) return emptyList()

            return try {
                val urls = mutableListOf<String>()
                val artistResponse = iTunesApi.search(term = trimmed, entity = "musicArtist", limit = 5)
                artistResponse.results.forEach { result ->
                    val art = result.artworkUrl100 ?: result.artworkUrl60
                    if (!art.isNullOrBlank()) {
                        urls.add(upgradeArtworkUrl(art))
                    }
                }
                if (urls.isEmpty()) {
                    val encodedName = java.net.URLEncoder.encode(trimmed, "UTF-8")
                    val searchUrl = "https://music.apple.com/us/search?term=$encodedName"
                    val req = okhttp3.Request.Builder()
                        .url(searchUrl)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .build()
                    okHttpClient.newCall(req).execute().use { res ->
                        if (res.isSuccessful) {
                            val html = res.body?.string().orEmpty()
                            val metaRegex = Regex("""<meta\s+property=["']og:image["']\s+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                            metaRegex.find(html)?.groupValues?.get(1)?.let { metaUrl ->
                                if (metaUrl.contains("mzstatic.com")) {
                                    urls.add(upgradeArtworkUrl(metaUrl))
                                }
                            }
                            if (urls.isEmpty()) {
                                val imgRegex = Regex("""https://is[0-9]-ssl\.mzstatic\.com/image/thumb/Artist[^"'\s>?<]+\.jpg""", RegexOption.IGNORE_CASE)
                                imgRegex.findAll(html).forEach { match ->
                                    val imgSrc = upgradeArtworkUrl(match.value)
                                    if (!urls.contains(imgSrc)) urls.add(imgSrc)
                                }
                            }
                        }
                    }
                }
                urls.distinct().take(3)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.w(TAG, "iTunes artist artwork search failed for $artistName: ${e.message}", e)
                emptyList()
            }
        }

        private fun upgradeArtworkUrl(url: String): String =
            url
                .replace("100x100bb", "1000x1000bb")
                .replace("60x60bb", "1000x1000bb")
                .replace("100x100", "1000x1000")

        companion object {
            private const val TAG = "ITunesProvider"
        }
    }
