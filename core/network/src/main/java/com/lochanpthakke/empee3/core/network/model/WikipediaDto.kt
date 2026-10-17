package com.lochanpthakke.empee3.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WikipediaQueryResponse(
    val query: WikipediaQueryResultDto? = null,
)

@Serializable
data class WikipediaQueryResultDto(
    val pages: Map<String, WikipediaPageDto> = emptyMap(),
)

@Serializable
data class WikipediaPageDto(
    val pageid: Long = 0,
    val title: String = "",
    val thumbnail: WikipediaThumbnailDto? = null,
    val imageinfo: List<WikipediaImageInfoDto>? = null,
)

@Serializable
data class WikipediaThumbnailDto(
    val source: String = "",
    val width: Int = 0,
    val height: Int = 0,
)

@Serializable
data class WikipediaImageInfoDto(
    val url: String? = null,
    @SerialName("thumburl")
    val thumbUrl: String? = null,
    val mime: String? = null,
    val width: Int = 0,
    val height: Int = 0,
)
