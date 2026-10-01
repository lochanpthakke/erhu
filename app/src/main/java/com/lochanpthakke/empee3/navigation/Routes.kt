package com.lochanpthakke.empee3.navigation

import kotlinx.serialization.Serializable

@Serializable
object Library

@Serializable
object NowPlaying

@Serializable
object Playlists

@Serializable
data class PlaylistDetail(
    val playlistId: Long,
)

/** An album's detail page, opened from the library's album grid. */
@Serializable
data class AlbumDetail(
    val albumId: Long,
)

@Serializable
object Equalizer

@Serializable
object MetadataEditor

@Serializable
object Settings

/** A settings category pushed on top of [Settings], so Back returns to the list. */
@Serializable
data class SettingsCategory(
    val categoryId: String,
)
