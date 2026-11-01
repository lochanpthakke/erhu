package com.erhu.erhu.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("com.lochanpthakke.empee3.core.network.model.GeniusSearchResponse")
data class GeniusSearchResponse(
    val response: GeniusResponseBody? = null,
)

@Serializable
@SerialName("com.lochanpthakke.empee3.core.network.model.GeniusResponseBody")
data class GeniusResponseBody(
    val sections: List<GeniusSectionDto>? = null,
    val hits: List<GeniusHitDto>? = null,
)

@Serializable
@SerialName("com.lochanpthakke.empee3.core.network.model.GeniusSectionDto")
data class GeniusSectionDto(
    val type: String? = null,
    val hits: List<GeniusHitDto>? = null,
)

@Serializable
@SerialName("com.lochanpthakke.empee3.core.network.model.GeniusHitDto")
data class GeniusHitDto(
    val type: String? = null,
    val result: GeniusResultDto? = null,
)

@Serializable
@SerialName("com.lochanpthakke.empee3.core.network.model.GeniusResultDto")
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
@SerialName("com.lochanpthakke.empee3.core.network.model.GeniusArtistDto")
data class GeniusArtistDto(
    val id: Long? = null,
    val name: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("header_image_url") val headerImageUrl: String? = null,
)
