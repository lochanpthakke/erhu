package com.lochanpthakke.empee3.core.network.apple

import com.lochanpthakke.empee3.core.common.result.AppResult
import com.lochanpthakke.empee3.core.common.result.AppError
import java.io.File

sealed interface ResolveOutcome {
    data object NoMatch : ResolveOutcome
    data class Matched(
        val catalogAlbumId: String,
        val storefront: String,
        val squareMasterUrl: String?,
        val tallMasterUrl: String?
    ) : ResolveOutcome
}

data class EditorialVideoUrls(
    val squareMasterUrl: String?,
    val tallMasterUrl: String?
)

interface AnimatedArtworkNetworkDataSource {
    suspend fun resolveAlbum(artist: String, album: String, trackCount: Int? = null): AppResult<ResolveOutcome>
    suspend fun fetchMasterUrls(catalogAlbumId: String, storefront: String): AppResult<EditorialVideoUrls>
    suspend fun fetchVariantStreamsForMasterUrl(masterUrl: String): AppResult<List<HlsVariantStream>>
    suspend fun downloadToFile(masterUrl: String, destination: File, tmp: File): AppResult<Unit>
    suspend fun downloadVariantToFile(variantUrl: String, destination: File, tmp: File): AppResult<Unit>
}

class NoOpAnimatedArtworkNetworkDataSource : AnimatedArtworkNetworkDataSource {
    override suspend fun resolveAlbum(artist: String, album: String, trackCount: Int?): AppResult<ResolveOutcome> =
        AppResult.Error(AppError.IoError("Animated artwork fetch is disabled by BuildConfig"))

    override suspend fun fetchMasterUrls(catalogAlbumId: String, storefront: String): AppResult<EditorialVideoUrls> =
        AppResult.Error(AppError.IoError("Animated artwork fetch is disabled by BuildConfig"))

    override suspend fun fetchVariantStreamsForMasterUrl(masterUrl: String): AppResult<List<HlsVariantStream>> =
        AppResult.Error(AppError.IoError("Animated artwork fetch is disabled by BuildConfig"))

    override suspend fun downloadToFile(masterUrl: String, destination: File, tmp: File): AppResult<Unit> =
        AppResult.Error(AppError.IoError("Animated artwork fetch is disabled by BuildConfig"))

    override suspend fun downloadVariantToFile(variantUrl: String, destination: File, tmp: File): AppResult<Unit> =
        AppResult.Error(AppError.IoError("Animated artwork fetch is disabled by BuildConfig"))
}
