package com.erhu.erhu.navigation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("com.lochanpthakke.empee3.navigation.Library")
object Library

@Serializable
@SerialName("com.lochanpthakke.empee3.navigation.PlaylistDetail")
data class PlaylistDetail(
    val playlistId: Long,
)

/** A smart mix's page ([playlist] is the mix's key, see MixKind), opened from the Tracks page. */
@Serializable
@SerialName("com.lochanpthakke.empee3.navigation.SmartPlaylistDetail")
data class SmartPlaylistDetail(
    val playlist: String,
)

/** An album's detail page, opened from the library's album grid. */
@Serializable
@SerialName("com.lochanpthakke.empee3.navigation.AlbumDetail")
data class AlbumDetail(
    val albumId: Long,
)

/** An artist's detail page, opened from the library's artist grid or search. */
@Serializable
@SerialName("com.lochanpthakke.empee3.navigation.ArtistDetail")
data class ArtistDetail(
    val artistId: Long,
)

/** Sound Capsule: the month's listening statistics. */
@Serializable
@SerialName("com.lochanpthakke.empee3.navigation.SoundCapsule")
object SoundCapsule

@Serializable
@SerialName("com.lochanpthakke.empee3.navigation.Equalizer")
object Equalizer

@Serializable
@SerialName("com.lochanpthakke.empee3.navigation.MetadataEditor")
object MetadataEditor

@Serializable
@SerialName("com.lochanpthakke.empee3.navigation.LibraryBuilder")
object LibraryBuilder

@Serializable
@SerialName("com.lochanpthakke.empee3.navigation.ArtworkManager")
object ArtworkManager

typealias ArtworkFetcher = ArtworkManager

@Serializable
@SerialName("com.lochanpthakke.empee3.navigation.AnimatedArtworkFetcher")
object AnimatedArtworkFetcher

typealias MotionArtworkManager = AnimatedArtworkFetcher

/** Wi-Fi Transfer: receive music from a computer's browser over the local network. */
@Serializable
@SerialName("com.lochanpthakke.empee3.navigation.WifiTransfer")
object WifiTransfer

@Serializable
@SerialName("com.lochanpthakke.empee3.navigation.Settings")
object Settings

/** A settings category pushed on top of [Settings], so Back returns to the list. */
@Serializable
@SerialName("com.lochanpthakke.empee3.navigation.SettingsCategory")
data class SettingsCategory(
    val categoryId: String,
)

/** A featured station is browsed before the user explicitly starts playback. */
@Serializable
data class RadioStationDetail(
    val albumId: Long,
)
