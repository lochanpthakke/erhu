package com.erhu.erhu.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("com.lochanpthakke.empee3.core.network.model.WikidataEntityResponse")
data class WikidataEntityResponse(
    val entities: Map<String, WikidataEntityDto> = emptyMap(),
)

@Serializable
@SerialName("com.lochanpthakke.empee3.core.network.model.WikidataEntityDto")
data class WikidataEntityDto(
    val id: String = "",
    val claims: Map<String, List<WikidataClaimDto>> = emptyMap(),
)

@Serializable
@SerialName("com.lochanpthakke.empee3.core.network.model.WikidataClaimDto")
data class WikidataClaimDto(
    val mainsnak: WikidataSnakDto? = null,
    val rank: String = "normal",
)

@Serializable
@SerialName("com.lochanpthakke.empee3.core.network.model.WikidataSnakDto")
data class WikidataSnakDto(
    val datavalue: WikidataDataValueDto? = null,
)

@Serializable
@SerialName("com.lochanpthakke.empee3.core.network.model.WikidataDataValueDto")
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
