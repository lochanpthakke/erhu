package com.lochanpthakke.empee3.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GoogleImageSearchResponse(
    val items: List<GoogleImageSearchItemDto>? = null,
    val error: GoogleImageSearchErrorDto? = null,
)

@Serializable
data class GoogleImageSearchItemDto(
    val link: String? = null,
    val title: String? = null,
    val mime: String? = null,
    val image: GoogleImageSearchMetadataDto? = null,
)

@Serializable
data class GoogleImageSearchMetadataDto(
    @SerialName("thumbnailLink")
    val thumbnailLink: String? = null,
    val height: Int = 0,
    val width: Int = 0,
)

@Serializable
data class GoogleImageSearchErrorDto(
    val code: Int = 0,
    val message: String? = null,
)
