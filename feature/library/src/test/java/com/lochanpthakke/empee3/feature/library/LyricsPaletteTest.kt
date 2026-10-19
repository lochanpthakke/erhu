package com.lochanpthakke.empee3.feature.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** The lyrics surface's colours from artwork: in the artwork's hue, and always readable. */
class LyricsPaletteTest {
    private fun artwork(vararg colors: Int): IntArray = IntArray(32 * 32) { colors[it % colors.size] }

    private fun hueOf(argb: Int): Float {
        val r = (argb shr 16 and 0xFF) / 255f
        val g = (argb shr 8 and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val hi = maxOf(r, g, b)
        val lo = minOf(r, g, b)
        if (hi == lo) return -1f
        val d = hi - lo
        val h =
            when (hi) {
                r -> ((g - b) / d + if (g < b) 6f else 0f)
                g -> (b - r) / d + 2f
                else -> (r - g) / d + 4f
            }
        return h * 60f
    }

    private fun hueDistance(
        a: Float,
        b: Float,
    ) = minOf(abs(a - b), 360f - abs(a - b))

    /** Text must read on the surface and on every stop of the opaque gradient. */
    private fun assertReadable(palette: LyricsPalette) {
        (palette.gradient + palette.surface).forEach { stop ->
            assertTrue("content ${contrast(palette.content, stop)}", contrast(palette.content, stop) >= 4.5)
            assertTrue("secondary ${contrast(palette.secondary, stop)}", contrast(palette.secondary, stop) >= 3.0)
            assertTrue("accent ${contrast(palette.accent, stop)}", contrast(palette.accent, stop) >= 4.5)
        }
    }

    @Test
    fun theGradientHasTonalDepth_notOneFlatColour() {
        val palette = LyricsPalette.fromPixels(artwork(0xFFFF8C1A.toInt(), 0xFFE0601E.toInt()))
        val (top, middle, bottom) = palette.gradient
        assertEquals(palette.surface, middle)
        assertTrue(relativeLuminance(top) > relativeLuminance(middle))
        assertTrue(relativeLuminance(bottom) < relativeLuminance(middle))
        // The deeper stop turns away from the dominant hue (a complementary tone, not the same colour darker).
        assertTrue(hueDistance(hueOf(bottom), hueOf(middle)) > 10f)
    }

    @Test
    fun monochromeArtwork_givesAGreyGradient() {
        val palette = LyricsPalette.fromPixels(artwork(0xFF222222.toInt(), 0xFF999999.toInt()))
        palette.gradient.forEach { assertEquals(-1f, hueOf(it)) }
        assertReadable(palette)
    }

    @Test
    fun warmOrangeArtwork_givesAWarmDarkSurface() {
        val palette = LyricsPalette.fromPixels(artwork(0xFFFF8C1A.toInt(), 0xFFE0601E.toInt(), 0xFF3A6B2F.toInt()))
        assertTrue(hueDistance(hueOf(palette.surface), 28f) < 25f)
        assertTrue(relativeLuminance(palette.surface) < 0.08)
        assertReadable(palette)
    }

    @Test
    fun blueAndPinkAndGreenArtwork_keepTheirHue() {
        mapOf(0xFF1E5BD6.toInt() to 220f, 0xFFE85D9A.toInt() to 333f, 0xFF2E8B57.toInt() to 146f).forEach { (color, hue) ->
            val palette = LyricsPalette.fromPixels(artwork(color))
            assertTrue("hue for $hue: ${hueOf(palette.surface)}", hueDistance(hueOf(palette.surface), hue) < 20f)
            assertReadable(palette)
        }
    }

    @Test
    fun competingColours_doNotCancelOutIntoGrey() {
        // Lavender background, pink and warm skin tones (a mostly pastel cover): the strongest colour wins.
        val palette =
            LyricsPalette.fromPixels(
                artwork(0xFFC9B3E0.toInt(), 0xFFC9B3E0.toInt(), 0xFFC9B3E0.toInt(), 0xFFE07AA0.toInt(), 0xFFC98A6B.toInt(), 0xFF2A1A22.toInt()),
            )
        assertTrue("surface should carry colour", hueOf(palette.surface) >= 0f)
        assertTrue(hueDistance(hueOf(palette.surface), 270f) < 40f)
        assertReadable(palette)
    }

    @Test
    fun blackAndWhiteArtwork_givesANeutralSurface() {
        val palette = LyricsPalette.fromPixels(artwork(0xFF111111.toInt(), 0xFF777777.toInt(), 0xFFDDDDDD.toInt()))
        assertEquals(-1f, hueOf(palette.surface))
        assertReadable(palette)
    }

    @Test
    fun genuinelyLightArtwork_getsDarkText() {
        val palette = LyricsPalette.fromPixels(artwork(0xFFFFFFFF.toInt(), 0xFFF2F2F0.toInt(), 0xFFE6E6E6.toInt()))
        assertTrue(relativeLuminance(palette.surface) > 0.5)
        assertTrue(relativeLuminance(palette.content) < 0.1)
        assertReadable(palette)
    }

    @Test
    fun everyHueAndLightness_staysReadable() {
        for (hue in 0 until 360 step 15) {
            for (lightness in listOf(0.15f, 0.5f, 0.85f)) {
                assertReadable(LyricsPalette.fromPixels(artwork(hslToArgb(hue.toFloat(), 0.8f, lightness))))
            }
        }
    }

    @Test
    fun missingOrTransparentArtwork_usesTheFallback() {
        assertEquals(LyricsPalette.FALLBACK, LyricsPalette.fromPixels(IntArray(16)))
        assertReadable(LyricsPalette.FALLBACK)
    }

    @Test
    fun theSameArtworkAlwaysGivesTheSamePalette() {
        val pixels = artwork(0xFF8E44AD.toInt(), 0xFF1ABC9C.toInt())
        assertEquals(LyricsPalette.fromPixels(pixels), LyricsPalette.fromPixels(pixels.copyOf()))
    }
}
