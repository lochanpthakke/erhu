@file:Suppress("detekt:All")

package com.lochanpthakke.empee3.core.designsystem.theme

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lochanpthakke.empee3.core.designsystem.skin.SkinManifest
import com.lochanpthakke.empee3.core.designsystem.widget.WidgetSkinConfig

val LocalEmPee3Colors = staticCompositionLocalOf { BuiltInThemes.DEFAULT_DARK.colors }
val LocalEmPee3Shapes = staticCompositionLocalOf { BuiltInThemes.DEFAULT_DARK.shapes }
val LocalEmPee3Skin = staticCompositionLocalOf<SkinManifest?> { null }
val LocalEmPee3WidgetConfig = staticCompositionLocalOf { BuiltInThemes.DEFAULT_DARK.widgetConfig }

object EmPee3Theme {
    val colors: EmPee3Colors
        @Composable
        @ReadOnlyComposable
        get() = LocalEmPee3Colors.current

    val shapes: EmPee3Shapes
        @Composable
        @ReadOnlyComposable
        get() = LocalEmPee3Shapes.current

    val skin: SkinManifest?
        @Composable
        @ReadOnlyComposable
        get() = LocalEmPee3Skin.current

    val widgetConfig: WidgetSkinConfig
        @Composable
        @ReadOnlyComposable
        get() = LocalEmPee3WidgetConfig.current
}

@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod")
fun EmPee3Theme(
    themeManifest: ThemeManifest = BuiltInThemes.DEFAULT_DARK,
    skinManifest: SkinManifest? = null,
    glassEffectEnabled: Boolean = false,
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
                themeManifest.glass ?: GlassTokens.derivedFrom(colors, themeManifest.isDark)
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

    CompositionLocalProvider(
        LocalEmPee3Colors provides colors,
        LocalEmPee3Shapes provides shapes,
        LocalEmPee3Skin provides skinManifest,
        LocalEmPee3WidgetConfig provides (skinManifest?.widgetConfig ?: themeManifest.widgetConfig),
        LocalGlassTokens provides glass,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = materialShapes,
        ) {
            // Always the same node, whether or not the theme is glass: branching here
            // would remount the whole app (and reset navigation) on every theme switch.
            GlassBackground(glass = glass, content = content)
        }
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
