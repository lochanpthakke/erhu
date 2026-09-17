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
            val query =
                if (!artist.isNullOrBlank()) {
                    "artist:\"$artist\" album:\"$albumTitle\""
                } else {
                    "album:\"$albumTitle\""
                }

            return try {
                val response = deezerApi.searchTrack(query)
                val firstMatch = response.data.firstOrNull { it.album != null }
                firstMatch?.album?.coverXl ?: firstMatch?.album?.coverBig ?: firstMatch?.album?.coverMedium
            } catch (e: Exception) {
                logger.w(TAG, "Deezer album art fetch failed: ${e.message}", e)
                null
            }
        }

        companion object {
            private const val TAG = "DeezerProvider"
        }
    }
