package com.lochanpthakke.empee3.core.network.apple.model

import kotlinx.serialization.Serializable

@Serializable
data class ItunesSearchResponse(
    val results: List<ItunesAlbumDto> = emptyList()
)

@Serializable
data class ItunesAlbumDto(
    val collectionId: Long? = null,
    val artistName: String? = null,
    val collectionName: String? = null,
    val trackCount: Int? = null,
    val country: String? = null
)
