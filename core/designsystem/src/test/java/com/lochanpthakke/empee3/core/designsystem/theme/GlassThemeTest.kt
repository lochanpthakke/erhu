package com.lochanpthakke.empee3.core.designsystem.theme

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Glass is a material (the Glass Effect setting), not a theme: no built-in theme
 * carries glass tokens, and the tokens derived for a theme keep its environment.
 */
class GlassThemeTest {
    // ---- Theme list / resolution (the ids are what the existing DataStore persists)

    @Test
    fun glassmorphismIsNoLongerATheme_existingThemesRemain() {
        assertFalse(BuiltInThemes.ALL_BUILT_IN_THEMES.any { it.id == BuiltInThemes.LEGACY_GLASSMORPHISM_ID })
        listOf("default_light", "default_dark", "frosted_glass", "cyberpunk_tech").forEach { id ->
            assertTrue(id, BuiltInThemes.ALL_BUILT_IN_THEMES.any { it.id == id })
        }
    }

    @Test
    fun themeIds_areUnique_andDoNotCollideWithSystemDefault() {
        val ids = BuiltInThemes.ALL_BUILT_IN_THEMES.map { it.id } + BuiltInThemes.ALL_BUILT_IN_SKINS.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertFalse(BuiltInThemes.SYSTEM_DEFAULT_ID in ids)
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
    fun noBuiltInThemeCarriesGlass_glassComesFromTheSetting() {
        BuiltInThemes.ALL_BUILT_IN_THEMES.forEach { theme -> assertNull(theme.id, theme.glass) }
    }

    @Test
    fun systemDefault_followsTheDeviceAppearance() {
        assertSame(BuiltInThemes.DEFAULT_DARK, BuiltInThemes.forSystemAppearance(isSystemDark = true))
        assertSame(BuiltInThemes.DEFAULT_LIGHT, BuiltInThemes.forSystemAppearance(isSystemDark = false))
    }

    @Test
    fun systemDefaultAndLegacyGlassIds_resolveSafely_withoutAUiContext() {
        assertSame(BuiltInThemes.DEFAULT_DARK, BuiltInThemes.resolve(BuiltInThemes.SYSTEM_DEFAULT_ID).theme)
        assertSame(BuiltInThemes.DEFAULT_DARK, BuiltInThemes.resolve(BuiltInThemes.LEGACY_GLASSMORPHISM_ID).theme)
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

    // ---- Derived glass keeps the theme's environment

    private fun derived(theme: ThemeManifest) = GlassTokens.derivedFrom(theme.colors, theme.isDark)

    @Test
    fun derivedGlass_keepsTheThemeBackground_withoutGlowsOrExtraColour() {
        BuiltInThemes.ALL_BUILT_IN_THEMES.forEach { theme ->
            val glass = derived(theme)
            val opaqueBackground = theme.colors.background or 0xFF000000L
            assertTrue(theme.id, glass.backgroundGradient.all { it == opaqueBackground })
            assertEquals(theme.id, 0L, glass.glowPrimary ushr 24)
            assertEquals(theme.id, 0L, glass.glowSecondary ushr 24)
            // Floating panels are the theme's own surface colour, made translucent.
            assertEquals(theme.id, theme.colors.surface and 0xFFFFFFL, glass.panelFillStrong and 0xFFFFFFL)
            assertTrue(theme.id, (glass.panelFillStrong ushr 24) < 0xFF)
        }
    }

    // ---- Serialization (imported theme packages use the same ThemeManifest JSON)

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun themeJsonWithoutGlass_stillDecodes() {
        val encoded = json.encodeToString(ThemeManifest.serializer(), BuiltInThemes.DEFAULT_DARK)
        assertTrue(!encoded.contains("\"glass\""))
        assertNull(json.decodeFromString(ThemeManifest.serializer(), encoded).glass)
    }

    // ---- Readability: text must stay legible on derived glass for every theme

    private fun channel(
        c: Long,
        shift: Int,
    ) = ((c shr shift) and 0xFF) / 255.0

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
    fun primaryText_meetsWcagAA_onDerivedGlassPanels_forEveryTheme() {
        BuiltInThemes.ALL_BUILT_IN_THEMES.forEach { theme ->
            val glass = derived(theme)
            val background = glass.backgroundGradient.first()
            val text = rgb(theme.colors.onSurface)
            for (fill in listOf(glass.panelFill, glass.panelFillStrong)) {
                val ratio = contrast(text, composite(fill, background))
                assertTrue("${theme.id}: onSurface on ${fill.toString(16)}: $ratio", ratio >= 4.5)
            }
        }
    }

    @Test
    fun artworkBackdropScrim_keepsTextReadable_onWorstCaseArtwork() {
        BuiltInThemes.ALL_BUILT_IN_THEMES.forEach { theme ->
            val glass = derived(theme)
            // Light text is hardest on white artwork, dark text on black artwork.
            val worstCaseArtwork = if (theme.isDark) 0xFFFFFFFFL else 0xFF000000L
            val ratio = contrast(rgb(theme.colors.onSurface), composite(glass.artworkScrim, worstCaseArtwork))
            assertTrue("${theme.id}: text over scrimmed artwork: $ratio", ratio >= 3.0)
        }
    }
}
