@file:Suppress("detekt:All")

package com.lochanpthakke.empee3.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeezerSearchResponse(
    val data: List<DeezerTrackDto> = emptyList(),
)

@Serializable
data class DeezerTrackDto(
    val id: Long,
    val title: String,
    val duration: Long? = null,
    val artist: DeezerArtistDto? = null,
    val album: DeezerAlbumDto? = null,
)

@Serializable
data class DeezerArtistDto(
    val id: Long,
    val name: String,
)

@Serializable
data class DeezerAlbumDto(
    val id: Long,
    val title: String,
    @SerialName("cover_xl")
    val coverXl: String? = null,
    @SerialName("cover_big")
    val coverBig: String? = null,
    @SerialName("cover_medium")
    val coverMedium: String? = null,
)
