@file:Suppress("detekt:All")

package com.lochanpthakke.empee3.core.network.provider

import com.lochanpthakke.empee3.core.common.logging.Logger
import com.lochanpthakke.empee3.core.model.MetadataCandidate
import com.lochanpthakke.empee3.core.model.MetadataProvider
import com.lochanpthakke.empee3.core.network.api.LastFmApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LastFmProvider
    @Inject
    constructor(
        private val lastFmApi: LastFmApi,
        private val logger: Logger,
    ) : MetadataProvider {
        override val providerName: String = "Last.fm"

        override suspend fun searchTrack(
            title: String,
            artist: String?,
            album: String?,
        ): List<MetadataCandidate> =
            try {
                val response =
                    lastFmApi.searchTrack(
                        track = title,
                        artist = artist,
                        apiKey = LAST_FM_API_KEY,
                    )
                val tracks = response.results?.trackMatches?.track ?: emptyList()
                tracks.map { dto ->
                    val coverUrl = dto.image.find { it.size == "extralarge" || it.size == "large" }?.url
                    MetadataCandidate(
                        id = "lastfm_${dto.name}_${dto.artist}",
                        title = dto.name,
                        artist = dto.artist,
                        album = "Unknown Album",
                        coverArtUrl = coverUrl?.takeIf { it.isNotBlank() },
                        sourceName = providerName,
                    )
                }
            } catch (e: Exception) {
                logger.w(TAG, "Last.fm search failed: ${e.message}", e)
                emptyList()
            }

        override suspend fun fetchAlbumArt(
            albumTitle: String,
            artist: String?,
        ): String? {
            val candidates = searchTrack(albumTitle, artist)
            return candidates.firstOrNull { !it.coverArtUrl.isNullOrBlank() }?.coverArtUrl
        }

        companion object {
            private const val TAG = "LastFmProvider"
            private const val LAST_FM_API_KEY = "0d20d754f42df49ba8adca53531b7829"
        }
    }
