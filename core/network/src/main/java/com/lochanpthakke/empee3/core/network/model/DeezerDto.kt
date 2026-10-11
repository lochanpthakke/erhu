@file:Suppress("detekt:All")

package com.lochanpthakke.empee3.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeezerSearchResponse(
    val data: List<DeezerTrackDto> = emptyList(),
)

@Serializable
data class DeezerArtistSearchResponse(
    /** Null when the body had neither results nor an error: an unreadable answer, not "no such artist". */
    val data: List<DeezerArtistDto>? = null,
    /** Set instead of [data] when the request failed, e.g. "Quota limit exceeded" (code 4). */
    val error: DeezerErrorDto? = null,
)

@Serializable
data class DeezerErrorDto(
    val type: String? = null,
    val message: String? = null,
    val code: Int? = null,
)

@Serializable
data class DeezerTrackDto(
    val id: Long,
    val title: String,
    val duration: Long? = null,
    @SerialName("bpm")
    val bpm: Float? = null,
    val artist: DeezerArtistDto? = null,
    val album: DeezerAlbumDto? = null,
)

@Serializable
data class DeezerArtistDto(
    val id: Long,
    val name: String,
    @SerialName("picture_small")
    val pictureSmall: String? = null,
    @SerialName("picture_medium")
    val pictureMedium: String? = null,
    @SerialName("picture_big")
    val pictureBig: String? = null,
    @SerialName("picture_xl")
    val pictureXl: String? = null,
    /** Followers on Deezer; used only to tell apart profiles that share one exact name. */
    @SerialName("nb_fan")
    val nbFan: Long = 0,
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
