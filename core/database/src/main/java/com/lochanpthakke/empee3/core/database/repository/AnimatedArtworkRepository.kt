package com.lochanpthakke.empee3.core.database.repository

import com.lochanpthakke.empee3.core.common.result.AppResult
import com.lochanpthakke.empee3.core.model.AnimatedArtOrientation
import com.lochanpthakke.empee3.core.model.LocalAnimatedArtwork
import com.lochanpthakke.empee3.core.network.apple.EditorialVideoUrls
import com.lochanpthakke.empee3.core.network.apple.HlsVariantStream
import com.lochanpthakke.empee3.core.network.apple.ResolveOutcome
import kotlinx.coroutines.flow.Flow

interface AnimatedArtworkRepository {
    fun observeAnimatedArtwork(albumId: Long): Flow<LocalAnimatedArtwork?>
    suspend fun resolveAlbum(
        albumArtist: String,
        album: String,
        trackCount: Int? = null
    ): AppResult<ResolveOutcome>
    suspend fun fetchMasterUrls(
        catalogAlbumId: String,
        storefront: String
    ): AppResult<EditorialVideoUrls>
    suspend fun fetchVariantsForMasterUrl(masterUrl: String): AppResult<List<HlsVariantStream>>
    suspend fun downloadAndSaveVariant(
        albumId: Long,
        albumArtist: String,
        album: String,
        catalogAlbumId: String,
        storefront: String,
        orientation: AnimatedArtOrientation,
        variantUrl: String
    ): AppResult<LocalAnimatedArtwork>
    suspend fun fetchAndStoreArtwork(
        albumId: Long,
        albumArtist: String,
        album: String,
        trackCount: Int?,
        orientation: AnimatedArtOrientation,
        forceRefresh: Boolean = false
    ): AppResult<LocalAnimatedArtwork>
    suspend fun fetchAndStoreArtworkByUrl(
        albumId: Long,
        albumArtist: String,
        album: String,
        catalogAlbumId: String,
        storefront: String,
        orientation: AnimatedArtOrientation,
        forceRefresh: Boolean = false
    ): AppResult<LocalAnimatedArtwork>
    suspend fun deleteArtwork(albumId: Long)
    /**
     * Whether album [albumId] has a live cover to offer: saved already, downloadable, or none.
     * Answers from what is stored when it can; otherwise asks Apple (without downloading) and
     * remembers the answer. Network failures are returned, not remembered.
     */
    suspend fun checkLiveCover(
        albumId: Long,
        albumArtist: String,
        album: String,
        trackCount: Int?
    ): AppResult<LiveCoverCheck>

    /** Stops album [albumId]'s download, if one is running; nothing partial is kept. */
    fun cancelDownload(albumId: Long)
    suspend fun deleteAllArtwork()
    suspend fun totalStorageBytes(): Long
    fun observePreferredOrientation(): Flow<AnimatedArtOrientation>
    suspend fun setPreferredOrientation(orientation: AnimatedArtOrientation)
    fun observePreferredResolution(): Flow<String>
    suspend fun setPreferredResolution(resolution: String)
    suspend fun cleanupOnStartup()
}
