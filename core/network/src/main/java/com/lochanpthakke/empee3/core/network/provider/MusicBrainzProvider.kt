@file:Suppress("detekt:All")

package com.lochanpthakke.empee3.core.network.provider

import com.lochanpthakke.empee3.core.common.logging.Logger
import com.lochanpthakke.empee3.core.model.MetadataCandidate
import com.lochanpthakke.empee3.core.model.MetadataProvider
import com.lochanpthakke.empee3.core.network.api.MusicBrainzApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicBrainzProvider
    @Inject
    constructor(
        private val musicBrainzApi: MusicBrainzApi,
        private val logger: Logger,
    ) : MetadataProvider {
        override val providerName: String = "MusicBrainz"

        override suspend fun searchTrack(
            title: String,
            artist: String?,
        ): List<MetadataCandidate> {
            val query =
                if (!artist.isNullOrBlank()) {
                    "recording:\"$title\" AND artist:\"$artist\""
                } else {
                    "recording:\"$title\""
                }

            return try {
                val response = musicBrainzApi.searchRecording(query)
                response.recordings.map { dto ->
                    val artistName = dto.artistCredit.firstOrNull()?.name ?: "Unknown Artist"
                    val firstRelease = dto.releases.firstOrNull()
                    val albumTitle = firstRelease?.title ?: "Unknown Album"
                    val yearStr = firstRelease?.date?.take(4)
                    val year = yearStr?.toIntOrNull() ?: 0

                    MetadataCandidate(
                        id = "mb_${dto.id}",
                        title = dto.title,
                        artist = artistName,
                        album = albumTitle,
                        year = year,
                        coverArtUrl = null,
                        sourceName = providerName,
                    )
                }
            } catch (e: Exception) {
                logger.w(TAG, "MusicBrainz search failed: ${e.message}", e)
                emptyList()
            }
        }

        override suspend fun fetchAlbumArt(
            albumTitle: String,
            artist: String?,
        ): String? = null

        companion object {
            private const val TAG = "MusicBrainzProvider"
        }
    }
