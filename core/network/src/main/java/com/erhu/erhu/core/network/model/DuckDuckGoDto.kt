package com.erhu.erhu.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("com.lochanpthakke.empee3.core.network.model.DuckDuckGoImageResultDto")
data class DuckDuckGoImageResultDto(
    val results: List<DuckDuckGoImageDto>? = null,
)

@Serializable
@SerialName("com.lochanpthakke.empee3.core.network.model.DuckDuckGoImageDto")
data class DuckDuckGoImageDto(
    val image: String? = null,
)
