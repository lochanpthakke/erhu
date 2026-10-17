package com.lochanpthakke.empee3.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TheAudioDbArtistResponse(
    val artists: List<TheAudioDbArtistDto>? = null,
)

@Serializable
data class TheAudioDbArtistDto(
    val idArtist: String? = null,
    val strArtist: String? = null,
    @SerialName("strMusicBrainzID")
    val strMusicBrainzID: String? = null,
    val strArtistThumb: String? = null,
    val strArtistFanart: String? = null,
    val strArtistFanart2: String? = null,
    val strArtistFanart3: String? = null,
    val strArtistFanart4: String? = null,
    val strArtistWideThumb: String? = null,
    val strArtistBanner: String? = null,
)
