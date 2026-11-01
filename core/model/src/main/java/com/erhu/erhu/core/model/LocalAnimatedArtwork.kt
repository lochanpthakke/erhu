package com.erhu.erhu.core.model

data class LocalAnimatedArtwork(
    val albumId: Long,
    val squareFilePath: String?,
    val tallFilePath: String?,
    val hasSquare: Boolean?,
    val hasTall: Boolean?,
    val status: AnimatedArtStatus,
    val catalogAlbumId: String?,
    val storefront: String?,
    val updatedAtEpochMs: Long,
)
