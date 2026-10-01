@file:Suppress("detekt:All")

package com.lochanpthakke.empee3.core.designsystem.theme

import com.lochanpthakke.empee3.core.designsystem.skin.LcdOverlayConfig
import com.lochanpthakke.empee3.core.designsystem.skin.SkinManifest
import com.lochanpthakke.empee3.core.designsystem.widget.WidgetLayoutPreset
import com.lochanpthakke.empee3.core.designsystem.widget.WidgetSkinConfig

@Suppress("VariableNaming", "MagicNumber", "LongMethod")
object BuiltInThemes {
    val DEFAULT_LIGHT =
        ThemeManifest(
            id = "default_light",
            name = "Default Light",
            author = "empee3",
            isDark = false,
            colors =
                EmPee3Colors(
                    primary = 0xFF121316L,
                    onPrimary = 0xFFFFFFFFL,
                    primaryContainer = 0xFFE2E4E8L,
                    onPrimaryContainer = 0xFF121316L,
                    secondary = 0xFF4A5568L,
                    onSecondary = 0xFFFFFFFFL,
                    background = 0xFFF4F5F8L,
                    onBackground = 0xFF121316L,
                    surface = 0xFFFFFFFFL,
                    onSurface = 0xFF121316L,
                    surfaceVariant = 0xFFE2E8F0L,
                    onSurfaceVariant = 0xFF4A5568L,
                    outline = 0xFFCBD5E0L,
                    bevelLight = 0xFFFFFFFFL,
                    bevelDark = 0xFFA0AEC0L,
                    lcdBackground = 0xFFE2E8F0L,
                    lcdText = 0xFF121316L,
                    lcdGlow = 0xFF718096L,
                    cardBackground = 0xFFFFFFFFL,
                    glassScrim = 0xCCFFFFFFL,
                ),
            shapes =
                EmPee3Shapes(
                    cardCornerRadiusDp = 16,
                    buttonCornerRadiusDp = 24,
                    sheetCornerRadiusDp = 28,
                    miniPlayerCornerRadiusDp = 16,
                    borderWidthDp = 1,
                    isRetroBevelEnabled = false,
                ),
            widgetConfig =
                WidgetSkinConfig(
                    layoutPreset = WidgetLayoutPreset.BOTTOM_CONTROLS_CENTER,
                ),
        )

    val DEFAULT_DARK =
        ThemeManifest(
            id = "default_dark",
            name = "Default Dark",
            author = "empee3",
            isDark = true,
            colors =
                EmPee3Colors(
                    primary = 0xFFF8F9FAL,
                    onPrimary = 0xFF101216L,
                    primaryContainer = 0xFF2D3748L,
                    onPrimaryContainer = 0xFFF8F9FAL,
                    secondary = 0xFFA0AEC0L,
                    onSecondary = 0xFF101216L,
                    background = 0xFF101216L,
                    onBackground = 0xFFF8F9FAL,
                    surface = 0xFF1E222AL,
                    onSurface = 0xFFF8F9FAL,
                    surfaceVariant = 0xFF2D3748L,
                    onSurfaceVariant = 0xFFA0AEC0L,
                    outline = 0xFF4A5568L,
                    bevelLight = 0xFF4A5568L,
                    bevelDark = 0xFF0A0C0EL,
                    lcdBackground = 0xFF1E222AL,
                    lcdText = 0xFFF8F9FAL,
                    lcdGlow = 0xFF4A5568L,
                    cardBackground = 0xFF1E222AL,
                    glassScrim = 0x99101216L,
                ),
            shapes =
                EmPee3Shapes(
                    cardCornerRadiusDp = 16,
                    buttonCornerRadiusDp = 24,
                    sheetCornerRadiusDp = 28,
                    miniPlayerCornerRadiusDp = 16,
                    borderWidthDp = 1,
                    isRetroBevelEnabled = false,
                ),
            widgetConfig =
                WidgetSkinConfig(
                    layoutPreset = WidgetLayoutPreset.COMPACT_ROW,
                ),
        )

    val FROSTED_GLASS =
        ThemeManifest(
            id = "frosted_glass",
            name = "Frosted Glass",
            author = "empee3",
            isDark = true,
            colors =
                EmPee3Colors(
                    primary = 0xFF6366F1L,
                    onPrimary = 0xFFFFFFFFL,
                    primaryContainer = 0xFF4338CAL,
                    onPrimaryContainer = 0xFFEEF2FFL,
                    secondary = 0xFF818CF8L,
                    onSecondary = 0xFFFFFFFFL,
                    background = 0xFF0F172AL,
                    onBackground = 0xFFF8FAFCL,
                    surface = 0x801E293BL,
                    onSurface = 0xFFF8FAFCL,
                    surfaceVariant = 0x80334155L,
                    onSurfaceVariant = 0xFF94A3B8L,
                    outline = 0x4094A3B8L,
                    bevelLight = 0x80FFFFFFL,
                    bevelDark = 0x80000000L,
                    lcdBackground = 0x801E293BL,
                    lcdText = 0xFF38BDF8L,
                    lcdGlow = 0x800284C7L,
                    cardBackground = 0x661E293BL,
                    glassScrim = 0x4D0F172AL,
                ),
            shapes =
                EmPee3Shapes(
                    cardCornerRadiusDp = 20,
                    buttonCornerRadiusDp = 28,
                    sheetCornerRadiusDp = 32,
                    miniPlayerCornerRadiusDp = 20,
                    borderWidthDp = 1,
                    isRetroBevelEnabled = false,
                ),
            widgetConfig =
                WidgetSkinConfig(
                    layoutPreset = WidgetLayoutPreset.BOTTOM_CONTROLS_CENTER,
                ),
        )

