@file:Suppress("detekt:All")

package com.lochanpthakke.empee3.core.network.provider

import com.lochanpthakke.empee3.core.common.logging.Logger
import com.lochanpthakke.empee3.core.model.MetadataCandidate
import com.lochanpthakke.empee3.core.model.MetadataProvider
import com.lochanpthakke.empee3.core.network.api.DeezerApi
import com.lochanpthakke.empee3.core.network.model.DeezerArtistDto
import java.util.Locale
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

        /**
         * Profile artwork for exactly this artist. [ArtistArtworkLookup.Failed] (network error,
         * Deezer error such as the request quota) is kept apart from [ArtistArtworkLookup.NotFound]
         * so a temporary failure is never remembered as "this artist has no photo".
         */
        @Suppress("TooGenericExceptionCaught")
        suspend fun lookupArtistArtwork(artistName: String): ArtistArtworkLookup {
            val trimmed = artistName.trim()
            if (trimmed.isBlank() || trimmed.equals("Unknown Artist", ignoreCase = true)) return ArtistArtworkLookup.NotFound
            return try {
                // Plain name, not artist:"…": Deezer ranks the field query so that the exact
                // artist is often missing (artist:"Drake" returns "William D Drake", "Drake Lamar", …).
                val response = deezerApi.searchArtist(trimmed)
                val match = pickExactArtist(trimmed, response.data)
                val url = match?.pictureBig ?: match?.pictureXl ?: match?.pictureMedium
                when {
                    response.error != null -> {
                        logger.w(TAG, "Deezer artist search failed for $artistName: ${response.error.message}")
                        ArtistArtworkLookup.Failed
                    }
                    url != null -> ArtistArtworkLookup.Found(url)
                    else -> ArtistArtworkLookup.NotFound
                }
            } catch (e: Exception) {
                logger.w(TAG, "Deezer artist art fetch failed for $artistName: ${e.message}", e)
                ArtistArtworkLookup.Failed
            }
        }

        companion object {
            private const val TAG = "DeezerProvider"
        }
    }

/** The candidate whose name equals [query] (case-insensitive, trimmed); never a partial or first-result fallback. */
internal fun pickExactArtist(
    query: String,
    candidates: List<DeezerArtistDto>,
): DeezerArtistDto? {
    val key = query.trim().lowercase(Locale.ROOT)
    return candidates.firstOrNull { it.name.trim().lowercase(Locale.ROOT) == key }
}

/** Outcome of an artist artwork lookup. */
sealed interface ArtistArtworkLookup {
    data class Found(
        val url: String,
    ) : ArtistArtworkLookup

    /** The search worked and Deezer has no artist with exactly this name (or no picture for it). */
    data object NotFound : ArtistArtworkLookup

    /** The search itself failed; try again later. */
    data object Failed : ArtistArtworkLookup
}
