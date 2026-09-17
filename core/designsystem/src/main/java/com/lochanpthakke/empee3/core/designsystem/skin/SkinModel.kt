package com.lochanpthakke.empee3.core.designsystem.skin

import androidx.compose.runtime.Immutable
import com.lochanpthakke.empee3.core.designsystem.widget.WidgetSkinConfig
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class LcdOverlayConfig(
    val backgroundColor: Long = 0xFF0D1B0EL,
    val backgroundTexturePath: String? = null,
    val textColor: Long = 0xFF00FF66L,
    val textGlowColor: Long = 0xFF004D1AL,
    val fontFilePath: String? = null,
    val borderStyle: String = "bevel_inset",
    val borderWidthDp: Int = 2,
    val tickerSpeedMs: Long = 150L,
)

@Immutable
@Serializable
data class SkinManifest(
    val id: String,
    val name: String,
    val author: String = "Unknown",
    val schemaVersion: Int = 1,
    val minimumSupportedAppVersion: String? = null,
    val chassisTexturePath: String? = null,
    val cdJewelCaseOverlayPath: String? = null,
    val knobSpritePath: String? = null,
    val faderThumbPath: String? = null,
    val faderTrackPath: String? = null,
    val playButtonSpritePath: String? = null,
    val pauseButtonSpritePath: String? = null,
    val lcdOverlay: LcdOverlayConfig? = null,
    val widgetConfig: WidgetSkinConfig = WidgetSkinConfig(),
)
