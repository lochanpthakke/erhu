@file:Suppress("detekt:All")

package com.erhu.erhu.core.designsystem.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.erhu.erhu.core.designsystem.skin.SkinManifest
import com.erhu.erhu.core.designsystem.widget.WidgetSkinConfig

val LocalErhuColors = staticCompositionLocalOf { BuiltInThemes.DEFAULT_DARK.colors }
val LocalErhuShapes = staticCompositionLocalOf { BuiltInThemes.DEFAULT_DARK.shapes }
val LocalErhuSkin = staticCompositionLocalOf<SkinManifest?> { null }
val LocalErhuWidgetConfig = staticCompositionLocalOf { BuiltInThemes.DEFAULT_DARK.widgetConfig }

object ErhuTheme {
    val colors: ErhuColors
        @Composable
        @ReadOnlyComposable
        get() = LocalErhuColors.current

    val shapes: ErhuShapes
        @Composable
        @ReadOnlyComposable
        get() = LocalErhuShapes.current

    val skin: SkinManifest?
        @Composable
        @ReadOnlyComposable
        get() = LocalErhuSkin.current

    val widgetConfig: WidgetSkinConfig
        @Composable
        @ReadOnlyComposable
        get() = LocalErhuWidgetConfig.current
}

@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod")
fun ErhuTheme(
    themeManifest: ThemeManifest = BuiltInThemes.DEFAULT_DARK,
    skinManifest: SkinManifest? = null,
    glassEffectEnabled: Boolean = true,
    popupGlassOpacity: Float = 0.5f,
    content: @Composable () -> Unit,
) {
    val colors = themeManifest.colors
    val shapes = themeManifest.shapes

    val baseColorScheme =
        if (themeManifest.isDark) {
            darkColorScheme(
                primary = colors.getPrimaryColor(),
                onPrimary = colors.getOnPrimaryColor(),
                primaryContainer = colors.getPrimaryContainerColor(),
                onPrimaryContainer = colors.getOnPrimaryContainerColor(),
                secondary = colors.getSecondaryColor(),
                onSecondary = colors.getOnSecondaryColor(),
                background = colors.getBackgroundColor(),
                onBackground = colors.getOnBackgroundColor(),
                surface = colors.getSurfaceColor(),
                onSurface = colors.getOnSurfaceColor(),
                surfaceVariant = colors.getSurfaceVariantColor(),
                onSurfaceVariant = colors.getOnSurfaceVariantColor(),
                outline = colors.getOutlineColor(),
            )
        } else {
            lightColorScheme(
                primary = colors.getPrimaryColor(),
                onPrimary = colors.getOnPrimaryColor(),
                primaryContainer = colors.getPrimaryContainerColor(),
                onPrimaryContainer = colors.getOnPrimaryContainerColor(),
                secondary = colors.getSecondaryColor(),
                onSecondary = colors.getOnSecondaryColor(),
                background = colors.getBackgroundColor(),
                onBackground = colors.getOnBackgroundColor(),
                surface = colors.getSurfaceColor(),
                onSurface = colors.getOnSurfaceColor(),
                surfaceVariant = colors.getSurfaceVariantColor(),
                onSurfaceVariant = colors.getOnSurfaceVariantColor(),
                outline = colors.getOutlineColor(),
            )
        }

    // Glass is a material on top of the theme, not part of it: with the Glass
    // Effect on, floating surfaces become glass tinted by this theme's own
    // environment, and container roles are frosted. Off, the exact scheme above.
    // Skins draw their own chrome and are left alone.
    val glass =
        remember(themeManifest, skinManifest, glassEffectEnabled) {
            if (glassEffectEnabled && skinManifest == null) {
                themeManifest.glass ?: GlassTokens.derivedFrom(
                    colors,
                    themeManifest.isDark,
                    // Artwork-coloured themes get tinted glass; every other theme keeps clear glass.
                    tinted = themeManifest.id == BuiltInThemes.DOMINANT_COLOR_ID || themeManifest.id == BuiltInThemes.GRADIENT_UI_ID,
                )
            } else {
                null
            }
        }
    val colorScheme = if (glass != null) baseColorScheme.withGlassContainers(glass) else baseColorScheme

    val materialShapes =
        Shapes(
            small = RoundedCornerShape(shapes.buttonCornerRadiusDp.dp),
            medium = RoundedCornerShape(shapes.cardCornerRadiusDp.dp),
            large = RoundedCornerShape(shapes.sheetCornerRadiusDp.dp),
        )

    val popupBackdrop = rememberGlassBackdrop("popups", screenCoordinates = true)
    CompositionLocalProvider(
        LocalPopupGlassOpacity provides popupGlassOpacity,
        LocalPopupGlassBackdrop provides popupBackdrop,
        LocalErhuColors provides colors,
        LocalErhuShapes provides shapes,
        LocalErhuSkin provides skinManifest,
        LocalErhuWidgetConfig provides (skinManifest?.widgetConfig ?: themeManifest.widgetConfig),
        LocalGlassTokens provides glass,
        LocalMenuGlassTokens provides glass,
        LocalPlainColorScheme provides baseColorScheme,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = materialShapes,
        ) {
            // Always the same node, whether or not the theme is glass: branching here
            // would remount the whole app (and reset navigation) on every theme switch.
            Box(Modifier.glassBackdropSource(popupBackdrop, enabled = popupBackdrop.consumers > 0)) {
                GlassBackground(glass = glass, content = content)
            }
        }
    }
}

/** The theme's colour scheme without the glass containers: what the app uses with the Glass Effect off. */
val LocalPlainColorScheme = staticCompositionLocalOf<ColorScheme?> { null }

/**
 * [content] exactly as with the Glass Effect off, whatever the setting: no glass material (so no
 * frosting, blur or glass layers) and the theme's plain, opaque colour scheme. For screens whose
 * glass costs more than it gives (the expanded player).
 */
@Composable
fun WithoutGlass(content: @Composable () -> Unit) {
    val plain = LocalPlainColorScheme.current ?: MaterialTheme.colorScheme
    CompositionLocalProvider(LocalGlassTokens provides null) {
        MaterialTheme(colorScheme = plain, shapes = MaterialTheme.shapes, typography = MaterialTheme.typography, content = content)
    }
}

/**
 * Frosted container roles for glass themes:
 * - background is transparent so the app-wide gradient shows behind every screen;
 * - drawer/bottom sheets are strongly frosted (content scrolls behind them);
 * - dialogs and menus stay almost opaque so their text is always readable.
 */
@Suppress("MagicNumber")
private fun ColorScheme.withGlassContainers(glass: GlassTokens): ColorScheme {
    val strong = Color(glass.panelFillStrong)
    return copy(
        background = Color.Transparent,
        surfaceContainerLowest = strong.copy(alpha = 0.85f),
        surfaceContainerLow = strong.copy(alpha = 0.98f),
        surfaceContainer = strong.copy(alpha = 0.96f),
        surfaceContainerHigh = strong.copy(alpha = 0.98f),
        surfaceContainerHighest = Color(glass.panelFill).copy(alpha = 0.18f),
        secondaryContainer = primaryContainer,
        onSecondaryContainer = onPrimaryContainer,
    )
}
