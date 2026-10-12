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

    suspend fun fetchLyrics(
        title: String,
        artist: String,
        album: String? = null,
        durationSec: Int? = null,
    ): AppResult<LyricsResult?>

    /** Writes tags through the file's content URI; requires [checkTagWriteAccess] to be Granted. */
    suspend fun writeTagsToFile(request: TagWriteRequest): AppResult<Unit>

    /** What the user must authorize before [writeTagsToFile] can modify these files. */
    fun checkTagWriteAccess(contentUris: List<String>): WriteAuthorization
}
