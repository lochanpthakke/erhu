@file:Suppress("detekt:All")

package com.lochanpthakke.empee3.core.network.provider

import com.lochanpthakke.empee3.core.common.logging.Logger
import com.lochanpthakke.empee3.core.model.MetadataCandidate
import com.lochanpthakke.empee3.core.model.MetadataProvider
import com.lochanpthakke.empee3.core.network.api.LastFmApi
import com.lochanpthakke.empee3.core.network.model.LastFmSearchResponse
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
        ): List<MetadataCandidate> {
            val response =
                lastFmApi.searchTrack(
                    track = title,
                    artist = artist,
                    apiKey = LAST_FM_API_KEY,
                )
            return lastFmTrackCandidates(response, providerName)
        }

        override suspend fun fetchAlbumArt(
            albumTitle: String,
            artist: String?,
        ): String? =
            try {
                searchTrack(albumTitle, artist).firstOrNull { !it.coverArtUrl.isNullOrBlank() }?.coverArtUrl
            } catch (e: Exception) {
                logger.w(TAG, "Last.fm album art lookup failed: ${e.message}", e)
                null
            }

        companion object {
            /**
             * Last.fm answers every request with this key with error 10 "Invalid API key" (checked
             * 2026-10-13), so the provider is not queried until a valid key is configured.
             */
            const val IS_CONFIGURED = false

            private const val TAG = "LastFmProvider"
            private const val LAST_FM_API_KEY = "0d20d754f42df49ba8adca53531b7829"
        }
    }

/** Last.fm's grey star shown when it has no picture; not artwork. */
private const val LAST_FM_PLACEHOLDER_IMAGE = "2a96cbd8b46e442fc41c2b86b821562f"

/**
 * Maps a `track.search` response. Last.fm's search has no album, year, genre or BPM, so those stay
 * empty. Throws [ProviderUnavailableException] when Last.fm reports an error (e.g. a rejected key).
 */
internal fun lastFmTrackCandidates(
    response: LastFmSearchResponse,
    providerName: String,
): List<MetadataCandidate> {
    response.error?.let { code -> throw ProviderUnavailableException("Last.fm error $code: ${response.message ?: "request rejected"}") }
    return response.results?.trackMatches?.track.orEmpty().map { dto ->
        val coverUrl =
            dto.image
                .find { it.size == "extralarge" || it.size == "large" }
                ?.url
                ?.takeIf { it.isNotBlank() && !it.contains(LAST_FM_PLACEHOLDER_IMAGE) }
        MetadataCandidate(
            id = "lastfm_${dto.name}_${dto.artist}",
            title = dto.name,
            artist = dto.artist,
            album = "",
            coverArtUrl = coverUrl,
            sourceName = providerName,
        )
    }
}
