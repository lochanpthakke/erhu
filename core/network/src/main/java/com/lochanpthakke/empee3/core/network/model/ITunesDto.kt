package com.lochanpthakke.empee3.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ITunesSearchResponse(
    val resultCount: Int = 0,
    val results: List<ITunesResultDto> = emptyList(),
)

@Serializable
data class ITunesResultDto(
    @SerialName("wrapperType")
    val wrapperType: String? = null,
    @SerialName("kind")
    val kind: String? = null,
    @SerialName("collectionType")
    val collectionType: String? = null,
    @SerialName("artistId")
    val artistId: Long? = null,
    @SerialName("collectionId")
    val collectionId: Long? = null,
    @SerialName("trackId")
    val trackId: Long? = null,
    @SerialName("artistName")
    val artistName: String? = null,
    @SerialName("collectionName")
    val collectionName: String? = null,
    @SerialName("trackName")
    val trackName: String? = null,
    @SerialName("artworkUrl100")
    val artworkUrl100: String? = null,
    @SerialName("artworkUrl60")
    val artworkUrl60: String? = null,
    @SerialName("releaseDate")
    val releaseDate: String? = null,
    @SerialName("trackTimeMillis")
    val trackTimeMillis: Long? = null,
    @SerialName("primaryGenreName")
    val primaryGenreName: String? = null,
    @SerialName("trackNumber")
    val trackNumber: Int? = null,
    @SerialName("discNumber")
    val discNumber: Int? = null,
)
