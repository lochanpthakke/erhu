package com.lochanpthakke.empee3.core.designsystem.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.lochanpthakke.empee3.core.designsystem.theme.popupGlassStyle
import com.lochanpthakke.empee3.core.designsystem.theme.popupGlassTokens
import com.lochanpthakke.empee3.core.designsystem.theme.playbackArtworkIdentity
import com.lochanpthakke.empee3.core.model.PlaybackState
import com.lochanpthakke.empee3.core.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiquidMenuMaterialTest {
    @Test
    fun materialIsNeutralAtEveryClarityAndTheme() {
        for (dark in listOf(true, false)) {
            for (opacity in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
                val tokens = popupGlassTokens(dark, opacity)
                val fill = Color(tokens.panelFill)
                assertEquals(fill.red, fill.green, 0.001f)
                assertEquals(fill.green, fill.blue, 0.001f)
                assertEquals(0L, tokens.tint)
                assertEquals(1f, tokens.frostSaturation)
                assertTrue(fill.alpha < 0.7f)
            }
        }
    }

    @Test
    fun clarityCoordinatesDiffusionFillAndContrast() {
        for (dark in listOf(true, false)) {
            val clear = popupGlassStyle(1f, dark)
            val opaque = popupGlassStyle(0f, dark)
            val middle = popupGlassStyle(0.5f, dark)
            assertTrue(middle.opacity > clear.opacity && middle.opacity < opaque.opacity)
            assertTrue(opaque.opacity - clear.opacity >= 0.5f)
            assertTrue(opaque.blurDp - clear.blurDp >= 24)
            assertTrue(clear.refractionDp > middle.refractionDp && middle.refractionDp > opaque.refractionDp)
            val samples = listOf(0f, 0.25f, 0.5f, 0.75f, 1f).map { popupGlassStyle(it, dark) }
            samples.zipWithNext().forEach { (less, more) ->
                assertTrue(less.opacity > more.opacity)
                assertTrue(less.blurDp > more.blurDp)
                assertTrue(less.refractionDp < more.refractionDp)
                assertTrue(less.reflectionAlpha < more.reflectionAlpha)
                assertTrue(less.edgeAlpha < more.edgeAlpha)
            }
            assertEquals(popupGlassStyle(0.5f, dark), popupGlassStyle(Float.NaN, dark))
            assertEquals(opaque, popupGlassStyle(-1f, dark))
            assertEquals(clear, popupGlassStyle(2f, dark))
        }
    }

    @Test
    fun labelsHeadingsAndDestructiveActionsHaveContrastAtBackdropExtremes() {
        for (dark in listOf(true, false)) {
            for (opacity in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
                assertBackdropContrast(dark, opacity, 0f)
                assertBackdropContrast(dark, opacity, 1f)
            }
        }
    }

    private fun assertBackdropContrast(dark: Boolean, opacity: Float, channel: Float) {
        val ink = if (dark) Color(0xFFF9F9F9) else Color(0xFF171717)
        val tokens = popupGlassTokens(dark, opacity)
        val toned = channel * (1f - tokens.frostDim - tokens.frostLift) + tokens.frostLift
        val backing = Color(toned, toned, toned)
        val middle = Color(tokens.panelFill).compositeOver(backing)
        val top = Color(tokens.specular).compositeOver(middle)
        val corner = Color(tokens.specular).copy(alpha = Color(tokens.specular).alpha * 0.6f).compositeOver(top)
        for (surface in listOf(middle, corner)) {
            assertTrue("Label: $dark $opacity $channel", contrast(ink, surface) >= 4.5f)
            assertTrue("Heading: $dark $opacity $channel", contrast(ink.copy(alpha = 0.9f).compositeOver(surface), surface) >= 4.5f)
            val destructive = menuDestructiveColor(if (dark) Color(0xFFFFB4AB) else Color(0xFFB3261E), ink)
            assertTrue("Destructive: $dark $opacity $channel", contrast(destructive, surface) >= 4.5f)
        }
    }

    @Test
    fun paletteIdentityUsesCurrentTrackRatherThanQueueOrPosition() {
        val playing = song("playing", "warm-art")
        val other = song("browsed", "blue-art")
        val state = PlaybackState(currentSong = playing, queue = listOf(other))
        assertEquals("playing" to "warm-art", playbackArtworkIdentity(state))
        assertEquals(playbackArtworkIdentity(state), playbackArtworkIdentity(state.copy(positionMs = 1234, queue = emptyList())))
        assertNotEquals(playbackArtworkIdentity(state), playbackArtworkIdentity(state.copy(currentSong = other)))
        assertNotEquals(
            playbackArtworkIdentity(state),
            playbackArtworkIdentity(state.copy(currentSong = playing.copy(artworkUri = "new-art"))),
        )
        assertNull(playbackArtworkIdentity(state.copy(currentSong = null)))
    }

    private fun song(
        uri: String,
        artwork: String,
    ) = Song(1, "Title", "Artist", "Album", 1000, uri, artwork)

    private fun contrast(
        a: Color,
        b: Color,
    ): Float = (maxOf(a.luminance(), b.luminance()) + 0.05f) / (minOf(a.luminance(), b.luminance()) + 0.05f)
}
