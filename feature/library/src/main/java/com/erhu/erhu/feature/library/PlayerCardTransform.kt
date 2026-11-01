package com.erhu.erhu.feature.library

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.erhu.erhu.core.designsystem.foundation.Radius

private const val NEIGHBOUR_REVEAL_START = 0.25f
private const val NEIGHBOUR_REVEAL_SPAN = 0.7f
private const val MIN_CARD_SCALE = 0.01f
private val MINI_ARTWORK_CORNER = 8.dp

/** A uniform transform of the complete card; artwork and metadata never have separate paths. */
internal data class PlayerCardTransform(
    val scale: Float,
    val translation: Offset,
)

internal fun playerCardTransform(
    progress: Float,
    viewport: Rect,
    artwork: Rect,
    thumbnail: Rect,
): PlayerCardTransform {
    if (artwork.width <= 0f || thumbnail.width <= 0f) return PlayerCardTransform(1f, Offset.Zero)
    val p = progress.coerceIn(0f, 1f)
    val scale = lerp(thumbnail.width / artwork.width, 1f, p)
    val target = lerp(thumbnail.topLeft, artwork.topLeft, p)
    return PlayerCardTransform(scale, target - viewport.topLeft - (artwork.topLeft - viewport.topLeft) * scale)
}

/** Neighbours unfold only after the anchor has started growing, on the same reversible timeline. */
internal fun playerNeighbourReveal(progress: Float): Float {
    val t = ((progress - NEIGHBOUR_REVEAL_START) / NEIGHBOUR_REVEAL_SPAN).coerceIn(0f, 1f)
    return t * t * (1f + 2f * (1f - t))
}

/** The whole carousel travels in one lightweight layer, around its untransformed layout. */
internal fun Modifier.playerCardTravel(state: PlayerTransitionState?): Modifier =
    graphicsLayer {
        if (state != null) {
            val transform = playerCardTransform(state.fraction, state.carouselBounds, state.cardArtworkBounds, state.miniArtworkBounds)
            transformOrigin = TransformOrigin(0f, 0f)
            translationX = transform.translation.x
            translationY = transform.translation.y
            scaleX = transform.scale
            scaleY = transform.scale
        }
    }

/** Match the mini thumbnail's visual corner despite the uniform scale of its parent card. */
internal fun Modifier.playerArtworkClip(state: PlayerTransitionState?): Modifier =
    graphicsLayer {
        val p = state?.fraction ?: 1f
        val mini = state?.miniArtworkBounds ?: Rect.Zero
        val scale = if (mini.width > 0f) lerp(mini.width / size.width, 1f, p) else 1f
        shape = RoundedCornerShape(lerp(MINI_ARTWORK_CORNER.toPx(), Radius.large.toPx(), p) / scale.coerceAtLeast(MIN_CARD_SCALE))
        clip = true
    }
