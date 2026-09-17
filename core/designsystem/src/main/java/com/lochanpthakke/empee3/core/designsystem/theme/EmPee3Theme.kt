package com.lochanpthakke.empee3.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
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
fun EmPee3Theme(
    themeManifest: ThemeManifest = BuiltInThemes.DEFAULT_DARK,
    skinManifest: SkinManifest? = null,
    content: @Composable () -> Unit,
) {
    val colors = themeManifest.colors
    val shapes = themeManifest.shapes

    val colorScheme = if (themeManifest.isDark) {
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

    val materialShapes = Shapes(
        small = RoundedCornerShape(shapes.buttonCornerRadiusDp.dp),
        medium = RoundedCornerShape(shapes.cardCornerRadiusDp.dp),
        large = RoundedCornerShape(shapes.sheetCornerRadiusDp.dp),
    )

    CompositionLocalProvider(
        LocalEmPee3Colors provides colors,
        LocalEmPee3Shapes provides shapes,
        LocalEmPee3Skin provides skinManifest,
        LocalEmPee3WidgetConfig provides (skinManifest?.widgetConfig ?: themeManifest.widgetConfig),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = materialShapes,
            content = content,
        )
    }
}
