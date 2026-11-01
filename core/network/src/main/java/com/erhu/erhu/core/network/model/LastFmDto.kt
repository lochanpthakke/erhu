@file:Suppress("detekt:All")

package com.erhu.erhu.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("com.lochanpthakke.empee3.core.network.model.LastFmSearchResponse")
data class LastFmSearchResponse(
    val results: LastFmResultsDto? = null,
    /** Set instead of [results] on failure, e.g. 10 = "Invalid API key". */
    val error: Int? = null,
    val message: String? = null,
)

@Serializable
@SerialName("com.lochanpthakke.empee3.core.network.model.LastFmResultsDto")
data class LastFmResultsDto(
    @SerialName("trackmatches")
    val trackMatches: LastFmTrackMatchesDto? = null,
)

@Serializable
@SerialName("com.lochanpthakke.empee3.core.network.model.LastFmTrackMatchesDto")
data class LastFmTrackMatchesDto(
    val track: List<LastFmTrackDto> = emptyList(),
)

@Serializable
@SerialName("com.lochanpthakke.empee3.core.network.model.LastFmTrackDto")
data class LastFmTrackDto(
    val name: String,
    val artist: String,
    val listeners: String? = null,
    val image: List<LastFmImageDto> = emptyList(),
)

@Serializable
@SerialName("com.lochanpthakke.empee3.core.network.model.LastFmImageDto")
data class LastFmImageDto(
    @SerialName("#text")
    val url: String,
    val size: String,
)
