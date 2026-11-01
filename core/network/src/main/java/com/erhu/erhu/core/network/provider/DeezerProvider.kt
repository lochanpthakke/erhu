@file:Suppress("detekt:All")

package com.erhu.erhu.core.network.provider

import com.erhu.erhu.core.common.logging.Logger
import com.erhu.erhu.core.model.MetadataCandidate
import com.erhu.erhu.core.model.MetadataProvider
import com.erhu.erhu.core.network.api.DeezerApi
import com.erhu.erhu.core.network.model.DeezerSearchResponse
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
            val query = deezerTrackQuery(title, artist, album)

            // Failures propagate: the repository reports them as FAILED, not as "no results".
            return deezerTrackCandidates(deezerApi.searchTrack(query), providerName)
        }

        /**
         * Genres of Deezer album [albumId] (Deezer has no per-track genre). Empty when the album
         * lists none; throws when the lookup itself fails.
         */
        suspend fun fetchAlbumGenres(albumId: Long): List<String> {
            val album = deezerApi.getAlbum(albumId)
            album.error?.let { throw ProviderUnavailableException("Deezer: ${it.message ?: "album lookup failed"}") }
            return album.genres
                ?.data
                .orEmpty()
                .map { it.name.trim() }
                .filter { it.isNotEmpty() }
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

        suspend fun fetchArtistPortraitList(artistName: String): List<String> {
            val trimmed = artistName.trim()
            if (trimmed.isBlank() || trimmed.equals("Unknown Artist", ignoreCase = true)) return emptyList()
            return try {
                val response = deezerApi.searchArtist(trimmed)
                val candidates = response.data.orEmpty()
                // 1. Filter to exact case-insensitive normalized name equality
                val exactMatches = candidates.filter { it.name.trim().equals(trimmed, ignoreCase = true) }
                // 2. Sort by fan count descending
                val sorted = (if (exactMatches.isNotEmpty()) exactMatches else candidates).sortedByDescending { it.nbFan }
                // 3. Extract pictureXl / pictureBig, requiring a valid 32-hex character hash segment
                sorted
                    .mapNotNull { it.pictureXl ?: it.pictureBig ?: it.pictureMedium }
                    .filter { url -> url.isNotBlank() && DEEZER_ARTIST_HASH_REGEX.containsMatchIn(url) }
                    .distinct()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.w(TAG, "Deezer artist portrait list search failed for $artistName: ${e.message}", e)
                emptyList()
            }
        }

        companion object {
            private const val TAG = "DeezerProvider"
            private val DEEZER_ARTIST_HASH_REGEX = Regex("""/images/artist/([a-fA-F0-9]{32})/""")
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

/**
 * Maps a track search response. Throws [ProviderUnavailableException] when Deezer answered with an
 * error, or counted matches but returned none (results withheld in this country), so neither is
 * reported as "no such track". Unknown names are left blank rather than set to a placeholder.
 */
internal fun deezerTrackCandidates(
    response: DeezerSearchResponse,
    providerName: String,
): List<MetadataCandidate> {
    response.error?.let { throw ProviderUnavailableException("Deezer: ${it.message ?: "search failed"}") }
    if (response.data.isEmpty() && response.total > 0) {
        throw ProviderUnavailableException(
            "Deezer found ${response.total} matches but returned none: track search isn't available in this country",
            sessionWide = true,
        )
    }
    return response.data.map { dto ->
        MetadataCandidate(
            id = "deezer_${dto.id}",
            title = dto.title,
            artist = dto.artist?.name.orEmpty(),
            album = dto.album?.title.orEmpty(),
            // Search results carry no BPM; track lookups report 0 for most tracks. Only a real value is kept.
            bpm = dto.bpm?.toInt()?.takeIf { it > 0 },
            durationMs = dto.duration?.times(1000L),
            coverArtUrl = dto.album?.coverXl ?: dto.album?.coverBig ?: dto.album?.coverMedium,
            sourceName = providerName,
            deezerAlbumId = dto.album?.id,
            versionNote = dto.titleVersion?.trim()?.takeIf { it.isNotEmpty() && !dto.title.contains(it, ignoreCase = true) },
        )
    }
}

/**
 * Deezer's advanced search for a track. A credit flattened with "/" ("Tyler/ The Creator/Frank
 * Ocean/ Steve Lacy") is no artist name Deezer knows, so then the album narrows the search instead.
 * (Not verifiable from India, where Deezer withholds track search results.)
 */
internal fun deezerTrackQuery(
    title: String,
    artist: String?,
    album: String?,
): String {
    fun phrase(text: String) = "\"${text.trim().replace("\"", "")}\""
    val usableArtist = artist?.takeUnless { it.isBlank() || it.equals("Unknown Artist", ignoreCase = true) || '/' in it }
    val usableAlbum = album?.takeUnless { it.isBlank() || it.equals("Unknown Album", ignoreCase = true) }
    return when {
        usableArtist != null -> "artist:${phrase(usableArtist)} track:${phrase(title)}"
        usableAlbum != null -> "album:${phrase(usableAlbum)} track:${phrase(title)}"
        else -> title.trim()
    }
}
