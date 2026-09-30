package com.lochanpthakke.empee3.core.designsystem.theme

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class GlassThemeTest {
    private val glassTheme = BuiltInThemes.GLASSMORPHISM
    private val glass = glassTheme.glass!!

    // ---- Selection / resolution (the ids are what the existing DataStore persists)

    @Test
    fun glassIsSelectable_asAnAdditionalBuiltInTheme() {
        assertTrue(glassTheme in BuiltInThemes.ALL_BUILT_IN_THEMES)
        // Nothing replaced: the original themes are all still offered.
        listOf("default_light", "default_dark", "frosted_glass", "cyberpunk_tech").forEach { id ->
            assertTrue(id, BuiltInThemes.ALL_BUILT_IN_THEMES.any { it.id == id })
        }
    }

    @Test
    fun themeIds_areUnique() {
        val ids = BuiltInThemes.ALL_BUILT_IN_THEMES.map { it.id } + BuiltInThemes.ALL_BUILT_IN_SKINS.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun everyBuiltInTheme_resolvesToItself_withoutSkin() {
        BuiltInThemes.ALL_BUILT_IN_THEMES.forEach { theme ->
            val resolved = BuiltInThemes.resolve(theme.id)
            assertSame(theme, resolved.theme)
            assertNull(resolved.skin)
        }
    }

    @Test
    fun switchingAwayFromGlassAndBack_restoresGlassTokens() {
        assertNotNull(BuiltInThemes.resolve("glassmorphism").theme.glass)
        assertNull(BuiltInThemes.resolve("default_dark").theme.glass)
        assertNotNull(BuiltInThemes.resolve("glassmorphism").theme.glass)
    }

    @Test
    fun onlyTheGlassThemeRendersGlass_existingThemesAreUnchanged() {
        BuiltInThemes.ALL_BUILT_IN_THEMES.filter { it.id != glassTheme.id }.forEach { theme ->
            assertNull("${theme.id} must keep the classic rendering", theme.glass)
        }
    }

    @Test
    fun unknownOrBlankIds_fallBackToTheDefaultTheme() {
        assertSame(BuiltInThemes.FALLBACK_THEME, BuiltInThemes.resolve("").theme)
        assertSame(BuiltInThemes.FALLBACK_THEME, BuiltInThemes.resolve("theme_that_was_removed").theme)
    }

    @Test
    fun skinIds_resolveToFallbackThemePlusSkin() {
        val skin = BuiltInThemes.RETRO_VST_SKIN
        val resolved = BuiltInThemes.resolve(skin.id)
        assertSame(BuiltInThemes.FALLBACK_THEME, resolved.theme)
        assertSame(skin, resolved.skin)
    }

    // ---- Serialization (imported theme packages use the same ThemeManifest JSON)

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun themeJsonWithoutGlass_stillDecodes_asClassicTheme() {
        val legacy = json.encodeToString(ThemeManifest.serializer(), BuiltInThemes.DEFAULT_DARK)
        assertTrue(!legacy.contains("\"glass\""))
        assertNull(json.decodeFromString(ThemeManifest.serializer(), legacy).glass)
    }

    @Test
    fun glassTheme_roundTripsThroughJson() {
        val encoded = json.encodeToString(ThemeManifest.serializer(), glassTheme)
        assertEquals(glassTheme, json.decodeFromString(ThemeManifest.serializer(), encoded))
    }

    // ---- Readability: text must stay legible on glass over every part of the gradient

    private fun channel(c: Long, shift: Int) = ((c shr shift) and 0xFF) / 255.0

    private fun alpha(c: Long) = channel(c, 24)

    /** Source-over composite of a translucent ARGB color onto an opaque one. */
    private fun composite(
        top: Long,
        bottom: Long,
    ): DoubleArray {
        val a = alpha(top)
        return intArrayOf(16, 8, 0).map { shift -> channel(top, shift) * a + channel(bottom, shift) * (1 - a) }.toDoubleArray()
    }

    private fun rgb(c: Long) = doubleArrayOf(channel(c, 16), channel(c, 8), channel(c, 0))

    private fun luminance(rgb: DoubleArray): Double {
        val lin = rgb.map { if (it <= 0.03928) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
        return 0.2126 * lin[0] + 0.7152 * lin[1] + 0.0722 * lin[2]
    }

    private fun contrast(
        a: DoubleArray,
        b: DoubleArray,
    ): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    @Test
    fun primaryText_meetsWcagAA_onGlassPanels_overTheWholeGradient() {
        val text = rgb(glassTheme.colors.onSurface)
        for (stop in glass.backgroundGradient) {
            for (fill in listOf(glass.panelFill, glass.panelFillStrong, glassTheme.colors.surfaceVariant)) {
                val ratio = contrast(text, composite(fill, stop))
                assertTrue("onSurface on ${fill.toString(16)} over ${stop.toString(16)}: $ratio", ratio >= 4.5)
            }
        }
    }

    @Test
    fun secondaryText_andAccent_stayReadable() {
        for (stop in glass.backgroundGradient) {
            val panel = composite(glass.panelFill, stop)
            val secondary = contrast(rgb(glassTheme.colors.onSurfaceVariant), panel)
            val accent = contrast(rgb(glassTheme.colors.primary), panel)
            assertTrue("onSurfaceVariant: $secondary", secondary >= 4.5)
            assertTrue("primary accent: $accent", accent >= 3.0)
        }
    }

    @Test
    fun artworkBackdropScrim_keepsTextReadable_evenOnWhiteArtwork() {
        val worstCaseArtwork = 0xFFFFFFFFL
        val ratio = contrast(rgb(glassTheme.colors.onSurface), composite(glass.artworkScrim, worstCaseArtwork))
        assertTrue("text over scrimmed white artwork: $ratio", ratio >= 3.0)
    }
}
