@file:Suppress("detekt:All")

package com.lochanpthakke.empee3.core.network.provider

import com.lochanpthakke.empee3.core.common.logging.Logger
import com.lochanpthakke.empee3.core.model.MetadataCandidate
import com.lochanpthakke.empee3.core.model.MetadataProvider
import com.lochanpthakke.empee3.core.network.api.DeezerApi
import kotlinx.coroutines.CancellationException
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
            album: String?,
        ): List<MetadataCandidate> {
            val isConstrained = (!artist.isNullOrBlank() && !artist.equals("Unknown Artist", ignoreCase = true)) ||
                (!album.isNullOrBlank() && !album.equals("Unknown Album", ignoreCase = true))

            val query =
                when {
                    !artist.isNullOrBlank() && !artist.equals("Unknown Artist", ignoreCase = true) ->
                        "artist:\"$artist\" track:\"$title\""
                    !album.isNullOrBlank() && !album.equals("Unknown Album", ignoreCase = true) ->
                        "album:\"$album\" track:\"$title\""
                    else -> title
                }

            return try {
                var response = deezerApi.searchTrack(query)
                if (response.data.isEmpty() && isConstrained) {
                    response = deezerApi.searchTrack(title)
                }
                response.data.map { dto ->
                    MetadataCandidate(
                        id = "deezer_${dto.id}",
                        title = dto.title,
                        artist = dto.artist?.name ?: "Unknown Artist",
                        album = dto.album?.title ?: "Unknown Album",
                        bpm = dto.bpm?.toInt()?.takeIf { it > 0 },
                        durationMs = dto.duration?.times(1000L),
                        coverArtUrl = dto.album?.coverXl ?: dto.album?.coverBig ?: dto.album?.coverMedium,
                        sourceName = providerName,
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
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

        /**
         * Profile artwork for exactly this artist, chosen by [selectDeezerArtist]. [ArtistArtworkLookup.Failed]
         * (network error, Deezer error such as the request quota, unreadable response) is kept apart from
         * [ArtistArtworkLookup.NotFound] so a temporary failure is never remembered as "this artist has no photo".
         */
        @Suppress("TooGenericExceptionCaught")
        suspend fun lookupArtistArtwork(artistName: String): ArtistArtworkLookup {
            val trimmed = artistName.trim()
            if (trimmed.isBlank() || trimmed.equals("Unknown Artist", ignoreCase = true)) return ArtistArtworkLookup.NotFound
            val response =
                try {
                    // Plain name, not artist:"…": Deezer ranks the field query so that the exact
                    // artist is often missing (artist:"Drake" returns "William D Drake", "Drake Lamar", …).
                    deezerApi.searchArtist(trimmed)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.w(TAG, "Deezer artist search failed for $artistName: ${e.message}", e)
                    return ArtistArtworkLookup.Failed
                }
            val candidates = response.data
            if (response.error != null || candidates == null) {
                logger.w(TAG, "Deezer artist search failed for $artistName: ${response.error?.message ?: "no results field"}")
                return ArtistArtworkLookup.Failed
            }
            return when (val match = selectDeezerArtist(trimmed, candidates)) {
                is DeezerArtistMatch.Selected -> ArtistArtworkLookup.Found(match.pictureUrl, match.artistId)
                is DeezerArtistMatch.Rejected -> {
                    logger.i(TAG, "No Deezer artwork for $artistName: ${match.reason}")
                    ArtistArtworkLookup.NotFound
                }
            }
        }

        companion object {
            private const val TAG = "DeezerProvider"
        }
    }

/** Outcome of an artist artwork lookup. */
sealed interface ArtistArtworkLookup {
    /** [url] is the profile photo of Deezer artist [deezerArtistId]. */
    data class Found(
        val url: String,
        val deezerArtistId: Long,
    ) : ArtistArtworkLookup

    /** The search worked and no profile is safely this artist with a real photo. */
    data object NotFound : ArtistArtworkLookup

    /** The search itself failed; try again later. */
    data object Failed : ArtistArtworkLookup
}
