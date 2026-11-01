package com.erhu.erhu.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.erhu.erhu.core.designsystem.theme.ErhuGlass
import com.erhu.erhu.core.designsystem.theme.LocalPopupGlassOpacity
import kotlin.math.abs

/*
 * A liquid-glass selection capsule for a row of destinations (the library sections): a soft,
 * milky frosted pill that encloses the selected item (icon AND label), slides and resizes between
 * the items' MEASURED places ([CapsuleSlots]), and can be dragged from one item to another
 * ([capsuleDrag]). Restrained like system glass tabs: the colour lives in the selected icon and
 * label; the pill itself is light, a brighter frosted fill with a hairline highlight on its top
 * edge, no outline ring and no glow.
 *
 * One value drives it: the fractional position (0 = first item, 1 = second...). The selection
 * animates it; a drag moves it with the finger; the items' emphasis is read from it too (see
 * [capsuleEmphasis]), so the highlighted label always travels with the capsule. All of it is read
 * in the draw phase: moving the capsule never recomposes the row.
 */

/** Room around the content inside the capsule (it fills its cell), and its margin to the cell's edges. */
internal val CAPSULE_PADDING = 24.dp
internal val CAPSULE_INSET = 2.dp

/** The capsule stretches along its motion with speed (positions per second) and gives back height. */
private const val STRETCH_PER_VELOCITY = 0.025f
private const val MAX_STRETCH = 0.1f
private const val SQUASH_RATIO = 0.35f

/** Neutral glass sheen; accent warmth is reserved for the non-glass fallback. */
private const val FILL_TOP_ALPHA = 0.22f
private const val FILL_BOTTOM_ALPHA = 0.12f
private const val FILL_WARMTH = 0.12f

/** The hairline edge: catches light along the top, fades down the sides, nearly gone at the bottom. */
private val EDGE_WIDTH = 1.dp
private const val EDGE_TOP_ALPHA = 0.32f
private const val EDGE_BOTTOM_ALPHA = 0.05f

/** Moving, the glass brightens a little with its speed (positions per second), up to this. */
private const val LIGHT_PER_VELOCITY = 0.02f
private const val MAX_LIGHT = 0.08f

/**
 * The capsule, drawn over the row (give it the row's size). [accent] applies only when glass is disabled; glass itself stays neutral;
 * [visibility] (0..1, read while drawing) fades it, e.g. while no item is current.
 */
@Composable
fun LiquidCapsule(
    position: Animatable<Float, AnimationVector1D>,
    slots: CapsuleSlots,
    accent: Color,
    modifier: Modifier = Modifier,
    visibility: () -> Float = { 1f },
) {
    val glass = ErhuGlass.isActive
    val strength = LocalPopupGlassOpacity.current.coerceIn(0f, 1f)
    Box(
        modifier =
            modifier.drawWithCache {
                val padding = CAPSULE_PADDING.toPx()
                val inset = CAPSULE_INSET.toPx()
                val frost = if (glass) Color.White else lerp(Color.White, accent, FILL_WARMTH)
                onDrawBehind {
                    val alpha = visibility().coerceIn(0f, 1f)
                    val rect = slots.capsuleBetween(position.value, padding, inset)
                    if (alpha <= 0f || rect == null) return@onDrawBehind
                    val speed = abs(position.velocity)
                    val stretch = (speed * STRETCH_PER_VELOCITY).coerceAtMost(MAX_STRETCH)
                    val light = (speed * LIGHT_PER_VELOCITY).coerceAtMost(MAX_LIGHT)
                    withTransform({ scale(1f + stretch, 1f - stretch * SQUASH_RATIO, pivot = rect.center) }) {
                        drawFrost(rect, frost, alpha * if (glass) (0.8f - strength * 0.6f) else 1f, light)
                        drawEdge(rect, alpha)
                    }
                }
            },
    )
}

private fun DrawScope.drawFrost(
    rect: Rect,
    frost: Color,
    alpha: Float,
    light: Float,
) {
    drawRoundRect(
        brush =
            Brush.verticalGradient(
                0f to frost.copy(alpha = (FILL_TOP_ALPHA + light) * alpha),
                1f to frost.copy(alpha = (FILL_BOTTOM_ALPHA + light) * alpha),
                startY = rect.top,
                endY = rect.bottom,
            ),
        topLeft = rect.topLeft,
        size = rect.size,
        cornerRadius = CornerRadius(rect.height / 2f),
    )
}

private fun DrawScope.drawEdge(
    rect: Rect,
    alpha: Float,
) {
    val width = EDGE_WIDTH.toPx()
    val inner = rect.deflate(width / 2f)
    drawRoundRect(
        brush =
            Brush.verticalGradient(
                0f to Color.White.copy(alpha = EDGE_TOP_ALPHA * alpha),
                0.5f to Color.White.copy(alpha = (EDGE_TOP_ALPHA + EDGE_BOTTOM_ALPHA) / 2f * alpha),
                1f to Color.White.copy(alpha = EDGE_BOTTOM_ALPHA * alpha),
                startY = inner.top,
                endY = inner.bottom,
            ),
        topLeft = inner.topLeft,
        size = inner.size,
        cornerRadius = CornerRadius(inner.height / 2f),
        style = Stroke(width),
    )
}
