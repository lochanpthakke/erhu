package com.lochanpthakke.empee3.navigation

import kotlinx.serialization.Serializable

@Serializable
object Library

@Serializable
data class PlaylistDetail(
    val playlistId: Long,
)

/** A smart playlist's page ([playlist] is a SmartPlaylist name), opened from the Tracks page. */
@Serializable
data class SmartPlaylistDetail(
    val playlist: String,
)

/** An album's detail page, opened from the library's album grid. */
@Serializable
data class AlbumDetail(
    val albumId: Long,
)

/** An artist's detail page, opened from the library's artist grid or search. */
@Serializable
data class ArtistDetail(
    val artistId: Long,
)

/** Sound Capsule: the month's listening statistics. */
@Serializable
object SoundCapsule

@Serializable
object Equalizer

@Serializable
object MetadataEditor

@Serializable
object ArtworkManager

/** Wi-Fi Transfer: receive music from a computer's browser over the local network. */
@Serializable
object WifiTransfer

@Serializable
object Settings

/** A settings category pushed on top of [Settings], so Back returns to the list. */
@Serializable
data class SettingsCategory(
    val categoryId: String,
)
