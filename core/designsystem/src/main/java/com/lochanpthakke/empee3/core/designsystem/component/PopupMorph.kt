package com.lochanpthakke.empee3.core.designsystem.component

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

internal fun popupMorphBounds(
    size: Size,
    origin: TransformOrigin,
    progress: Float,
    compactPx: Float,
): Rect {
    val fraction = progress.coerceIn(0f, 1f)
    val width = compactPx.coerceAtMost(size.width) + (size.width - compactPx.coerceAtMost(size.width)) * fraction
    val height = compactPx.coerceAtMost(size.height) + (size.height - compactPx.coerceAtMost(size.height)) * fraction
    val x = (size.width - width) * origin.pivotFractionX
    val y = (size.height - height) * origin.pivotFractionY
    return Rect(x, y, x + width, y + height)
}

/** Reads progress in drawing: no row remeasure, stretched text, or per-frame composition. */
internal class PopupMorphShape(
    private val origin: () -> TransformOrigin,
    private val progress: () -> Float,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val fraction = progress().coerceIn(0f, 1f)
        val bounds = popupMorphBounds(size, origin(), fraction, with(density) { 44.dp.toPx() })
        val corner = with(density) { (22f + fraction * 6f).dp.toPx() }
        return Outline.Rounded(RoundRect(bounds, CornerRadius(corner.coerceAtMost(bounds.size.minDimension / 2f))))
    }
}
