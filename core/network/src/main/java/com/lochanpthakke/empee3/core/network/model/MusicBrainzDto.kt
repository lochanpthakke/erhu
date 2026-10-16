@file:Suppress("detekt:All")

package com.lochanpthakke.empee3.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MusicBrainzSearchResponse(
    val recordings: List<MusicBrainzRecordingDto> = emptyList(),
)

@Serializable
data class MusicBrainzReleaseGroupSearchResponse(
    @SerialName("release-groups")
    val releaseGroups: List<MusicBrainzReleaseGroupDto> = emptyList(),
)

@Serializable
data class MusicBrainzRecordingDto(
    val id: String,
    val title: String,
    val length: Long? = null,
    /** e.g. "live, 2011-04-17: Coachella" or "explicit": the version, kept out of [title]. */
    val disambiguation: String? = null,
    @SerialName("first-release-date")
    val firstReleaseDate: String? = null,
    @SerialName("artist-credit")
    val artistCredit: List<MusicBrainzArtistCreditDto> = emptyList(),
    val releases: List<MusicBrainzReleaseDto> = emptyList(),
)

@Serializable
data class MusicBrainzArtistCreditDto(
    val name: String,
    @SerialName("joinphrase")
    val joinPhrase: String? = null,
)

@Serializable
data class MusicBrainzReleaseDto(
    val id: String,
    val title: String,
    val date: String? = null,
    /** "Official", "Promotion", "Bootleg", "Pseudo-Release"; absent when unknown. */
    val status: String? = null,
    @SerialName("release-group")
    val releaseGroup: MusicBrainzReleaseGroupDto? = null,
)

@Serializable
data class MusicBrainzReleaseGroupDto(
    val id: String,
    val title: String? = null,
    @SerialName("primary-type")
    val primaryType: String? = null,
    /** e.g. "Compilation", "Live", "Mixtape/Street", "Remix". */
    @SerialName("secondary-types")
    val secondaryTypes: List<String> = emptyList(),
)

/** `release-group/{id}?inc=genres`: curated genres with their vote counts. */
@Serializable
data class MusicBrainzReleaseGroupGenresDto(
    val id: String = "",
    val genres: List<MusicBrainzGenreDto> = emptyList(),
)

@Serializable
data class MusicBrainzGenreDto(
    val name: String,
    val count: Int = 0,
)
