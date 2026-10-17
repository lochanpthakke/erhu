package com.lochanpthakke.empee3.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DiscogsSearchResponse(
    val results: List<DiscogsSearchResultDto>? = null,
)

@Serializable
data class DiscogsSearchResultDto(
    val id: Long = 0,
    val title: String = "",
    val type: String = "",
    @SerialName("cover_image")
    val coverImage: String? = null,
    @SerialName("thumb")
    val thumb: String? = null,
)

@Serializable
data class DiscogsArtistDetailDto(
    val id: Long = 0,
    val name: String = "",
    val images: List<DiscogsImageDto>? = null,
)

@Serializable
data class DiscogsImageDto(
    val uri: String? = null,
    @SerialName("uri150")
    val uri150: String? = null,
    val type: String = "",
    val width: Int? = null,
    val height: Int? = null,
)
