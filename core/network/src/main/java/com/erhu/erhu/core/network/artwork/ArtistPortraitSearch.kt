package com.erhu.erhu.core.network.artwork

import com.erhu.erhu.core.common.result.AppResult
import com.erhu.erhu.core.model.ProviderOutcome
import com.erhu.erhu.core.model.ProviderStatus
import com.erhu.erhu.core.network.apple.AppleMusicCatalogApi
import com.erhu.erhu.core.network.apple.AppleMusicTokenProvider
import com.erhu.erhu.core.network.apple.AppleRequestGate
import com.erhu.erhu.core.network.apple.RateLimitException
import com.erhu.erhu.core.network.apple.model.AppleArtistSearchResponse
import com.erhu.erhu.core.network.provider.DeezerProvider
import com.erhu.erhu.core.network.provider.ProviderUnavailableException
import com.erhu.erhu.core.network.repository.ArtistArtworkSearchResponse
import com.erhu.erhu.core.network.retry.classifyProviderFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Inject
import javax.inject.Singleton

private const val PORTRAIT_TIMEOUT_MS = 15_000L
private const val APPLE_IMAGE_TARGET = 1200
private const val HTTP_CHALLENGE = 202

@Singleton
class ArtistPortraitSearch
    @Inject
    constructor(
        client: OkHttpClient,
        json: Json,
        private val deezer: DeezerProvider,
        private val apple: AppleMusicCatalogApi,
        private val appleToken: AppleMusicTokenProvider,
        private val appleGate: AppleRequestGate,
    ) {
        private val web =
            Retrofit
                .Builder()
                .baseUrl("https://genius.com/")
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(ArtistPortraitApi::class.java)

        suspend fun search(name: String): ArtistArtworkSearchResponse =
            coroutineScope {
                val artist = PortraitArtist(name)
                val providers =
                    listOf(
                        "iTunes / Apple Music" to suspend { searchApple(artist).map { PortraitImage(it) } },
                        "Genius" to suspend { searchGenius(artist).map { PortraitImage(it) } },
                        "DuckDuckGo Image Search" to suspend { searchDuck(artist) },
                        "Deezer" to suspend { deezer.searchArtistPortraits(artist.catalogueQuery).map { PortraitImage(it) } },
                    )
                val results =
                    providers
                        .map { (provider, lookup) ->
                            async {
                                if (artist.isSearchable) {
                                    lookupPortraits(
                                        provider,
                                        lookup,
                                    )
                                } else {
                                    PortraitResult(ProviderOutcome(provider), emptyList())
                                }
                            }
                        }.awaitAll()
                val images = results.flatMap { it.images }.distinctBy { it.url }
                ArtistArtworkSearchResponse(
                    candidates = images.map { it.url },
                    providerCandidates = results.associate { it.outcome.providerName to it.images.map { image -> image.url } },
                    providerOutcomes = results.map { it.outcome },
                    previewUrls = images.filter { it.previewUrl != it.url }.associate { it.url to it.previewUrl },
                )
            }

        private suspend fun searchGenius(artist: PortraitArtist): List<String> =
            try {
                web.searchGenius(artist.catalogueQuery).portraits(artist)
            } catch (error: HttpException) {
                if (error.code() != 401 && error.code() != 403) throw error
                throw ProviderUnavailableException("Genius public artist search refused the request", sessionWide = true)
            }

        private suspend fun searchDuck(artist: PortraitArtist): List<PortraitImage> {
            for (query in artist.webQueries) {
                val portraits = searchDuckQuery(query).portraits(artist)
                if (portraits.isNotEmpty()) return portraits
            }
            return emptyList()
        }

        private suspend fun searchDuckQuery(query: String): DuckImages {
            val page = web.duckSearchPage(query).use { body -> withContext(Dispatchers.IO) { body.string() } }
            val token =
                DUCK_TOKEN.find(page)?.groupValues?.get(1)
                    ?: throw ProviderUnavailableException("DuckDuckGo did not offer image search", sessionWide = true)
            val response = web.searchDuckImages(query, token)
            if (!response.isSuccessful) throw HttpException(response)
            val images = response.body()
            if (response.code() == HTTP_CHALLENGE) {
                throw ProviderUnavailableException("DuckDuckGo requires a browser challenge", sessionWide = true)
            }
            return checkNotNull(images) { "DuckDuckGo returned an empty response" }
        }

        private suspend fun searchApple(artist: PortraitArtist): List<String> {
            val token =
                when (val result = appleToken.getToken()) {
                    is AppResult.Success -> result.data
                    is AppResult.Error ->
                        throw ProviderUnavailableException("Apple Music catalog access is unavailable", sessionWide = true)
                }
            val response = appleGate.runAppleMusic { apple.searchArtists("us", artist.catalogueQuery, "Bearer $token") }
            return response.portraits(artist)
        }
    }

internal fun AppleArtistSearchResponse.portraits(artist: PortraitArtist): List<String> {
    val resources =
        results
            ?.artists
            ?.data
            .orEmpty()
            .filter { it.type == "artists" }
    val match =
        if (artist.isRapperDrake) {
            resources.firstOrNull { it.id == "271256" && it.attributes?.name.equals("Drake", ignoreCase = true) }
        } else {
            resources.filter { artist.matches(it.attributes?.name) }.distinctBy { it.id }.singleOrNull()
        }
    val artwork = match?.attributes?.artwork ?: return emptyList()
    if (artwork.width <= 0 || artwork.height <= 0) return emptyList()
    val url =
        artwork.url
            .replace("{w}", minOf(artwork.width, APPLE_IMAGE_TARGET).toString())
            .replace("{h}", minOf(artwork.height, APPLE_IMAGE_TARGET).toString())
            .replace("{f}", "jpg")
    return listOfNotNull(url.artistImageUrl())
}

private data class PortraitResult(
    val outcome: ProviderOutcome,
    val images: List<PortraitImage>,
)

private suspend fun lookupPortraits(
    name: String,
    lookup: suspend () -> List<PortraitImage>,
): PortraitResult =
    try {
        val images =
            withTimeoutOrNull(PORTRAIT_TIMEOUT_MS) {
                lookup()
                    .mapNotNull { image ->
                        image.url.artistImageUrl()?.let { original ->
                            PortraitImage(original, image.previewUrl.artistImageUrl() ?: original)
                        }
                    }.distinctBy { it.url }
            }
        when {
            images == null -> PortraitResult(ProviderOutcome(name, status = ProviderStatus.TIMED_OUT), emptyList())
            images.isEmpty() -> PortraitResult(ProviderOutcome(name), emptyList())
            else -> PortraitResult(ProviderOutcome(name, images.size, ProviderStatus.CANDIDATES_FOUND), images)
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (limited: RateLimitException) {
        PortraitResult(ProviderOutcome(name, status = ProviderStatus.RATE_LIMITED), emptyList())
    } catch (error: Exception) {
        val failure = classifyProviderFailure(error)
        PortraitResult(ProviderOutcome(name, status = failure.status), emptyList())
    }
