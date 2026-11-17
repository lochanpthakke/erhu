package com.erhu.erhu.core.network.apple.model

import kotlinx.serialization.Serializable

@Serializable
data class AppleArtistSearchResponse(
    val results: AppleArtistSearchResults? = null,
)

@Serializable
data class AppleArtistSearchResults(
    val artists: AppleArtistResults? = null,
)

@Serializable
data class AppleArtistResults(
    val data: List<AppleArtistResource> = emptyList(),
)

@Serializable
data class AppleArtistResource(
    val id: String,
    val type: String,
    val attributes: AppleArtistAttributes? = null,
)

@Serializable
data class AppleArtistAttributes(
    val name: String,
    val artwork: AppleArtistArtwork? = null,
)

@Serializable
data class AppleArtistArtwork(
    val url: String,
    val width: Int,
    val height: Int,
)
