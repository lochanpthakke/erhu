package com.lochanpthakke.empee3.navigation

import kotlinx.serialization.Serializable

@Serializable
object Library

@Serializable
object NowPlaying

@Serializable
object Playlists

@Serializable
object Equalizer

@Serializable
object MetadataEditor

@Serializable
object ArtworkManager

@Serializable
data class Settings(
    val category: String? = null,
)
