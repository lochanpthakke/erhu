package com.erhu.erhu.core.network.apple.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("com.lochanpthakke.empee3.core.network.apple.model.ItunesSearchResponse")
data class ItunesSearchResponse(
    val results: List<ItunesAlbumDto> = emptyList(),
)

@Serializable
@SerialName("com.lochanpthakke.empee3.core.network.apple.model.ItunesAlbumDto")
data class ItunesAlbumDto(
    val collectionId: Long? = null,
    val artistId: Long? = null,
    val artistName: String? = null,
    val collectionName: String? = null,
    val trackCount: Int? = null,
    val country: String? = null,
    /** "explicit", "cleaned" or "notExplicit". */
    val collectionExplicitness: String? = null,
)
