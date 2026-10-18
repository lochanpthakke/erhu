package com.lochanpthakke.empee3.feature.library

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lochanpthakke.empee3.core.designsystem.foundation.Motion
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Progress spans a screen, not one pixel. The default Float spring tolerance
 * (0.01) snaps the last ~1% of that journey, visibly resizing the artwork at
 * handoff. Keep the residual below a pixel even on a tall, high-density screen.
 */
private const val PROGRESS_VISIBILITY_THRESHOLD = 0.0001f

/** Released below this expansion (and not flung up), the player collapses. */
private const val SETTLE_THRESHOLD = 0.65f

/** A release faster than this (per second, in screen heights) decides the direction on its own. */
private const val FLING_FRACTION_PER_SECOND = 1.2f

/** The arrival settle's kick: with [Motion.playful] it peaks near 1 and rings out in ~0.4 s. */
private const val ARRIVAL_IMPULSE = 32f
private const val ARRIVAL_THRESHOLD = 0.001f

/**
 * The player has visibly LANDED within this much progress of rest (~15-20 px on a phone): its
 * spring then only creeps the last pixels (critically damped, so that tail takes ~0.35 s more).
 * The settle starts here, not after the creep, so it follows the landing at once.
 */
private const val LANDED_WITHIN = 0.01f

/**
 * The player as ONE object with one continuous state: [progress] 0 = collapsed
 * (the mini player), 1 = expanded (Now Playing). Everything that belongs to the
 * player — the expanding surface, the travelling artwork, the content fading in,
 * the bottom chrome stepping aside — is drawn from this single value, so a tap,
 * a drag and a release all move the same timeline and can interrupt each other
 * at any point.
 *
 * The geometry it interpolates between is measured by the two representations
 * themselves (root coordinates, updated on layout, never per frame).
 */
@Stable
class PlayerTransitionState {
    val progress = Animatable(0f, visibilityThreshold = PROGRESS_VISIBILITY_THRESHOLD)

    /** The mini player surface, and its artwork, in root coordinates. */
    var miniBounds by mutableStateOf(Rect.Zero)
    var miniArtworkBounds by mutableStateOf(Rect.Zero)

    /** Now Playing's hero artwork in root coordinates (in the expanded layout). */
    var heroArtworkBounds by mutableStateOf(Rect.Zero)

    /** Fully expanded: the real Now Playing artwork shows instead of the travelling one. */
    val isSettledOpen: Boolean get() = progress.value == 1f

    /** True while the player is open or opening (drives Back and accessibility). */
    val isOpen: Boolean get() = progress.targetValue > 0f

    /**
     * A brief settle as the player LANDS (0 = mini player, 1 = Now Playing), continuing the
     * direction it travelled: it starts and ends at 0, peaks near 1. Read it only through
     * [landing] / [arriving], in the draw phase, as small transforms inside the measured geometry:
     * the transition itself (progress, bounds, the artwork handoff at 1) never overshoots.
     */
    val arrival = Animatable(0f, visibilityThreshold = ARRIVAL_THRESHOLD)

    /** The settle as the mini player lands (0 unless it has just collapsed). */
    val landing: Float get() = if (progress.value <= LANDED_WITHIN) arrival.value else 0f

    /** The settle as Now Playing arrives (0 unless it has just opened). */
    val arriving: Float get() = if (progress.value >= 1f - LANDED_WITHIN) arrival.value else 0f

    // Interrupting a running open/close keeps its current velocity, so reversing
    // mid-way turns around smoothly from where the player is instead of restarting.
    suspend fun expand(velocity: Float = progress.velocity) = moveTo(1f, Motion.hero(PROGRESS_VISIBILITY_THRESHOLD), velocity)

    suspend fun collapse(velocity: Float = progress.velocity) = moveTo(0f, Motion.hero(PROGRESS_VISIBILITY_THRESHOLD), velocity)

    /** Direct manipulation: the finger moved by [delta] in units of the drag distance. */
    internal suspend fun dragBy(delta: Float) {
        stopArrival()
        progress.snapTo((progress.value + delta).coerceIn(0f, 1f))
    }

