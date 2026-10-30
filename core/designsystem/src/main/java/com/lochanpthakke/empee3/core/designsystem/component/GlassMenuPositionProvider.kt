package com.lochanpthakke.empee3.core.designsystem.component

import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.PopupPositionProvider

/** Window-relative positioning; prefers the trigger's trailing edge, then flips/clamps at edges. */
internal class GlassMenuPositionProvider(
    private val margin: Int,
    private val gap: Int,
    private val contentInset: Int = 0,
    private val onPosition: (TransformOrigin) -> Unit = {},
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val width = popupContentSize.width
        val height = popupContentSize.height
        val preferredX = if (layoutDirection == LayoutDirection.Ltr) anchorBounds.right - width else anchorBounds.left
        val x = fit(preferredX, width, windowSize.width)
        val below = anchorBounds.bottom + gap
        val above = anchorBounds.top - gap - height
        val preferredY =
            when {
                below + height <= windowSize.height - margin -> below
                above >= margin -> above
                else -> anchorBounds.top
            }
        val y = fit(preferredY, height, windowSize.height)
        onPosition(
            TransformOrigin(
                pivotFractionX = fraction(anchorBounds.center.x - x - contentInset, width - 2 * contentInset),
                pivotFractionY = fraction(anchorBounds.center.y - y - contentInset, height - 2 * contentInset),
            ),
        )
        return IntOffset(x, y)
    }

    private fun fit(
        preferred: Int,
        content: Int,
        window: Int,
    ): Int {
        val inset = margin.coerceAtMost(((window - content) / 2).coerceAtLeast(0))
        return preferred.coerceIn(inset, (window - content - inset).coerceAtLeast(inset))
    }

    private fun fraction(
        value: Int,
        size: Int,
    ): Float = if (size > 0) (value.toFloat() / size).coerceIn(0f, 1f) else 0.5f
}
