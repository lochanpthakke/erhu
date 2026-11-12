package com.erhu.erhu.core.designsystem.component

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** The last stretch of the large title's rise over which the header collapses. */
private val COLLAPSE_TRAVEL = 120.dp

/** The bar's title takes over from the large one over this first part of the collapse. */
private const val HANDOVER_SPAN = 0.15f

/** The artist / facts lines under the title are gone by this point of the collapse. */
private const val DETAILS_GONE_AT = 0.5f

/** Two-line titles can't travel as one line: they fade where they are instead, over this part. */
private const val WRAPPED_HERO_GONE_FROM = 0.3f
private const val WRAPPED_HERO_GONE_AT = 0.6f

/**
 * A detail page's collapsing header: the large title at the bottom of a hero (the list's first
 * item) becomes [OverlayHeader]'s title as the page scrolls.
 *
 * As the collapse begins, the header's title takes over from the large one at exactly its place
 * and size, then travels into the back button, shrinking and fading as it merges, until only the
 * button is left (no title stays in the header); the lines under the title fade. One title,
 * moving: drawn by the header, above the list. Scrolling back brings it out of the button again.
 *
 * [progress] (0 = expanded, 1 = collapsed) follows the list's scroll position directly, so partial
 * scrolls hold an intermediate state and reversing direction reverses the motion. Everything here
 * is read in draw-phase lambdas (graphicsLayer, drawBehind): scrolling never recomposes.
 *
 * Wiring: `OverlayHeader(collapsingHeader = state, title = ...)`; in the hero, the title block gets
 * [collapsingHeroBlock], its title [collapsingHeroTitle] (and its `onTextLayout` [onHeroTitleLayout]),
 * the lines under it [collapsingHeroDetails].
 */
@Stable
class CollapsingHeaderState internal constructor(
    private val listState: LazyListState,
    private val travelPx: Float,
    private val isRtl: Boolean = false,
) {
    /** Height of the header bar, px (measured by [OverlayHeader]). */
    internal var barHeight by mutableFloatStateOf(0f)

    /** Top of the large title block within the hero item, px ([collapsingHeroBlock]). */
    internal var titleTop by mutableFloatStateOf(Float.NaN)

    /** The large title on screen (root coordinates), its first line's height and its line count. */
    internal var heroLeft by mutableFloatStateOf(Float.NaN)
    internal var heroRight by mutableFloatStateOf(Float.NaN)
    internal var heroTop by mutableFloatStateOf(Float.NaN)
    internal var heroLineHeight by mutableFloatStateOf(0f)
    internal var heroLines by mutableIntStateOf(1)

    /** The header's title where it is laid out (root coordinates) and its line height ([OverlayHeader]). */
    internal var barLeft by mutableFloatStateOf(Float.NaN)
    internal var barTop by mutableFloatStateOf(Float.NaN)
    internal var barLineHeight by mutableFloatStateOf(0f)

    /** The centre of the header's back button (root coordinates), where the title merges. */
    internal var button by mutableStateOf(Offset.Unspecified)

    /** 0 = expanded, 1 = collapsed: how far the large title has risen into the bar. */
    val progress: Float
        get() {
            if (listState.firstVisibleItemIndex > 0) return 1f
            val top = titleTop
            if (top.isNaN()) return 0f
            val titleOnScreen = top - listState.firstVisibleItemScrollOffset
            return ((barHeight + travelPx - titleOnScreen) / travelPx).coerceIn(0f, 1f)
        }

    /** The title can travel: one line, and both ends measured. */
    private val travels: Boolean
        get() =
            heroLines == 1 &&
                !heroTop.isNaN() &&
                !barTop.isNaN() &&
                heroLineHeight > 0f &&
                barLineHeight > 0f &&
                button.isSpecified

    /** The large title's `onTextLayout`. */
    fun onHeroTitleLayout(result: TextLayoutResult) {
        heroLines = result.lineCount
        heroLineHeight = result.getLineBottom(0) - result.getLineTop(0)
    }

    /** The large title: shown until the bar's title takes over (or, two lines, fades). */
    internal fun drawHeroTitle(scope: GraphicsLayerScope) {
        val p = progress
        scope.alpha =
            if (travels) {
                1f - smoothstep((p / HANDOVER_SPAN).coerceAtMost(1f))
            } else {
                1f - smoothstep(((p - WRAPPED_HERO_GONE_FROM) / (WRAPPED_HERO_GONE_AT - WRAPPED_HERO_GONE_FROM)).coerceIn(0f, 1f))
            }
    }

    /**
     * The header's title: it appears over the large title, same place and size, then travels into
     * the back button and is gone ([mergeIntoButton]). Two-line titles never appear here.
     */
    internal fun drawBarTitle(scope: GraphicsLayerScope) {
        if (!travels) {
            scope.alpha = 0f
            return
        }
        val p = progress
        with(scope) {
            mergeIntoButton(
                t = smoothstep(p),
                button = button,
                restLeft = barLeft,
                restTop = barTop,
                height = barLineHeight,
                left = if (isRtl && !heroRight.isNaN()) heroRight - size.width else heroLeft,
                top = heroTop,
                startScale = heroLineHeight / barLineHeight,
                isRtl = isRtl,
            )
            alpha *= smoothstep((p / HANDOVER_SPAN).coerceAtMost(1f))
        }
    }
}

/** A collapsing header for a page whose first [listState] item is the hero. */
@Composable
fun rememberCollapsingHeaderState(listState: LazyListState): CollapsingHeaderState {
    val travel = with(LocalDensity.current) { COLLAPSE_TRAVEL.toPx() }
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    return remember(listState, travel, isRtl) { CollapsingHeaderState(listState, travel, isRtl) }
}

/** The hero's title block (placed directly in the hero item's root box): where the collapse is measured from. */
fun Modifier.collapsingHeroBlock(state: CollapsingHeaderState): Modifier = onPlaced { state.titleTop = it.positionInParent().y }

/** The hero's large title (also pass [CollapsingHeaderState.onHeroTitleLayout] as its `onTextLayout`). */
fun Modifier.collapsingHeroTitle(state: CollapsingHeaderState): Modifier =
    onGloballyPositioned {
        val position = it.positionInRoot()
        state.heroLeft = position.x
        state.heroRight = position.x + it.size.width
        state.heroTop = position.y
    }.graphicsLayer { state.drawHeroTitle(this) }

/** The lines under the large title (artist, facts): they fade as the collapse begins. */
fun Modifier.collapsingHeroDetails(state: CollapsingHeaderState): Modifier =
    graphicsLayer { alpha = 1f - smoothstep((state.progress / DETAILS_GONE_AT).coerceAtMost(1f)) }

/** The bar's title in [OverlayHeader]. */
internal fun Modifier.collapsingBarTitle(state: CollapsingHeaderState): Modifier =
    onGloballyPositioned {
        val position = it.positionInRoot()
        state.barLeft = position.x
        state.barTop = position.y
        state.barLineHeight = it.size.height.toFloat()
    }.graphicsLayer { state.drawBarTitle(this) }

/** Eased 0..1 (no overshoot: the title motion is controlled, not springy). */
internal fun smoothstep(x: Float): Float = x * x * (3f - 2f * x)
