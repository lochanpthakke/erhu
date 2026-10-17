package com.lochanpthakke.empee3.core.network.model

import kotlinx.serialization.Serializable

@Serializable
data class FanartArtistResponse(
    val artistthumb: List<FanartImageDto>? = null,
    val artistbackground: List<FanartImageDto>? = null,
)

@Serializable
data class FanartImageDto(
    val url: String? = null,
    val likes: String? = null,
)
