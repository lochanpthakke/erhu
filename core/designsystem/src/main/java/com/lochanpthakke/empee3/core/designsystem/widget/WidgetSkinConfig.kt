@file:Suppress("detekt:All")

package com.lochanpthakke.empee3.core.designsystem.widget

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
enum class WidgetLayoutPreset {
    BOTTOM_CONTROLS_CENTER,
    CENTER_OVERLAY_CONTROLS,
    RIGHT_STACKED_CONTROLS,
    COMPACT_ROW,
}

@Immutable
@Serializable
@Suppress("LongParameterList")
data class WidgetSkinConfig(
    val layoutPreset: WidgetLayoutPreset = WidgetLayoutPreset.BOTTOM_CONTROLS_CENTER,
    val chassisTexturePath: String? = null,
    val showTrackTitle: Boolean = true,
    val artXPaddingDp: Int = 12,
    val artYPaddingDp: Int = 12,
    val playButtonIconPath: String? = null,
    val pauseButtonIconPath: String? = null,
    val prevButtonIconPath: String? = null,
    val nextButtonIconPath: String? = null,
)
