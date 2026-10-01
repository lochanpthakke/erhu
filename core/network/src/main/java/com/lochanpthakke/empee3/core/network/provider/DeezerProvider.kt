@file:Suppress("detekt:All")

package com.lochanpthakke.empee3.core.network.provider

import com.lochanpthakke.empee3.core.common.logging.Logger
import com.lochanpthakke.empee3.core.model.MetadataCandidate
import com.lochanpthakke.empee3.core.model.MetadataProvider
import com.lochanpthakke.empee3.core.network.api.DeezerApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeezerProvider
    @Inject
    constructor(
        private val deezerApi: DeezerApi,
        private val logger: Logger,
    ) : MetadataProvider {
        override val providerName: String = "Deezer"

        override suspend fun searchTrack(
            title: String,
            artist: String?,
        ): List<MetadataCandidate> {
            val query =
                if (!artist.isNullOrBlank()) {
                    "artist:\"$artist\" track:\"$title\""
                } else {
                    title
                }

            return try {
                val response = deezerApi.searchTrack(query)
                response.data.map { dto ->
                    MetadataCandidate(
                        id = "deezer_${dto.id}",
                        title = dto.title,
                        artist = dto.artist?.name ?: "Unknown Artist",
                        album = dto.album?.title ?: "Unknown Album",
                        coverArtUrl = dto.album?.coverXl ?: dto.album?.coverBig ?: dto.album?.coverMedium,
                        sourceName = providerName,
                    )
                }
            } catch (e: Exception) {
                logger.w(TAG, "Deezer search failed: ${e.message}", e)
                emptyList()
            }
        }

        override suspend fun fetchAlbumArt(
            albumTitle: String,
            artist: String?,
        ): String? {
            val urls = fetchAlbumArtList(albumTitle, artist)
            return urls.firstOrNull()
        }

        suspend fun fetchAlbumArtList(
            albumTitle: String,
            artist: String?,
        ): List<String> {
            val strictQuery =
                if (!artist.isNullOrBlank() && artist != "Unknown Artist") {
                    "artist:\"$artist\" album:\"$albumTitle\""
                } else {
                    albumTitle
                }

            val urls = mutableListOf<String>()

            try {
                val response = deezerApi.searchTrack(strictQuery)
                val coverUrls = response.data.mapNotNull { it.album?.coverXl ?: it.album?.coverBig ?: it.album?.coverMedium }
                urls.addAll(coverUrls)
            } catch (e: Exception) {
                logger.w(TAG, "Deezer album art fetch failed: ${e.message}", e)
            }

            if (urls.isEmpty() && albumTitle.isNotBlank()) {
                try {
                    val fallbackResponse = deezerApi.searchTrack(albumTitle)
                    val fallbackUrls = fallbackResponse.data.mapNotNull { it.album?.coverXl ?: it.album?.coverBig ?: it.album?.coverMedium }
                    urls.addAll(fallbackUrls)
                } catch (e: Exception) {
                    logger.w(TAG, "Deezer fallback album art fetch failed: ${e.message}", e)
                }
            }

            return urls.distinct()
        }

        companion object {
            private const val TAG = "DeezerProvider"
        }
    }
