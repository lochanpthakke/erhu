package com.erhu.erhu.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("com.lochanpthakke.empee3.core.network.model.WikipediaQueryResponse")
data class WikipediaQueryResponse(
    val query: WikipediaQueryResultDto? = null,
)

@Serializable
@SerialName("com.lochanpthakke.empee3.core.network.model.WikipediaQueryResultDto")
data class WikipediaQueryResultDto(
    val pages: Map<String, WikipediaPageDto> = emptyMap(),
)

@Serializable
@SerialName("com.lochanpthakke.empee3.core.network.model.WikipediaPageDto")
data class WikipediaPageDto(
    val pageid: Long = 0,
    val title: String = "",
    val thumbnail: WikipediaThumbnailDto? = null,
    val imageinfo: List<WikipediaImageInfoDto>? = null,
)

@Serializable
@SerialName("com.lochanpthakke.empee3.core.network.model.WikipediaThumbnailDto")
data class WikipediaThumbnailDto(
    val source: String = "",
    val width: Int = 0,
    val height: Int = 0,
)

@Serializable
@SerialName("com.lochanpthakke.empee3.core.network.model.WikipediaImageInfoDto")
data class WikipediaImageInfoDto(
    val url: String? = null,
    @SerialName("thumburl")
    val thumbUrl: String? = null,
    val mime: String? = null,
    val width: Int = 0,
    val height: Int = 0,
)
