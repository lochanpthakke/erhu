package com.lochanpthakke.empee3.core.network.model

import kotlinx.serialization.Serializable

@Serializable
data class DuckDuckGoImageResultDto(
    val results: List<DuckDuckGoImageDto>? = null,
)

@Serializable
data class DuckDuckGoImageDto(
    val image: String? = null,
)
