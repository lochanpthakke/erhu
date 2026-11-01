@file:Suppress("detekt:All")

package com.erhu.erhu.core.designsystem.theme

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
@Suppress("LongParameterList")
data class ErhuShapes(
    val cardCornerRadiusDp: Int = 16,
    val buttonCornerRadiusDp: Int = 24,
    val sheetCornerRadiusDp: Int = 28,
    val miniPlayerCornerRadiusDp: Int = 16,
    val borderWidthDp: Int = 1,
    val isRetroBevelEnabled: Boolean = false,
)
