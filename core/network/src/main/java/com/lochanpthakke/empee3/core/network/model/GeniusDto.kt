package com.lochanpthakke.empee3.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GeniusSearchResponse(
    val response: GeniusResponseBody? = null,
)

@Serializable
data class GeniusResponseBody(
    val sections: List<GeniusSectionDto>? = null,
    val hits: List<GeniusHitDto>? = null,
)

@Serializable
data class GeniusSectionDto(
    val type: String? = null,
    val hits: List<GeniusHitDto>? = null,
)

@Serializable
data class GeniusHitDto(
    val type: String? = null,
    val result: GeniusResultDto? = null,
)

@Serializable
data class GeniusResultDto(
    val id: Long? = null,
    val name: String? = null,
    val title: String? = null,
    @SerialName("artist_names") val artistNames: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("header_image_url") val headerImageUrl: String? = null,
    @SerialName("primary_artist") val primaryArtist: GeniusArtistDto? = null,
    @SerialName("primary_artists") val primaryArtists: List<GeniusArtistDto>? = null,
)

@Serializable
data class GeniusArtistDto(
    val id: Long? = null,
    val name: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("header_image_url") val headerImageUrl: String? = null,
)
