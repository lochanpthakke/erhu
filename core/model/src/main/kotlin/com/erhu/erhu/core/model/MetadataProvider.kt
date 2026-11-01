package com.erhu.erhu.core.model

interface MetadataProvider {
    val providerName: String

    suspend fun searchTrack(
        title: String,
        artist: String?,
        album: String? = null,
    ): List<MetadataCandidate>

    suspend fun fetchAlbumArt(
        albumTitle: String,
        artist: String?,
    ): String?
}
