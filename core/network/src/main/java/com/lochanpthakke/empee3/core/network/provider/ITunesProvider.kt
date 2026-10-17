package com.lochanpthakke.empee3.core.network.provider

import com.lochanpthakke.empee3.core.common.logging.Logger
import com.lochanpthakke.empee3.core.model.MetadataCandidate
import com.lochanpthakke.empee3.core.model.MetadataProvider
import com.lochanpthakke.empee3.core.network.api.ITunesApi
import com.lochanpthakke.empee3.core.network.tagger.ReleaseDate
import kotlinx.coroutines.CancellationException
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ITunesProvider
    @Inject
    constructor(
        private val iTunesApi: ITunesApi,
        private val logger: Logger,
    ) : MetadataProvider {
        override val providerName: String = "iTunes"

        override suspend fun searchTrack(
            title: String,
            artist: String?,
            album: String?,
        ): List<MetadataCandidate> {
            val query =
                listOfNotNull(
                    artist?.takeIf { it.isNotBlank() && it != "Unknown Artist" },
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
                                    com.lochanpthakke.empee3.core.network.tagger.ReleaseDate
                                        .parse(it, today)
                                }
                        com.lochanpthakke.empee3.core.model.MetadataCandidate(
                            id = "itunes_${dto.trackId ?: dto.collectionId ?: dto.trackName.hashCode()}",
                            title = dto.trackName.orEmpty(),
                            artist = dto.artistName.orEmpty(),
                            album = dto.collectionName.orEmpty(),
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
                val response = iTunesApi.search(term = trimmed, entity = "album", limit = 5)
                val exactMatches = response.results.filter { it.artistName?.trim()?.equals(trimmed, ignoreCase = true) == true }
                val targetResults = if (exactMatches.isNotEmpty()) exactMatches else response.results
                targetResults
                    .mapNotNull { it.artworkUrl100 ?: it.artworkUrl60 }
                    .map { upgradeArtworkUrl(it) }
                    .distinct()
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
