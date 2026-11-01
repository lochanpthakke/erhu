@file:Suppress("detekt:All")

package com.erhu.erhu.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("com.lochanpthakke.empee3.core.network.model.MusicBrainzSearchResponse")
data class MusicBrainzSearchResponse(
    val recordings: List<MusicBrainzRecordingDto> = emptyList(),
)

@Serializable
@SerialName("com.lochanpthakke.empee3.core.network.model.MusicBrainzArtistSearchResponse")
data class MusicBrainzArtistSearchResponse(
    val artists: List<MusicBrainzArtistDto> = emptyList(),
)

@Serializable
@SerialName("com.lochanpthakke.empee3.core.network.model.MusicBrainzArtistDto")
data class MusicBrainzArtistDto(
    val id: String = "",
    val name: String = "",
    val score: Int = 0,
    val disambiguation: String? = null,
    val type: String? = null,
    val country: String? = null,
    val aliases: List<MusicBrainzAliasDto> = emptyList(),
    val tags: List<MusicBrainzTagDto> = emptyList(),
    @SerialName("life-span")
    val lifeSpan: MusicBrainzLifeSpanDto? = null,
    val relations: List<MusicBrainzRelationDto> = emptyList(),
)

@Serializable
@SerialName("com.lochanpthakke.empee3.core.network.model.MusicBrainzAliasDto")
data class MusicBrainzAliasDto(
    val name: String = "",
    @SerialName("sort-name")
    val sortName: String? = null,
    val primary: Boolean? = null,
)

@Serializable
@SerialName("com.lochanpthakke.empee3.core.network.model.MusicBrainzTagDto")
data class MusicBrainzTagDto(
    val name: String = "",
    val count: Int = 0,
)

@Serializable
@SerialName("com.lochanpthakke.empee3.core.network.model.MusicBrainzLifeSpanDto")
data class MusicBrainzLifeSpanDto(
    val begin: String? = null,
    val end: String? = null,
    val ended: Boolean? = null,
)

@Serializable
@SerialName("com.lochanpthakke.empee3.core.network.model.MusicBrainzRelationDto")
data class MusicBrainzRelationDto(
    val type: String = "",
    val url: MusicBrainzUrlDto? = null,
)

@Serializable
@SerialName("com.lochanpthakke.empee3.core.network.model.MusicBrainzUrlDto")
data class MusicBrainzUrlDto(
    val id: String = "",
    val resource: String = "",
)

@Serializable
@SerialName("com.lochanpthakke.empee3.core.network.model.MusicBrainzReleaseGroupSearchResponse")
data class MusicBrainzReleaseGroupSearchResponse(
    @SerialName("release-groups")
    val releaseGroups: List<MusicBrainzReleaseGroupDto> = emptyList(),
)

@Serializable
@SerialName("com.lochanpthakke.empee3.core.network.model.MusicBrainzRecordingDto")
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
@SerialName("com.lochanpthakke.empee3.core.network.model.MusicBrainzArtistCreditDto")
data class MusicBrainzArtistCreditDto(
    val name: String,
    @SerialName("joinphrase")
    val joinPhrase: String? = null,
)

@Serializable
@SerialName("com.lochanpthakke.empee3.core.network.model.MusicBrainzReleaseDto")
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
@SerialName("com.lochanpthakke.empee3.core.network.model.MusicBrainzReleaseGroupDto")
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
@SerialName("com.lochanpthakke.empee3.core.network.model.MusicBrainzReleaseGroupGenresDto")
data class MusicBrainzReleaseGroupGenresDto(
    val id: String = "",
    val genres: List<MusicBrainzGenreDto> = emptyList(),
)

@Serializable
@SerialName("com.lochanpthakke.empee3.core.network.model.MusicBrainzGenreDto")
data class MusicBrainzGenreDto(
    val name: String,
    val count: Int = 0,
)
