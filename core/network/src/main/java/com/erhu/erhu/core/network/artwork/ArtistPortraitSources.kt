package com.erhu.erhu.core.network.artwork

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query

private const val GENIUS_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/131.0.0.0 Safari/537.36"

internal interface ArtistPortraitApi {
    @Headers("User-Agent: $GENIUS_USER_AGENT", "Accept: application/json")
    @GET("https://genius.com/api/search/artist")
    suspend fun searchGenius(
        @Query("q") artist: String,
        @Query("per_page") limit: Int = 15,
    ): GeniusArtistSearch

    @Headers("User-Agent: Mozilla/5.0")
    @GET("https://duckduckgo.com/?ia=images&iax=images")
    suspend fun duckSearchPage(
        @Query("q") query: String,
    ): ResponseBody

    @Headers("User-Agent: Mozilla/5.0", "Referer: https://duckduckgo.com/")
    @GET("https://duckduckgo.com/i.js?o=json")
    suspend fun searchDuckImages(
        @Query("q") query: String,
        @Query("vqd") token: String,
    ): Response<DuckImages>
}

@Serializable
internal data class GeniusArtistSearch(
    val response: GeniusArtistResults,
)

@Serializable
internal data class GeniusArtistResults(
    val sections: List<GeniusArtistSection> = emptyList(),
    val hits: List<GeniusArtistHit> = emptyList(),
)

@Serializable
internal data class GeniusArtistSection(
    val hits: List<GeniusArtistHit> = emptyList(),
)

@Serializable
internal data class GeniusArtistHit(
    val type: String,
    val result: GeniusArtist,
)

@Serializable
internal data class GeniusArtist(
    val id: Long,
    val name: String? = null,
    @SerialName("image_url") val image: String? = null,
    @SerialName("header_image_url") val banner: String? = null,
)

@Serializable
internal data class DuckImages(
    val results: List<DuckImage>? = null,
)

@Serializable
internal data class DuckImage(
    val title: String = "",
    val image: String? = null,
    val thumbnail: String? = null,
    val url: String = "",
)

internal val DUCK_TOKEN = Regex("""\bvqd\s*(?:=|:)\s*['"]?(\d+-[\d-]+)""")
private val NON_WORD = Regex("""[^\p{L}\p{N}]+""")
private val NON_PORTRAIT =
    Regex(
        """\b(album cover|cover art|album artwork|single cover|track cover|logo|discography|merch|wallpaper|fan art|""" +
            """poster|posters|print|prints|canvas|framed|wall art|painting|illustration|etsy|redbubble|displate|fine art america)\b""",
    )
private const val MAX_DUCK_PORTRAITS = 30
private val DIFFERENT_DRAKE =
    Regex(
        """\b(drake bell|nick drake|drake white|drake milligan|drake english|drake electric|drake electronic|drake chisholm|william d drake|drakeo|drake from the band)\b""",
    )
private val DRAKE_PHOTO_CONTEXT = Regex("""\b(aubrey|rapper|rap|hip hop|ovo|photo|photograph|portrait|headshot|red carpet)\b""")

internal fun normalizedArtistName(value: String): String = value.lowercase().replace(NON_WORD, " ").trim()

internal fun String.artistImageUrl(): String? =
    trim()
        .toHttpUrlOrNull()
        ?.takeIf {
            it.isHttps &&
                it.username.isEmpty() &&
                it.password.isEmpty()
        }?.newBuilder()
        ?.fragment(null)
        ?.build()
        ?.toString()

internal class PortraitArtist(
    name: String,
) {
    val name = name.trim()
    val normalized = normalizedArtistName(name)
    val isRapperDrake = normalized in setOf("drake", "aubrey drake graham", "aubrey graham")
    val catalogueQuery = if (isRapperDrake) "Drake" else this.name
    val webQueries = listOf(catalogueQuery, "$catalogueQuery profile photo", "$catalogueQuery pfp")
    val isSearchable = normalized.isNotEmpty() && normalized != "unknown artist"

    fun matches(displayName: String?): Boolean = displayName != null && normalizedArtistName(displayName) == normalized
}

internal fun GeniusArtistSearch.portraits(artist: PortraitArtist): List<String> {
    val hits = (response.hits + response.sections.flatMap { it.hits }).filter { it.type == "artist" }.map { it.result }
    val match =
        if (artist.isRapperDrake) {
            hits.firstOrNull { it.id == 130L && it.name.equals("Drake", ignoreCase = true) }
        } else {
            hits.filter { artist.matches(it.name) }.distinctBy { it.id }.singleOrNull()
        } ?: return emptyList()
    return listOfNotNull(match.image, match.banner)
        .filterNot { url ->
            url.contains("default_avatar", ignoreCase = true) ||
                url.contains("default_cover", ignoreCase = true) ||
                url.contains("assets.genius.com/images/default", ignoreCase = true)
        }.mapNotNull { it.artistImageUrl() }
        .distinct()
}

internal data class PortraitImage(
    val url: String,
    val previewUrl: String = url,
)

internal fun DuckImages.portraits(artist: PortraitArtist): List<PortraitImage> =
    requireNotNull(results) { "DuckDuckGo returned no image results field" }
        .asSequence()
        .filter { result ->
            val text = normalizedArtistName(result.title + " " + result.url + " " + result.image.orEmpty())
            val identity = " $text "
            val matches =
                if (artist.isRapperDrake) {
                    val plainNameTitle = normalizedArtistName(result.title) == "drake"
                    " drake " in identity &&
                        (plainNameTitle || DRAKE_PHOTO_CONTEXT.containsMatchIn(text)) &&
                        !DIFFERENT_DRAKE.containsMatchIn(text)
                } else {
                    " ${artist.normalized} " in identity
                }
            matches && !NON_PORTRAIT.containsMatchIn(text)
        }.mapNotNull { result ->
            val original = result.image?.artistImageUrl() ?: return@mapNotNull null
            PortraitImage(original, result.thumbnail?.artistImageUrl() ?: original)
        }.distinctBy { it.url }
        .take(MAX_DUCK_PORTRAITS)
        .toList()
