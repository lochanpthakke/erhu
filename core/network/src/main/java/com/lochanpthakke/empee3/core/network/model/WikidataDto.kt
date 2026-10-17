package com.lochanpthakke.empee3.core.network.model

import kotlinx.serialization.Serializable

@Serializable
data class WikidataEntityResponse(
    val entities: Map<String, WikidataEntityDto> = emptyMap(),
)

@Serializable
data class WikidataEntityDto(
    val id: String = "",
    val claims: Map<String, List<WikidataClaimDto>> = emptyMap(),
)

@Serializable
data class WikidataClaimDto(
    val mainsnak: WikidataSnakDto? = null,
    val rank: String = "normal",
)

@Serializable
data class WikidataSnakDto(
    val datavalue: WikidataDataValueDto? = null,
)

@Serializable
data class WikidataDataValueDto(
    val value: kotlinx.serialization.json.JsonElement? = null,
) {
    val stringValue: String?
        get() =
            if (value is kotlinx.serialization.json.JsonPrimitive) {
                value.content
            } else {
                null
            }
}