    val CYBERPUNK_TECH =
        ThemeManifest(
            id = "cyberpunk_tech",
            name = "Cyberpunk Tech",
            author = "empee3",
            isDark = true,
            colors =
                EmPee3Colors(
                    primary = 0xFFE0A15EL,
                    onPrimary = 0xFF0A0B0EL,
                    primaryContainer = 0xFF3D2C15L,
                    onPrimaryContainer = 0xFFE0A15EL,
                    secondary = 0xFF7E9A82L,
                    onSecondary = 0xFF0A0B0EL,
                    background = 0xFF0A0B0EL,
                    onBackground = 0xFFE2E8F0L,
                    surface = 0xFF14171FL,
                    onSurface = 0xFFE2E8F0L,
                    surfaceVariant = 0xFF1F2430L,
                    onSurfaceVariant = 0xFF94A3B8L,
                    outline = 0xFFE0A15EL,
                    bevelLight = 0xFFE0A15EL,
                    bevelDark = 0xFF000000L,
                    lcdBackground = 0xFF14171FL,
                    lcdText = 0xFFE0A15EL,
                    lcdGlow = 0xFF5C3F18L,
                    cardBackground = 0xFF14171FL,
                    glassScrim = 0xCC0A0B0EL,
                ),
            shapes =
                EmPee3Shapes(
                    cardCornerRadiusDp = 2,
                    buttonCornerRadiusDp = 2,
                    sheetCornerRadiusDp = 4,
                    miniPlayerCornerRadiusDp = 2,
                    borderWidthDp = 2,
                    isRetroBevelEnabled = true,
                ),
            widgetConfig =
                WidgetSkinConfig(
                    layoutPreset = WidgetLayoutPreset.RIGHT_STACKED_CONTROLS,
                ),
        )

    val RETRO_VST_SKIN =
        SkinManifest(
            id = "retro_vst_skin",
            name = "3D Retro VST & CD",
            author = "empee3",
            chassisTexturePath = "textures/vst_metal_chassis.png",
            cdJewelCaseOverlayPath = "textures/cd_jewel_case_overlay.png",
            knobSpritePath = "sprites/knob_dial.png",
            faderThumbPath = "sprites/fader_thumb.png",
            faderTrackPath = "sprites/fader_track.png",
            lcdOverlay =
                LcdOverlayConfig(
                    backgroundColor = 0xFF0D1B0EL,
                    textColor = 0xFF00FF66L,
                    textGlowColor = 0xFF004D1AL,
                    borderStyle = "bevel_inset",
                    borderWidthDp = 2,
                    tickerSpeedMs = 150L,
                ),
            widgetConfig =
                WidgetSkinConfig(
                    layoutPreset = WidgetLayoutPreset.CENTER_OVERLAY_CONTROLS,
                    artXPaddingDp = 16,
                    artYPaddingDp = 12,
                ),
        )

    val ALL_BUILT_IN_THEMES = listOf(DEFAULT_LIGHT, DEFAULT_DARK, FROSTED_GLASS, CYBERPUNK_TECH)
    val ALL_BUILT_IN_SKINS = listOf(RETRO_VST_SKIN)

    /** Theme used when a stored id is blank or unknown (the app's long-standing default). */
    val FALLBACK_THEME: ThemeManifest get() = CYBERPUNK_TECH

    /**
     * App theme selection that follows the device's light/dark setting. It is not
     * a palette of its own: it shows [DEFAULT_LIGHT] or [DEFAULT_DARK], chosen at
     * runtime with [forSystemAppearance].
     */
    const val SYSTEM_DEFAULT_ID = "system_default"

    /**
     * Id of the removed Glassmorphism theme. Glass is now a separate Glass Effect
     * setting; stored selections of this id are migrated to [SYSTEM_DEFAULT_ID].
     */
    const val LEGACY_GLASSMORPHISM_ID = "glassmorphism"

    /** The theme System Default shows for the device's current appearance. */
    fun forSystemAppearance(isSystemDark: Boolean): ThemeManifest = if (isSystemDark) DEFAULT_DARK else DEFAULT_LIGHT

    /**
     * Resolves a persisted theme/skin id: a built-in theme, or a built-in skin
     * (rendered on top of the fallback theme), or the fallback theme.
     * System Default (and the legacy Glassmorphism id) resolve to Default Dark here.
     */
    fun resolve(id: String): ResolvedTheme {
        // Without a UI context (widget, rollback) the device appearance is unknown: use Dark.
        if (id == SYSTEM_DEFAULT_ID || id == LEGACY_GLASSMORPHISM_ID) return ResolvedTheme(DEFAULT_DARK, null)
        ALL_BUILT_IN_THEMES.find { it.id == id }?.let { return ResolvedTheme(it, null) }
        ALL_BUILT_IN_SKINS.find { it.id == id }?.let { return ResolvedTheme(FALLBACK_THEME, it) }
        return ResolvedTheme(FALLBACK_THEME, null)
    }
}

data class ResolvedTheme(
    val theme: ThemeManifest,
    val skin: SkinManifest?,
)
