package com.lochanpthakke.empee3.core.designsystem.component

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlin.math.PI
import kotlin.math.sin

/** Scrolling this far collapses a header title completely into its button. */
private val TITLE_COLLAPSE_DISTANCE = 72.dp

/** A merging title shrinks to this scale as it reaches the button... */
internal const val MERGE_SCALE = 0.2f

/** ...and is gone a little before it gets there (it never sits on top of the button). */
private const val MERGE_FADE_END = 0.85f

/** The button's glass swells this much as it takes the title in, then settles back. */
private const val MERGE_PULSE = 0.06f
private const val MERGE_PULSE_FROM = 0.55f

/**
 * A header title that collapses into the header's left button (menu / back) as the page scrolls:
 * the visible list or grid reports its real scroll state ([ReportTitleScroll]); [OverlayHeader]
 * moves its title towards the button, shrinking and fading it, until only the button is left.
 * Scrolling back brings it out again. [progress] follows the scroll directly (partial scrolls hold
 * an intermediate state); it is read only while drawing, so scrolling never recomposes the header.
 */
@Stable
class CollapsingTitleState internal constructor(
    private val distancePx: Float,
) {
    private var source by mutableStateOf<(() -> Float)?>(null)
    private var owner: Any? = null

    /** 0 = title in place, 1 = merged into the button. */
    val progress: Float get() = source?.invoke()?.coerceIn(0f, 1f) ?: 0f

    internal fun attach(
        key: Any,
        progress: () -> Float,
    ) {
        owner = key
        source = progress
    }

    internal fun detach(key: Any) {
        if (owner === key) {
            owner = null
            source = null
        }
    }

    /** Collapse from a lazy container's first item: gone once it has scrolled [distancePx] (or out of view). */
    internal fun fromFirstItem(
        index: Int,
        offset: Int,
    ): Float = if (index > 0) 1f else offset / distancePx
}

/** The screen's collapsing title, for the lists inside it to report to (null: nothing collapses). */
val LocalCollapsingTitle = staticCompositionLocalOf<CollapsingTitleState?> { null }

@Composable
fun rememberCollapsingTitleState(): CollapsingTitleState {
    val distance = with(LocalDensity.current) { TITLE_COLLAPSE_DISTANCE.toPx() }
    return remember(distance) { CollapsingTitleState(distance) }
}

/** This list drives the screen's collapsing title while it is shown (the newest shown list wins). */
@Composable
fun ReportTitleScroll(state: LazyListState) {
    val title = LocalCollapsingTitle.current ?: return
    DisposableEffect(title, state) {
        title.attach(state) { title.fromFirstItem(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset) }
        onDispose { title.detach(state) }
    }
}

/** This grid drives the screen's collapsing title while it is shown (the newest shown list wins). */
@Composable
fun ReportTitleScroll(state: LazyGridState) {
    val title = LocalCollapsingTitle.current ?: return
    DisposableEffect(title, state) {
        title.attach(state) { title.fromFirstItem(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset) }
        onDispose { title.detach(state) }
    }
}

/**
 * Places a title layer (origin: its top-left corner) on its way into the button: from its resting
 * [left]/[top] at [startScale] to the button's centre at [MERGE_SCALE], as [t] goes 0 -> 1, fading
 * out before it arrives. [restLeft]/[restTop] are where the layer is laid out; [height] its height.
 */
internal fun GraphicsLayerScope.mergeIntoButton(
    t: Float,
    button: Offset,
    restLeft: Float,
    restTop: Float,
    height: Float,
    left: Float = restLeft,
    top: Float = restTop,
    startScale: Float = 1f,
) {
    val scale = lerp(startScale, MERGE_SCALE, t)
    val endLeft = button.x
    val endTop = button.y - height * MERGE_SCALE / 2f
    transformOrigin = TransformOrigin(0f, 0f)
    scaleX = scale
    scaleY = scale
    translationX = lerp(left, endLeft, t) - restLeft
    translationY = lerp(top, endTop, t) - restTop
    alpha = 1f - smoothstep((t / MERGE_FADE_END).coerceAtMost(1f))
}

/** The button's glass taking a title in: a small swell near the end of the merge (1 at rest). */
internal fun mergePulse(t: Float): Float {
    val x = ((t - MERGE_PULSE_FROM) / (1f - MERGE_PULSE_FROM)).coerceIn(0f, 1f)
    return 1f + MERGE_PULSE * sin(PI.toFloat() * x)
}
