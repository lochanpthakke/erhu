package com.erhu.erhu.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.erhu.erhu.core.designsystem.theme.PopupGlassSurface

@Composable
internal fun LiquidMenuSurface(
    modifier: Modifier,
    shape: Shape,
    content: @Composable () -> Unit,
) {
    PopupGlassSurface(modifier = modifier, shape = shape, content = content)
}

internal fun menuDestructiveColor(
    error: Color,
    content: Color,
): Color = lerp(error, content, if (content.luminance() < 0.5f) 0.85f else 0.7f)
