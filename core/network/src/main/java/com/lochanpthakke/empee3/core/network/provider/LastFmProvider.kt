@file:Suppress("detekt:All")

package com.lochanpthakke.empee3.core.network.provider

import com.lochanpthakke.empee3.core.common.logging.Logger
import com.lochanpthakke.empee3.core.model.MetadataCandidate
import com.lochanpthakke.empee3.core.model.MetadataProvider
import com.lochanpthakke.empee3.core.network.BuildConfig
import com.lochanpthakke.empee3.core.network.api.LastFmApi
import com.lochanpthakke.empee3.core.network.model.LastFmSearchResponse
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LastFmProvider
    @Inject
    constructor(
        private val lastFmApi: LastFmApi,
        private val credentials: LastFmCredentials,
        private val logger: Logger,
    ) : MetadataProvider {
        override val providerName: String = "Last.fm"

        /** False without an API key: then the provider is reported as not configured and never called. */
        val isConfigured: Boolean get() = credentials.isConfigured

        override suspend fun searchTrack(
            title: String,
            artist: String?,
            album: String?,
        ): List<MetadataCandidate> {
            if (!isConfigured) throw ProviderAuthException(NOT_CONFIGURED_MESSAGE)
            val response =
                try {
                    lastFmApi.searchTrack(
                        track = title,
                        artist = artist,
                        apiKey = credentials.apiKey,
                    )
                } catch (e: HttpException) {
                    // Last.fm sends API errors as HTTP 4xx with a JSON body: HTTP 403 + error 10 for a bad key.
                    throw lastFmHttpError(e)
                }
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
            const val NOT_CONFIGURED_MESSAGE = "No Last.fm API key configured (lastfm.apiKey in local.properties)"

            private const val TAG = "LastFmProvider"
        }
    }

/** Last.fm's grey star shown when it has no picture; not artwork. */
private const val LAST_FM_PLACEHOLDER_IMAGE = "2a96cbd8b46e442fc41c2b86b821562f"

/**
 * Last.fm's API key. Supplied at build time from local.properties / LASTFM_API_KEY.
 */
@Singleton
class LastFmCredentials(
    val apiKey: String,
) {
    @Inject
    constructor() : this(BuildConfig.LASTFM_API_KEY)

    val isConfigured: Boolean get() = apiKey.isNotBlank()
}

/** Last.fm error codes about the key itself (10 invalid, 26 suspended), and its rate limit (29). */
private val LAST_FM_AUTH_ERRORS = setOf(10, 26)
private const val LAST_FM_RATE_LIMIT_ERROR = 29

/** A Last.fm API error as the exception the search reports: key errors are auth failures, never "no results". */
internal fun lastFmApiError(
    code: Int,
    message: String?,
): Exception {
    val text = "Last.fm error $code: ${message ?: "request rejected"}"
    return when (code) {
        in LAST_FM_AUTH_ERRORS -> ProviderAuthException(text)
        LAST_FM_RATE_LIMIT_ERROR -> ProviderRateLimitedException(text)
        else -> ProviderUnavailableException(text)
    }
}

private val errorJson = Json { ignoreUnknownKeys = true }

/** Reads Last.fm's JSON error body from an HTTP error; the HTTP error itself when the body isn't one. */
internal fun lastFmHttpError(error: HttpException): Exception {
    val body = runCatching { error.response()?.errorBody()?.string() }.getOrNull() ?: return error
    val parsed = runCatching { errorJson.decodeFromString<LastFmSearchResponse>(body) }.getOrNull()
    val code = parsed?.error ?: return error
    return lastFmApiError(code, parsed.message)
}

/**
 * Maps a `track.search` response. Last.fm's search has no album, year, genre or BPM, so those stay
 * empty. Throws when Last.fm reports an error (see [lastFmApiError]).
 */
internal fun lastFmTrackCandidates(
    response: LastFmSearchResponse,
    providerName: String,
): List<MetadataCandidate> {
    response.error?.let { code -> throw lastFmApiError(code, response.message) }
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
