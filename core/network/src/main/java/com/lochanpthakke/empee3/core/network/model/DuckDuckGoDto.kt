package com.lochanpthakke.empee3.core.network.model

import kotlinx.serialization.Serializable

@Serializable
data class DuckDuckGoImageSearchResponse(
    val results: List<DuckDuckGoImageResultDto>? = null,
)

@Serializable
data class DuckDuckGoImageResultDto(
    val title: String? = null,
    val image: String? = null,
    val thumbnail: String? = null,
    val width: Int? = null,
    val height: Int? = null,
)
