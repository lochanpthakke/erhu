@file:Suppress("detekt:All")

package com.erhu.erhu.core.designsystem.theme

import androidx.compose.runtime.Immutable
import com.erhu.erhu.core.designsystem.widget.WidgetSkinConfig
import kotlinx.serialization.Serializable

@Immutable
@Serializable
@Suppress("LongParameterList")
data class ThemeManifest(
    val id: String,
    val name: String,
    val author: String = "Unknown",
    val schemaVersion: Int = 1,
    val minimumSupportedAppVersion: String? = null,
    val isDark: Boolean = true,
    val colors: ErhuColors,
    val shapes: ErhuShapes = ErhuShapes(),
    val fontFilePath: String? = null,
    val widgetConfig: WidgetSkinConfig = WidgetSkinConfig(),
    /** Present only for glassmorphism themes; null keeps the classic rendering. */
    val glass: GlassTokens? = null,
)
