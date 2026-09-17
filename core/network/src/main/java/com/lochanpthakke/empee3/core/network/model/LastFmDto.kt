@file:Suppress("detekt:All")

package com.lochanpthakke.empee3.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LastFmSearchResponse(
    val results: LastFmResultsDto? = null,
)

@Serializable
data class LastFmResultsDto(
    @SerialName("trackmatches")
    val trackMatches: LastFmTrackMatchesDto? = null,
)

@Serializable
data class LastFmTrackMatchesDto(
    val track: List<LastFmTrackDto> = emptyList(),
)

@Serializable
data class LastFmTrackDto(
    val name: String,
    val artist: String,
    val listeners: String? = null,
    val image: List<LastFmImageDto> = emptyList(),
)

@Serializable
data class LastFmImageDto(
    @SerialName("#text")
    val url: String,
    val size: String,
)
