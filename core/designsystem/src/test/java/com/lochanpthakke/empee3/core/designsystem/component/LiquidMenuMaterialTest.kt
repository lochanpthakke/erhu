package com.lochanpthakke.empee3.core.designsystem.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.lochanpthakke.empee3.core.designsystem.theme.ArtworkColors
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
    fun warmCoolAndGreenArtworkColorTheFillAndReflections() {
        val warm = glassMenuColors(true, 0.7f, palette(Color(0xFFBC7138)))
        val cool = glassMenuColors(true, 0.7f, palette(Color(0xFF347DBB)))
        val green = glassMenuColors(true, 0.7f, palette(Color(0xFF37945A)))
        val darkWarm = glassMenuColors(true, 0.7f, palette(Color(0xFF42271A)))
        val neutral = glassMenuColors(true, 0.7f, palette(Color.Gray))
        assertTrue(warm.surface.red > warm.surface.blue)
        assertTrue(darkWarm.surface.red > darkWarm.surface.blue)
        assertEquals(neutral.surface.red, neutral.surface.blue, 0.001f)
        assertTrue(cool.surface.blue > cool.surface.red)
        assertTrue(green.surface.green > green.surface.red)
        assertTrue(warm.reflection.red > warm.reflection.blue)
        assertTrue(cool.edge.blue > cool.edge.red)
        assertTrue(warm.surface.alpha < 0.85f && cool.surface.alpha > 0.7f)
    }

    @Test
    fun playbackTintIgnoresBrowsedThemeSurfaceAndGlassOffIgnoresArtwork() {
        val art = palette(Color.Red)
        assertEquals(glassMenuColors(true, 0.7f, art, Color.Blue), glassMenuColors(true, 0.7f, art, Color.Green))
        assertEquals(glassMenuColors(true, null), glassMenuColors(true, null, art))
        assertNotEquals(glassMenuColors(true, 0.7f, art), glassMenuColors(true, 0.7f))
    }

    @Test
    fun labelsAndHeadingsRetainContrastThroughGlassAndGloss() {
        val artworks = listOf(null, Color.White, Color.Black, Color.Gray, Color.Red, Color.Blue, Color.Green, Color(0xFFBC7138))
        for (dark in listOf(true, false)) {
            for (art in artworks) assertContrast(dark, art?.let(::palette))
        }
    }

    private fun assertContrast(
        dark: Boolean,
        art: ArtworkColors?,
    ) {
        val colors = glassMenuColors(dark, 0.7f, art)
        val destructive = menuDestructiveColor(if (dark) Color(0xFFFFB4AB) else Color(0xFFB3261E), colors.content)
        for (background in listOf(Color.White, Color.Black)) {
            val middle = colors.surface.compositeOver(background)
            val topFill = colors.surface.copy(alpha = (colors.surface.alpha + 0.06f).coerceAtMost(1f)).compositeOver(background)
            val upper = colors.reflection.compositeOver(topFill)
            val corner = colors.reflection.copy(alpha = colors.reflection.alpha * 0.6f).compositeOver(upper)
            for (surface in listOf(middle, corner, Color.Black.copy(alpha = 0.075f).compositeOver(middle))) {
                assertTrue("Action contrast: $dark $art", contrast(colors.content, surface) >= 4.5f)
                val heading = colors.content.copy(alpha = 0.9f).compositeOver(surface)
                assertTrue("Heading contrast: $dark $art", contrast(heading, surface) >= 4.5f)
                assertTrue("Destructive contrast: $dark $art", contrast(destructive, surface) >= 4.5f)
            }
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

    private fun palette(color: Color) = ArtworkColors(color, color, color)

    private fun song(
        uri: String,
        artwork: String,
    ) = Song(1, "Title", "Artist", "Album", 1000, uri, artwork)

    private fun contrast(
        a: Color,
        b: Color,
    ): Float = (maxOf(a.luminance(), b.luminance()) + 0.05f) / (minOf(a.luminance(), b.luminance()) + 0.05f)
}
