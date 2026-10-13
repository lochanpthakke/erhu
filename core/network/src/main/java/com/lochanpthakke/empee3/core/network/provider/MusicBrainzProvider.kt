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
            album: String?,
        ): List<MetadataCandidate> {
            val isConstrained = (!artist.isNullOrBlank() && !artist.equals("Unknown Artist", ignoreCase = true)) ||
                (!album.isNullOrBlank() && !album.equals("Unknown Album", ignoreCase = true))

            val query =
                when {
                    !artist.isNullOrBlank() && !artist.equals("Unknown Artist", ignoreCase = true) ->
                        "recording:\"$title\" AND artist:\"$artist\""
                    !album.isNullOrBlank() && !album.equals("Unknown Album", ignoreCase = true) ->
                        "recording:\"$title\" AND release:\"$album\""
                    else -> "recording:\"$title\""
                }

            return try {
                var response = musicBrainzApi.searchRecording(query)
                if (response.recordings.isEmpty() && isConstrained) {
                    response = musicBrainzApi.searchRecording("recording:\"$title\"")
                }
                response.recordings.map { dto ->
                    val artistName =
                        if (dto.artistCredit.isNotEmpty()) {
                            val hasJoinPhrases = dto.artistCredit.any { !it.joinPhrase.isNullOrBlank() }
                            if (hasJoinPhrases) {
                                dto.artistCredit.joinToString("") { credit ->
                                    credit.name + (credit.joinPhrase ?: "")
                                }.trim()
                            } else {
                                dto.artistCredit.joinToString(", ") { it.name }.trim()
                            }
                        } else {
                            "Unknown Artist"
                        }
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
                        durationMs = dto.length,
                        coverArtUrl = null,
                        sourceName = providerName,
                    )
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
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
