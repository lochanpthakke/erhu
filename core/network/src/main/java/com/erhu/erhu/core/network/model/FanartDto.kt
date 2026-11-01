package com.erhu.erhu.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("com.lochanpthakke.empee3.core.network.model.FanartArtistResponse")
data class FanartArtistResponse(
    val artistthumb: List<FanartImageDto>? = null,
    val artistbackground: List<FanartImageDto>? = null,
)

@Serializable
@SerialName("com.lochanpthakke.empee3.core.network.model.FanartImageDto")
data class FanartImageDto(
    val url: String? = null,
    val likes: String? = null,
)
