@file:Suppress("detekt:All")

package com.lochanpthakke.empee3.core.designsystem.theme

import com.lochanpthakke.empee3.core.designsystem.skin.SkinManifest
import com.lochanpthakke.empee3.core.designsystem.widget.WidgetLayoutPreset
import com.lochanpthakke.empee3.core.designsystem.widget.WidgetSkinConfig

@Suppress("VariableNaming", "MagicNumber", "LongMethod")
object BuiltInThemes {
    val DEFAULT_LIGHT =
        ThemeManifest(
            id = "default_light",
            name = "Light",
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
            name = "Dark",
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

    val ALL_BUILT_IN_THEMES = listOf(DEFAULT_LIGHT, DEFAULT_DARK)
    val ALL_BUILT_IN_SKINS = emptyList<SkinManifest>()

    /** Theme used when a stored id is blank or unknown (the app's long-standing default). */
    val FALLBACK_THEME: ThemeManifest get() = DEFAULT_DARK

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
