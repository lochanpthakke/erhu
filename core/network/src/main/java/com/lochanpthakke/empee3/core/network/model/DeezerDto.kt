@file:Suppress("detekt:All")

package com.lochanpthakke.empee3.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeezerSearchResponse(
    val data: List<DeezerTrackDto> = emptyList(),
    /**
     * Matches Deezer counted before filtering by the caller's country. In countries where Deezer is
     * not offered (e.g. India, where `api.deezer.com/infos` reports `"open": false`) track and album
     * searches answer `total > 0` with an empty [data] (observed 2026-10-13).
     */
    val total: Int = 0,
    val error: DeezerErrorDto? = null,
)

/** `album/{id}`: the only Deezer object that carries genres. Lookups by id work in every country. */
@Serializable
data class DeezerAlbumDetailDto(
    val id: Long = 0,
    val title: String? = null,
    val genres: DeezerGenreListDto? = null,
    val error: DeezerErrorDto? = null,
)

@Serializable
data class DeezerGenreListDto(
    val data: List<DeezerGenreDto> = emptyList(),
)

@Serializable
data class DeezerGenreDto(
    val id: Long = 0,
    val name: String,
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
    /** e.g. "(Live)" or "(Remix)": the version, which Deezer keeps out of [title] in some records. */
    @SerialName("title_version")
    val titleVersion: String? = null,
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
