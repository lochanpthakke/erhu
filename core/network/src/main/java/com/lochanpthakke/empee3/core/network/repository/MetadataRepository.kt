@file:Suppress("detekt:All")

package com.lochanpthakke.empee3.core.network.repository

import com.lochanpthakke.empee3.core.common.result.AppResult
import com.lochanpthakke.empee3.core.model.LyricsResult
import com.lochanpthakke.empee3.core.model.MetadataCandidate
import com.lochanpthakke.empee3.core.model.ProviderOutcome
import com.lochanpthakke.empee3.core.network.cleaner.CleanedFilename
import com.lochanpthakke.empee3.core.network.tagger.TagWriteRequest
import com.lochanpthakke.empee3.core.network.tagger.WriteAuthorization

data class DetailedSearchResponse(
    val candidates: List<MetadataCandidate>,
    val providerOutcomes: List<ProviderOutcome>,
)

data class ArtistArtworkSearchResponse(
    val candidates: List<String>,
    val providerCandidates: Map<String, List<String>> = emptyMap(),
    val resolvedMbid: String? = null,
)

/** Outcome of a genre lookup for a matched track. */
sealed interface GenreLookup {
    data class Found(
        val genre: String,
        val source: String,
    ) : GenreLookup

    /** The lookup worked but gave no single genre; [reason] says why. */
    data class NotFound(
        val reason: String,
    ) : GenreLookup

    data class Failed(
        val reason: String,
    ) : GenreLookup
}

/** Provider names as stored in settings ("ALL", "Deezer", "MusicBrainz", …). */
object MetadataSources {
    const val ALL = "ALL"
    const val GENIUS = "Genius"
    const val DUCKDUCKGO = "DuckDuckGo"
    const val FANART_TV = "FanartTv"
    const val DISCOGS = "Discogs"
    const val WIKIPEDIA = "Wikipedia"
    const val ITUNES = "iTunes"
    const val MUSICBRAINZ = "MusicBrainz"
    const val DEEZER = "Deezer"
    const val LAST_FM = "LastFm"

    /** Whether provider [providerName] is selected by [selection]; "Last.fm" and "LastFm" are the same. */
    fun matches(
        providerName: String,
        selection: String,
    ): Boolean {
        if (selection.equals(ALL, ignoreCase = true) ||
            selection.equals("All Sources", ignoreCase = true) ||
            selection.equals("All Providers", ignoreCase = true) ||
            selection.equals("All providers", ignoreCase = true)
        ) {
            return true
        }
        val pKey = canonicalKey(providerName)
        val sKey = canonicalKey(selection)
        return pKey == sKey || pKey.contains(sKey) || sKey.contains(pKey)
    }

    private fun canonicalKey(name: String): String {
        val raw = name.lowercase().filter { it.isLetterOrDigit() }
        return when {
            raw.startsWith("genius") -> "genius"
            raw.startsWith("duckduckgo") || raw.startsWith("duck") -> "duckduckgo"
            raw.startsWith("fanart") -> "fanarttv"
            raw.startsWith("wikipedia") || raw.startsWith("wikimedia") -> "wikipedia"
            raw.startsWith("discogs") -> "discogs"
            raw.startsWith("deezer") -> "deezer"
            raw.startsWith("itunes") || raw.startsWith("applemusic") -> "itunes"
            raw.startsWith("musicbrainz") -> "musicbrainz"
            raw.startsWith("lastfm") -> "lastfm"
            else -> raw
        }
    }

    private val HOSTS =
        listOf(
            GENIUS to "genius.com",
            DUCKDUCKGO to "duckduckgo.com",
            FANART_TV to "webservice.fanart.tv",
            DISCOGS to "api.discogs.com",
            WIKIPEDIA to "en.wikipedia.org",
            ITUNES to "itunes.apple.com",
            MUSICBRAINZ to "musicbrainz.org",
            DEEZER to "api.deezer.com",
            LAST_FM to "ws.audioscrobbler.com",
        )

    /** The hosts [selection] will contact (for a DNS-only preflight); Last.fm only when it has a key. */
    fun hostsFor(
        selection: String,
        lastFmConfigured: Boolean,
    ): List<String> =
        HOSTS
            .filter { (provider, _) -> matches(provider, selection) && (provider != LAST_FM || lastFmConfigured) }
            .map { it.second }
}

interface MetadataRepository {
    fun cleanFilename(
        rawFilename: String,
        customBlocklist: List<String> = emptyList(),
    ): CleanedFilename

    suspend fun searchTrack(
        title: String,
        artist: String?,
        album: String? = null,
        sourceProvider: String = "ALL",
    ): AppResult<List<MetadataCandidate>>

    suspend fun searchAndScoreTrack(
        title: String,
        artist: String?,
        album: String? = null,
        durationMs: Long? = null,
        sourceProvider: String = "ALL",
    ): AppResult<List<MetadataCandidate>>

    suspend fun searchAndScoreTrackDetailed(
        title: String,
        artist: String?,
        album: String? = null,
        durationMs: Long? = null,
        sourceProvider: String = "ALL",
    ): AppResult<DetailedSearchResponse>

    suspend fun fetchAlbumArt(
        albumTitle: String,
        artist: String?,
        sourceProvider: String = "ALL",
    ): AppResult<String?>

    suspend fun fetchAlbumArtCandidates(
        albumTitle: String,
        artist: String?,
        sourceProvider: String = "ALL",
    ): AppResult<List<String>>

    suspend fun fetchArtistArtworkCandidates(
        artistName: String,
        sourceProvider: String = "ALL",
    ): AppResult<List<String>>

    suspend fun fetchArtistArtworkCandidatesDetailed(
        artistName: String,
        sourceProvider: String = "ALL",
    ): AppResult<ArtistArtworkSearchResponse>

    suspend fun fetchLyrics(
        title: String,
        artist: String,
        album: String? = null,
        durationSec: Int? = null,
    ): AppResult<LyricsResult?>

    /**
     * The genre of a confident match: MusicBrainz's top-voted curated genre of the release group,
     * or the first genre of the Deezer album. Album-level, never invented.
     */
    suspend fun lookupGenre(match: MetadataCandidate): GenreLookup

    /** Downloads an image for embedding (artwork); fails rather than returning a non-image. */
    suspend fun downloadArtwork(url: String): AppResult<ByteArray>

    /** Writes tags through the file's content URI; requires [checkTagWriteAccess] to be Granted. */
    suspend fun writeTagsToFile(request: TagWriteRequest): AppResult<Unit>

    /** What the user must authorize before [writeTagsToFile] can modify these files. */
    fun checkTagWriteAccess(contentUris: List<String>): WriteAuthorization
}
