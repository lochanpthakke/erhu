package com.erhu.erhu.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("com.lochanpthakke.empee3.core.network.model.GoogleImageSearchResponse")
data class GoogleImageSearchResponse(
    val items: List<GoogleImageSearchItemDto>? = null,
    val error: GoogleImageSearchErrorDto? = null,
)

@Serializable
@SerialName("com.lochanpthakke.empee3.core.network.model.GoogleImageSearchItemDto")
data class GoogleImageSearchItemDto(
    val link: String? = null,
    val title: String? = null,
    val mime: String? = null,
    val image: GoogleImageSearchMetadataDto? = null,
)

@Serializable
@SerialName("com.lochanpthakke.empee3.core.network.model.GoogleImageSearchMetadataDto")
data class GoogleImageSearchMetadataDto(
    @SerialName("thumbnailLink")
    val thumbnailLink: String? = null,
    val height: Int = 0,
    val width: Int = 0,
)

@Serializable
@SerialName("com.lochanpthakke.empee3.core.network.model.GoogleImageSearchErrorDto")
data class GoogleImageSearchErrorDto(
    val code: Int = 0,
    val message: String? = null,
)
