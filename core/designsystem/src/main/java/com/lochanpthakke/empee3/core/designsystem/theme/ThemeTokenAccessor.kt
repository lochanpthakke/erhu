package com.lochanpthakke.empee3.core.designsystem.theme

import com.lochanpthakke.empee3.core.designsystem.skin.SkinManifest
import com.lochanpthakke.empee3.core.designsystem.widget.WidgetLayoutPreset
import com.lochanpthakke.empee3.core.designsystem.widget.WidgetSkinConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThemeTokenAccessor @Inject constructor() {

    fun resolveColorsWithFallback(
        customColors: EmPee3Colors?,
        fallbackColors: EmPee3Colors,
    ): EmPee3Colors {
        if (customColors == null) return fallbackColors
        return customColors.copy(
            primary = customColors.primary.takeIf { it != 0L } ?: fallbackColors.primary,
            onPrimary = customColors.onPrimary.takeIf { it != 0L } ?: fallbackColors.onPrimary,
            background = customColors.background.takeIf { it != 0L } ?: fallbackColors.background,
            onBackground = customColors.onBackground.takeIf { it != 0L } ?: fallbackColors.onBackground,
            surface = customColors.surface.takeIf { it != 0L } ?: fallbackColors.surface,
            onSurface = customColors.onSurface.takeIf { it != 0L } ?: fallbackColors.onSurface,
            cardBackground = customColors.cardBackground.takeIf { it != 0L } ?: fallbackColors.cardBackground,
            lcdBackground = customColors.lcdBackground.takeIf { it != 0L } ?: fallbackColors.lcdBackground,
            lcdText = customColors.lcdText.takeIf { it != 0L } ?: fallbackColors.lcdText,
        )
    }

    fun resolveShapesWithFallback(
        customShapes: EmPee3Shapes?,
        fallbackShapes: EmPee3Shapes,
    ): EmPee3Shapes {
        if (customShapes == null) return fallbackShapes
        return customShapes.copy(
            cardCornerRadiusDp = customShapes.cardCornerRadiusDp.takeIf { it >= 0 } ?: fallbackShapes.cardCornerRadiusDp,
            buttonCornerRadiusDp = customShapes.buttonCornerRadiusDp.takeIf { it >= 0 } ?: fallbackShapes.buttonCornerRadiusDp,
            sheetCornerRadiusDp = customShapes.sheetCornerRadiusDp.takeIf { it >= 0 } ?: fallbackShapes.sheetCornerRadiusDp,
            miniPlayerCornerRadiusDp = customShapes.miniPlayerCornerRadiusDp.takeIf { it >= 0 } ?: fallbackShapes.miniPlayerCornerRadiusDp,
        )
    }

    fun resolveWidgetConfigWithFallback(
        customConfig: WidgetSkinConfig?,
        fallbackConfig: WidgetSkinConfig,
    ): WidgetSkinConfig {
        if (customConfig == null) return fallbackConfig
        return customConfig.copy(
            layoutPreset = customConfig.layoutPreset ?: fallbackConfig.layoutPreset,
            artXPaddingDp = customConfig.artXPaddingDp.takeIf { it >= 0 } ?: fallbackConfig.artXPaddingDp,
            artYPaddingDp = customConfig.artYPaddingDp.takeIf { it >= 0 } ?: fallbackConfig.artYPaddingDp,
        )
    }
}
