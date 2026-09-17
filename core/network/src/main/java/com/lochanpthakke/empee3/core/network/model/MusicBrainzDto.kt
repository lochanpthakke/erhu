@file:Suppress("detekt:All")

package com.lochanpthakke.empee3.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MusicBrainzSearchResponse(
    val recordings: List<MusicBrainzRecordingDto> = emptyList(),
)

@Serializable
data class MusicBrainzRecordingDto(
    val id: String,
    val title: String,
    val length: Long? = null,
    @SerialName("artist-credit")
    val artistCredit: List<MusicBrainzArtistCreditDto> = emptyList(),
    val releases: List<MusicBrainzReleaseDto> = emptyList(),
)

@Serializable
data class MusicBrainzArtistCreditDto(
    val name: String,
)

@Serializable
data class MusicBrainzReleaseDto(
    val id: String,
    val title: String,
    val date: String? = null,
)
