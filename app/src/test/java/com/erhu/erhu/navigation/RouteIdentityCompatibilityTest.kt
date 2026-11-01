package com.erhu.erhu.navigation

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/** Saved typed-navigation route IDs must survive a source package rename. */
@OptIn(ExperimentalSerializationApi::class)
class RouteIdentityCompatibilityTest {
    @Test
    fun publishedRouteDescriptorsStayStable() {
        val routes = listOf(
            "Library" to Library.serializer(),
            "PlaylistDetail" to PlaylistDetail.serializer(),
            "SmartPlaylistDetail" to SmartPlaylistDetail.serializer(),
            "AlbumDetail" to AlbumDetail.serializer(),
            "ArtistDetail" to ArtistDetail.serializer(),
            "SoundCapsule" to SoundCapsule.serializer(),
            "Equalizer" to Equalizer.serializer(),
            "MetadataEditor" to MetadataEditor.serializer(),
            "LibraryBuilder" to LibraryBuilder.serializer(),
            "ArtworkManager" to ArtworkManager.serializer(),
            "AnimatedArtworkFetcher" to AnimatedArtworkFetcher.serializer(),
            "WifiTransfer" to WifiTransfer.serializer(),
            "Settings" to Settings.serializer(),
            "SettingsCategory" to SettingsCategory.serializer(),
        )
        routes.forEach { (name, serializer) ->
            assertEquals("com.lochanpthakke.empee3.navigation.$name", serializer.descriptor.serialName)
        }
    }

    @Test
    fun previouslySavedRouteArgumentsStillDecode() {
        assertEquals(AlbumDetail(42), Json.decodeFromString(AlbumDetail.serializer(), """{"albumId":42}"""))
        assertEquals(SettingsCategory("theme"), Json.decodeFromString(SettingsCategory.serializer(), """{"categoryId":"theme"}"""))
    }
}