    /**
     * Moves the player to [target]. As it lands (enters [LANDED_WITHIN] of the target, having
     * come from further away) it settles, alongside the last pixels of the move; an interrupted
     * move cancels its settle. Nothing waits for the settle.
     */
    private suspend fun moveTo(
        target: Float,
        spec: AnimationSpec<Float>,
        velocity: Float,
    ) = coroutineScope {
        var settles = abs(progress.value - target) > LANDED_WITHIN
        stopArrival()
        progress.animateTo(target, spec, initialVelocity = velocity) {
            if (settles && abs(value - target) <= LANDED_WITHIN) {
                settles = false
                launch { arrival.animateTo(0f, Motion.playful(ARRIVAL_THRESHOLD), initialVelocity = ARRIVAL_IMPULSE) }
            }
        }
    }

    /** Any new movement ends a settle at once, exactly at rest. */
    private suspend fun stopArrival() {
        if (arrival.isRunning || arrival.value != 0f) arrival.snapTo(0f)
    }

    /**
     * Finishing a drag: a fast enough fling decides by its direction, otherwise
     * the nearer state wins. The settle starts from the finger's velocity.
     */
    internal suspend fun release(velocity: Float) {
        val expand =
            when {
                velocity > FLING_FRACTION_PER_SECOND -> true
                velocity < -FLING_FRACTION_PER_SECOND -> false
                else -> progress.value > SETTLE_THRESHOLD
            }
        moveTo(if (expand) 1f else 0f, Motion.gestureSettle(PROGRESS_VISIBILITY_THRESHOLD), velocity)
    }
}

@Composable
fun rememberPlayerTransitionState(): PlayerTransitionState = remember { PlayerTransitionState() }

/**
 * A part of Now Playing arriving with the player: over [start]..[end] of the
 * expansion it fades in and travels [rise] into place (negative = from above).
 * Each section has its own slice of the SAME progress, so the content assembles
 * in order as the player opens and leaves in reverse as it closes; there is no
 * separate animation. Read in the draw phase only.
 */
fun Modifier.revealWithPlayer(
    state: PlayerTransitionState?,
    start: Float,
    end: Float,
    rise: Dp = REVEAL_RISE_DEFAULT,
): Modifier =
    if (state == null) {
        this
    } else {
        graphicsLayer {
            val p = state.progress.value
            val t = ((p - start) / (end - start)).coerceIn(0f, 1f)
            alpha = t
            // Arriving: the content carries on upward a touch with the player, and settles
            // (static layers such as the backdrop, rise 0, stay put).
            val settle = if (rise != 0.dp) state.expandedSettleOffset(this) else 0f
            translationY = (1f - Motion.Decelerate.transform(t)) * rise.toPx() + settle
        }
    }

private val REVEAL_RISE_DEFAULT = 24.dp

/** How far Now Playing's content carries on upward as the player arrives (at the settle's peak). */
private val EXPANDED_SETTLE_RISE = 4.dp

/** How far the mini player carries on down as it lands, and how much it squashes on its bottom edge. */
private val COLLAPSED_SETTLE_DROP = 3.dp
private const val COLLAPSED_SETTLE_SQUASH = 0.03f

/** The mini player squashes onto its bottom edge, the edge it lands on. */
private val LANDING_EDGE = TransformOrigin(pivotFractionX = 0.5f, pivotFractionY = 1f)

/**
 * The mini player landing (applied in its own layer, inside its measured bounds): it carries on
 * down a touch, squashing slightly on its bottom edge, and settles. Identity except just after
 * the player collapsed; the artwork and controls inside move with the surface.
 */
fun PlayerTransitionState.applyCollapsedSettle(scope: GraphicsLayerScope) {
    val a = landing
    with(scope) {
        translationY = a * COLLAPSED_SETTLE_DROP.toPx()
        scaleY = 1f - a * COLLAPSED_SETTLE_SQUASH
        scaleX = 1f + a * COLLAPSED_SETTLE_SQUASH / 2f
        transformOrigin = LANDING_EDGE
    }
}

