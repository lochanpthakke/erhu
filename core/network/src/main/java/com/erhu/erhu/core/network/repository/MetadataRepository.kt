@file:Suppress("detekt:All")

package com.erhu.erhu.core.network.repository

import com.erhu.erhu.core.common.result.AppResult
import com.erhu.erhu.core.model.Genres
import com.erhu.erhu.core.model.LyricsResult
import com.erhu.erhu.core.model.MetadataCandidate
import com.erhu.erhu.core.model.ProviderOutcome
import com.erhu.erhu.core.network.cleaner.CleanedFilename
import com.erhu.erhu.core.network.tagger.TagWriteRequest
import com.erhu.erhu.core.network.tagger.WriteAuthorization

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
    /** Every usable genre the source lists, most-voted first where it ranks them. */
    data class Found(
        val genres: List<String>,
        val source: String,
    ) : GenreLookup {
        /** [genres] in the stored form ("Electronic; Synth-pop"), see [Genres]. */
        val genre: String get() = genres.joinToString(Genres.SEPARATOR)
    }

    /** The lookup worked but gave no genre; [reason] says why. */
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
    const val ITUNES = "iTunes"
    const val MUSICBRAINZ = "MusicBrainz"
    const val DEEZER = "Deezer"

    /** Unsupported legacy selections use the remaining providers instead of producing an empty lookup. */
    fun normalizeSelection(selection: String?): String =
        HOSTS.firstOrNull { canonicalKey(it.first) == canonicalKey(selection.orEmpty()) }?.first ?: ALL

    /** Whether keyless provider [providerName] is selected by a supported or legacy saved selection. */
    fun matches(
        providerName: String,
        selection: String,
    ): Boolean {
        val normalized = normalizeSelection(selection)
        if (normalized == ALL) {
            return true
        }
        val pKey = canonicalKey(providerName)
        return pKey == canonicalKey(normalized)
    }

    private fun canonicalKey(name: String): String {
        val raw = name.lowercase().filter { it.isLetterOrDigit() }
        return when {
            raw.startsWith("deezer") -> "deezer"
            raw.startsWith("itunes") || raw.startsWith("applemusic") -> "itunes"
            raw.startsWith("musicbrainz") -> "musicbrainz"
            else -> raw
        }
    }

    private val HOSTS =
        listOf(
            ITUNES to "itunes.apple.com",
            MUSICBRAINZ to "musicbrainz.org",
            DEEZER to "api.deezer.com",
        )

    /** Keyless hosts contacted by this selection, for the DNS-only preflight. */
    fun hostsFor(selection: String): List<String> =
        HOSTS
            .filter { (provider, _) -> matches(provider, selection) }
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

    suspend fun searchAlbumArtwork(
        query: com.erhu.erhu.core.network.artwork.AlbumArtworkQuery,
    ): AppResult<com.erhu.erhu.core.network.artwork.AlbumArtworkResult>

    fun artworkPreviewUrl(url: String): String = url

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
