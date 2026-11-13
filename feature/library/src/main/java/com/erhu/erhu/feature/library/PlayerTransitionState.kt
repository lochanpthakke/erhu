package com.erhu.erhu.feature.library

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.erhu.erhu.core.designsystem.foundation.Motion
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

/**
 * The player as ONE object with one continuous state: [fraction] 0 = collapsed
 * (the mini player), 1 = expanded (Now Playing). Everything that belongs to the
 * player — the expanding surface, the complete travelling card, the content fading in,
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

    private var dragProgress by mutableStateOf<Float?>(null)

    /** Drawing follows the finger synchronously; the spring takes over at exactly this value. */
    val fraction: Float get() = (dragProgress ?: progress.value).coerceIn(0f, 1f)

    var carouselBounds by mutableStateOf(Rect.Zero)
    var cardSize by mutableStateOf(Size.Zero)
    var cardArtworkInset by mutableStateOf(0f)
    var measuredCardArtworkBounds by mutableStateOf(Rect.Zero)

    /** Derived from untransformed layout, never from a moving graphics layer. */
    val cardArtworkBounds: Rect
        get() {
            if (measuredCardArtworkBounds.width > 0f) return measuredCardArtworkBounds
            val side = (cardSize.width - 2f * cardArtworkInset).coerceAtLeast(0f)
            val left = carouselBounds.center.x - side / 2f
            val top = carouselBounds.center.y - cardSize.height / 2f + cardArtworkInset
            return Rect(left, top, left + side, top + side)
        }

    /** The mini player surface, and its artwork, in root coordinates. */
    var miniBounds by mutableStateOf(Rect.Zero)
    var miniArtworkBounds by mutableStateOf(Rect.Zero)

    /** Fully expanded: the card transform is identity and normal carousel layering resumes. */
    val isSettledOpen: Boolean get() = fraction == 1f

    private val openState = derivedStateOf { fraction > 0f || progress.targetValue > 0f }

    /** Own Back while any part is visible, including a direct drag or an interrupted close. */
    val isOpen: Boolean get() = openState.value

    // Interrupting a running open/close keeps its current velocity, so reversing
    // mid-way turns around smoothly from where the player is instead of restarting.
    suspend fun expand(velocity: Float = progress.velocity) = moveTo(1f, Motion.hero(PROGRESS_VISIBILITY_THRESHOLD), velocity)

    suspend fun collapse(velocity: Float = progress.velocity) = moveTo(0f, Motion.hero(PROGRESS_VISIBILITY_THRESHOLD), velocity)

    /** Direct manipulation: the finger moved by [delta] in units of the drag distance. */
    internal fun dragBy(delta: Float) {
        dragProgress = (fraction + delta).coerceIn(0f, 1f)
    }

    /** One interruptible spring owns the journey, including the exact final geometry. */
    private suspend fun moveTo(
        target: Float,
        spec: AnimationSpec<Float>,
        velocity: Float,
    ) {
        val dragged = dragProgress
        if (dragged != null) {
            progress.snapTo(dragged)
            dragProgress = null
        }
        progress.animateTo(target, spec, initialVelocity = velocity)
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
                else -> fraction > SETTLE_THRESHOLD
            }
        val target = if (expand) 1f else 0f
        val distance = kotlin.math.abs(target - fraction)
        moveTo(target, Motion.playerRelease(distance, velocity, PROGRESS_VISIBILITY_THRESHOLD), velocity)
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
            val p = state.fraction
            val t = ((p - start) / (end - start)).coerceIn(0f, 1f)
            alpha = t
            translationY = (1f - Motion.Decelerate.transform(t)) * rise.toPx()
        }
    }

private val REVEAL_RISE_DEFAULT = 24.dp

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
    /** Whether a drag may start now (Now Playing: only while its page is scrolled to the top). */
    canStart: () -> Boolean = { true },
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
                    if (vertical && rightWay && canStart()) {
                        change.consume()
                        slop = over
                    }
                } ?: return@awaitEachGesture
            if (slop == Offset.Zero) return@awaitEachGesture
            // A full transition is the distance from the mini player to the top of the screen.
            val travel = state.miniBounds.top.takeIf { it > 0f } ?: size.height.toFloat().coerceAtLeast(1f)
            val tracker = VelocityTracker()
            tracker.addPosition(down.uptimeMillis, down.position)
            start.historical.forEach { tracker.addPosition(it.uptimeMillis, it.position) }
            tracker.addPosition(start.uptimeMillis, start.position)
            state.dragBy(-slop.y / travel)
            try {
                if (start.pressed) trackDrag(start.id, tracker) { delta -> state.dragBy(-delta.y / travel) }
            } finally {
                val velocity = -tracker.calculateVelocity().y / travel
                scope.launch { state.release(velocity) }
            }
        }
    }
}
