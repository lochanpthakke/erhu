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
    suspend fun deleteAllArtwork()
    suspend fun totalStorageBytes(): Long
    fun observePreferredOrientation(): Flow<AnimatedArtOrientation>
    suspend fun setPreferredOrientation(orientation: AnimatedArtOrientation)
    suspend fun cleanupOnStartup()
}