/**
 * The travelling artwork during the landing's last pixels: placed at [left], [top] with [scale]
 * (its layer's origin is its top-left corner), it gets exactly the mini player's landing transform
 * (see [applyCollapsedSettle]), so artwork and card settle as one before the card's own artwork
 * takes over at rest.
 */
fun PlayerTransitionState.applyLandingToArtwork(
    scope: GraphicsLayerScope,
    left: Float,
    top: Float,
    scale: Float,
) {
    val a = landing
    val card = miniBounds
    with(scope) {
        if (a == 0f || card.width <= 0f) {
            translationX = left
            translationY = top
            scaleX = scale
            scaleY = scale
            return
        }
        val squashY = 1f - a * COLLAPSED_SETTLE_SQUASH
        val stretchX = 1f + a * COLLAPSED_SETTLE_SQUASH / 2f
        translationX = card.center.x + (left - card.center.x) * stretchX
        translationY = card.bottom - (card.bottom - top) * squashY + a * COLLAPSED_SETTLE_DROP.toPx()
        scaleX = scale * stretchX
        scaleY = scale * squashY
    }
}

/** Now Playing's arrival settle as a vertical offset, px (0 except just after opening). */
fun PlayerTransitionState.expandedSettleOffset(density: Density): Float = -arriving * with(density) { EXPANDED_SETTLE_RISE.toPx() }

/**
 * Follows pointer [pointerId] until it lifts, reporting every movement to [onDelta] and
 * [tracker] INCLUDING the movement carried by the up event and the batched historical
 * points. Foundation's drag() drops the up event's movement: when the UI thread was busy
 * (e.g. composing a new track), a whole fast swipe can arrive as one batch ending in that
 * up, and the swipe would read as a tiny, velocity-less drag.
 */
internal suspend fun AwaitPointerEventScope.trackDrag(
    pointerId: PointerId,
    tracker: VelocityTracker,
    onDelta: (Offset) -> Unit,
) {
    while (true) {
        val change = awaitPointerEvent().changes.firstOrNull { it.id == pointerId } ?: return
        change.historical.forEach { tracker.addPosition(it.uptimeMillis, it.position) }
        tracker.addPosition(change.uptimeMillis, change.position)
        val delta = change.positionChange()
        if (delta != Offset.Zero) onDelta(delta)
        change.consume()
        if (!change.pressed) return
    }
}

/** The player's transition, provided by the shell to both representations. */
val LocalPlayerTransition = staticCompositionLocalOf<PlayerTransitionState?> { null }

/**
 * Vertical drag that moves the player between its states: down on Now Playing
 * collapses it, up on the mini player expands it. The player follows the finger
 * one-to-one (dragging from the mini player's height to the top of the screen is
 * the whole transition) and the release settles with the finger's velocity. Taps are untouched (no movement,
 * no drag); a drag in the other direction or sideways is left to other handlers.
 */
@Composable
fun Modifier.playerDrag(
    state: PlayerTransitionState,
    expandsUpward: Boolean,
): Modifier {
    val scope = rememberCoroutineScope()
    return pointerInput(state, expandsUpward) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var slop = Offset.Zero
            val start =
                awaitTouchSlopOrCancellation(down.id) { change, over ->
                    // Only claim clearly vertical drags in this handler's direction.
                    val vertical = abs(over.y) > abs(over.x)
                    val rightWay = if (expandsUpward) over.y < 0 else over.y > 0
                    if (vertical && rightWay) {
                        change.consume()
                        slop = over
                    }
                } ?: return@awaitEachGesture
            if (slop == Offset.Zero) return@awaitEachGesture
            // A full transition is the distance from the mini player to the top of the screen.
            val travel = state.miniBounds.top.takeIf { it > 0f } ?: size.height.toFloat().coerceAtLeast(1f)
            val tracker = VelocityTracker()
            tracker.addPosition(start.uptimeMillis, start.position)
            scope.launch { state.dragBy(-slop.y / travel) }
            trackDrag(start.id, tracker) { delta -> scope.launch { state.dragBy(-delta.y / travel) } }
            val velocity = -tracker.calculateVelocity().y / travel
            scope.launch { state.release(velocity) }
        }
    }
}
