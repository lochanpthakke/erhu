package com.lochanpthakke.empee3.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GeniusSearchResponse(
    val response: GeniusResponseBodyDto? = null,
)

@Serializable
data class GeniusResponseBodyDto(
    val hits: List<GeniusHitDto>? = null,
    val sections: List<GeniusSectionDto>? = null,
)

@Serializable
data class GeniusSectionDto(
    val hits: List<GeniusHitDto>? = null,
)

@Serializable
data class GeniusHitDto(
    val result: GeniusHitResultDto? = null,
    val artist: GeniusArtistDto? = null,
)

@Serializable
data class GeniusHitResultDto(
    val id: Long = 0,
    val name: String = "",
    val title: String = "",
    @SerialName("image_url")
    val imageUrl: String? = null,
    @SerialName("header_image_url")
    val headerImageUrl: String? = null,
    @SerialName("primary_artist")
    val primaryArtist: GeniusArtistDto? = null,
)

@Serializable
data class GeniusArtistDto(
    val id: Long = 0,
    val name: String = "",
    @SerialName("image_url")
    val imageUrl: String? = null,
    @SerialName("header_image_url")
    val headerImageUrl: String? = null,
)
