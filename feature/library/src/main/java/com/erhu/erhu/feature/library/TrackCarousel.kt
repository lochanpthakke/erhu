package com.erhu.erhu.feature.library

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import com.erhu.erhu.core.common.perf.Interactions
import com.erhu.erhu.core.designsystem.foundation.Motion
import com.erhu.erhu.core.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/** Dragged past this share of the page, the track changes on release. */
private const val SKIP_DISTANCE_FRACTION = 0.25f

/** A sideways release faster than this skips regardless of distance (in its direction). */
private val SKIP_FLING_VELOCITY = 900.dp

/** Dragging towards a side with no song moves the content this much per finger pixel. */
private const val EDGE_RESISTANCE = 0.25f

/** A committed skip whose track change never arrives (e.g. the player refused it) springs back after this. */
private const val TRACK_CHANGE_TIMEOUT_MS = 1_000L

/**
 * Now Playing's tracks as a strip of pages: previous | current | next, one
 * [page] apart. While settling, [position] = [offset] + [base]; at rest it is
 * zero. Pointer movement writes [position]
 * synchronously; the neighbours are already composed, so
 * their artwork is loaded before they come into view.
 *
 * A committed swipe asks the player to skip AT RELEASE and settles the strip
 * toward the next page with the finger's velocity. When the player reports the
 * new track, the pages move one slot over and [base] moves one page the other
 * way IN THE SAME COMPOSITION, so not a pixel moves at that moment and the
 * running settle already aims at the new rest position. A track change from the
 * buttons or the end of a song slides the same strip, so every track change is
 * one motion with no second animation on top.
 */
@Stable
internal class TrackCarouselState(
    private val scope: CoroutineScope,
) {
    val offset = Animatable(0f)

    /** Page shifts applied when the current track changed (see the class doc). */
    private var base = 0f

    /**
     * Distance between two pages, in px: the drag distance that moves one track over (the
     * artwork cards' spacing, so the cards follow the finger one to one).
     */
    var page by mutableFloatStateOf(0f)

    /**
     * Where page [slot] stands, in pages from the centre: 0 = current and at rest, -1 one place to
     * the left. Fractional while dragging or settling. Read in the draw phase.
     */
    fun distance(slot: Int): Float = if (page > 0f) slot + position / page else slot.toFloat()

    /** Where the strip is drawn, relative to the current page at rest (0). Read in the draw phase. */
    var position by mutableFloatStateOf(0f)
        private set

    private var dragging = false

    /** Committed swipes whose track change has not arrived yet (+ = next, - = previous). */
    private var pending = 0
    private var settle: Job? = null
    private var settleTarget = 0f
    private var watchdog: Job? = null

    internal fun startDrag() {
        dragging = true
        settle?.cancel()
        Interactions.put(Interactions.TRACK, "swipe")
    }

    internal fun dragBy(
        delta: Float,
        canGoNext: Boolean,
        canGoPrevious: Boolean,
    ) {
        val towardsMissing = (position + delta < 0f && !canGoNext) || (position + delta > 0f && !canGoPrevious)
        position += if (towardsMissing) delta * EDGE_RESISTANCE else delta
    }

    /** Ends a drag: [direction] +1 = skip to next, -1 = previous, 0 = spring back. */
    internal fun release(
        direction: Int,
        velocity: Float,
        skip: (Int) -> Unit,
    ) {
        dragging = false
        if (direction == 0) {
            settleAt(0f, velocity)
            return
        }
        commit(direction, velocity, skip)
    }

    /** The skip buttons: the same strip motion as a swipe, starting at rest. */
    internal fun skipFromButton(
        direction: Int,
        skip: (Int) -> Unit,
    ) {
        commit(direction, 0f, skip)
    }

    /**
     * Send the playback action immediately. The strip settles independently;
     * the reported track change re-centres its pages without a position jump.
     * Animation completion must never gate a music control or a committed swipe.
     */
    private fun commit(
        direction: Int,
        velocity: Float,
        skip: (Int) -> Unit,
    ) {
        pending += direction
        Interactions.put(Interactions.TRACK, "swipe")
        // Opposite commands can cancel before a new state is composed. Keep
        // the visual target on the net requested neighbour, never beyond the
        // three pages we actually have ready to display.
        settleAt(-pending.coerceIn(-1, 1) * page, velocity)
        armWatchdog()
        skip(direction)
    }

    /**
     * Called DURING COMPOSITION when the current track changed, together with the
     * new pages. [direction]: +1 the new one was the next song, -1 the previous one,
     * 0 an unrelated jump (e.g. a queue tap).
     */
    internal fun onTrackChanged(direction: Int) {
        val shift =
            if (pending != 0) {
                sign(pending.toFloat()).toInt()
            } else {
                direction
            }
        base += shift * page
        // This handoff runs during composition. Do not subscribe that composition
        // to position, which otherwise changes on every drag/animation frame.
        Snapshot.withoutReadObservation { position += shift * page }
        // StateFlow/composition can coalesce several skips into one latest
        // state. Do not leave an unobservable intermediate skip pending.
        pending = 0
    }

    /** After a track change has been composed: settle on the (new) current page unless a finger holds the strip. */
    internal fun settleAfterTrackChange() {
        if (pending == 0) watchdog?.cancel()
        if (!dragging && (settle?.isActive != true || settleTarget != -base)) {
            settleAt(0f, offset.velocity)
        }
    }

    /** Animates [position] to [target] (relative to the current page). */
    private fun settleAt(
        target: Float,
        velocity: Float,
    ) {
        settle?.cancel()
        val absolute = target - base
        val start = position - base
        settleTarget = absolute
        settle =
            scope.launch {
                offset.snapTo(start)
                offset.animateTo(absolute, Motion.trackSettle(), initialVelocity = velocity) {
                    position = value + base
                }
                if (!dragging) Interactions.put(Interactions.TRACK, null)
            }
    }

    private fun armWatchdog() {
        watchdog?.cancel()
        watchdog =
            scope.launch {
                delay(TRACK_CHANGE_TIMEOUT_MS)
                if (pending != 0) {
                    pending = 0
                    if (!dragging) settleAt(0f, 0f)
                }
            }
    }
}

@Composable
internal fun rememberTrackCarouselState(): TrackCarouselState {
    val scope = rememberCoroutineScope()
    return remember(scope) { TrackCarouselState(scope) }
}

/**
 * Follows the current song: when it changes, tells [state] from which side it
 * came (the previous composition's next / previous song) in the same
 * composition that shows the new pages, then lets the strip settle.
 */
@Composable
internal fun TrackChangeEffect(
    state: TrackCarouselState,
    current: Song?,
    currentIndex: Int,
    previousSong: Song?,
    nextSong: Song?,
) {
    val last = remember { LastNeighbours() }
    val id = current?.id
    // Repeated queue entries can have the same song id. Moving to the next
    // occurrence must acknowledge the skip and re-centre the visual strip too.
    if (id != last.current || currentIndex != last.index) {
        if (last.current != null && id != null) {
            state.onTrackChanged(
                when {
                    id == last.current -> currentIndex.compareTo(last.index)
                    id == last.next -> 1
                    id == last.previous -> -1
                    else -> 0
                },
            )
        }
        last.current = id
        last.index = currentIndex
    }
    last.next = nextSong?.id
    last.previous = previousSong?.id
    LaunchedEffect(id, currentIndex) { state.settleAfterTrackChange() }
}

private class LastNeighbours {
    var current: Long? = null
    var index: Int = -1
    var next: Long? = null
    var previous: Long? = null
}

/**
 * The strip itself: [previous], [current] and [next] as cards in depth (the current one in front;
 * its neighbours to either side, smaller, turned towards the centre and dimmed, partly visible),
 * moved by the shared position in the draw phase only (no recomposition per frame). Pages are keyed by song, so when the strip re-centres a
 * page keeps its composition (and its loaded artwork) as it becomes the current one.
 *
 * [content] gets each page's song, whether it is the current one, and its live distance from the
 * centre (see [TrackCarouselState.distance]) for effects drawn inside the page.
 *
 * [stack] (0..1, read in the draw phase only) gathers the neighbours into one card behind the
 * current one: 0 = the three-card carousel, 1 = stacked (see [depthCard]).
 */
@Composable
@Suppress("LongParameterList")
internal fun TrackCarousel(
    state: TrackCarouselState,
    previous: Song?,
    current: Song,
    next: Song?,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
    stack: () -> Float = { 0f },
    content: @Composable (song: Song, isCurrent: Boolean, distance: () -> Float) -> Unit,
) {
    // Stacked, the card behind is the next song's; the previous one fades out (at the end of the
    // queue, with no next song, the previous one stays as the card behind instead).
    val hideLeft = next != null
    val transition = LocalPlayerTransition.current
    Box(
        modifier =
            modifier.onGloballyPositioned { coordinates ->
                transition?.carouselBounds = coordinates.findRootCoordinates().localBoundingBoxOf(coordinates, clipBounds = false)
            },
        contentAlignment = Alignment.Center,
    ) {
        // Layout stays at its final size. One transform carries the cards together;
        // the child layers only unfold the neighbours, never the central artwork.
        Box(
            modifier = Modifier.fillMaxSize().playerCardTravel(transition),
            contentAlignment = Alignment.Center,
        ) {
            val pages = listOfNotNull(previous?.let { -1 to it }, 0 to current, next?.let { 1 to it })
            pages.forEach { (slot, song) ->
                // A song can be its own neighbour (2-song repeat, single song): keep keys unique.
                val duplicate = slot != 0 && (song.id == current.id || (slot == 1 && song.id == previous?.id))
                key(if (duplicate) "${song.id}#$slot" else song.id) {
                    val distance = { state.distance(slot) }
                    // The card nearest the centre is drawn on top. Flips once per swipe, not per frame.
                    val inFront by remember(slot, transition) {
                        derivedStateOf {
                            if (transition?.isSettledOpen == false) slot == 0 else abs(state.distance(slot)) < FRONT_DISTANCE
                        }
                    }
                    Box(
                        modifier =
                            Modifier
                                .zIndex(if (inFront) 1f else 0f)
                                .graphicsLayer {
                                    val reveal = transition?.let { playerNeighbourReveal(it.fraction) } ?: 1f
                                    depthCard(distance() * reveal, reduceMotion, stack(), hideLeft)
                                    if (slot != 0) alpha *= reveal
                                }
                                // One page is the distance between two cards: the cards follow the finger one to one.
                                .then(if (slot == 0) Modifier.onSizeChanged { state.page = it.width * SIDE_SPACING } else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        content(song, slot == 0, distance)
                    }
                }
            }
        }
    }
}

/** Below this distance from the centre a page is drawn in front of its neighbours. */
private const val FRONT_DISTANCE = 0.5f

/** Side cards: where they stand (share of a card's width from the centre), how small, how turned. */
private const val SIDE_SPACING = 0.62f
private const val SIDE_SCALE = 0.78f
private const val SIDE_TURN_DEGREES = 32f
private const val REDUCED_SIDE_SCALE = 0.86f

/** Stacked: the card behind is a little larger than the current one, peeking out around it. */
private const val STACKED_BEHIND_SCALE = 1.06f

/** Stacked: the left card is gone by the time it has covered this share of the way in. */
private const val STACKED_HIDE_RATE = 1.5f

/** Beyond one place over, cards shrink and fade out by this distance. */
private const val VANISH_DISTANCE = 1.6f
private const val CAMERA_DISTANCE = 14f

/**
 * One card of the depth carousel at [distance] places from the centre: moved sideways by a share of
 * its width, scaled down and turned towards the centre as it leaves, fading only past one place.
 * With [reduceMotion] the cards slide and scale but never turn.
 *
 * [stack] blends towards the stacked layout: one place over, a card sits centred behind the
 * current one, unturned and a little larger; on the way there (a swipe) it slides out to the side
 * and back, so the motion stays continuous in [distance]. With [hideLeft] the cards on the left
 * fade out as they gather, so the two neighbours merge into one card instead of piling up.
 */
private fun GraphicsLayerScope.depthCard(
    distance: Float,
    reduceMotion: Boolean,
    stack: Float,
    hideLeft: Boolean,
) {
    val away = abs(distance)
    val near = away.coerceAtMost(1f)
    val sideScale = if (reduceMotion) REDUCED_SIDE_SCALE else SIDE_SCALE
    val spread = sign(distance) * size.width * SIDE_SPACING
    // Out to the side half way, back behind the centre at one place over.
    val stackedOut = 2f * minOf(near, 1f - near)
    val scale = lerp(1f - (1f - sideScale) * near, 1f + (STACKED_BEHIND_SCALE - 1f) * near, stack)
    scaleX = scale
    scaleY = scale
    translationX = lerp(distance * size.width * SIDE_SPACING, spread * stackedOut, stack)
    rotationY = if (reduceMotion) 0f else -sign(distance) * SIDE_TURN_DEGREES * near * (1f - stack)
    cameraDistance = CAMERA_DISTANCE * density
    val vanish = (1f - (away - 1f).coerceAtLeast(0f) / (VANISH_DISTANCE - 1f)).coerceIn(0f, 1f)
    val gathered = if (hideLeft) (stack * (-distance).coerceIn(0f, 1f) * STACKED_HIDE_RATE).coerceAtMost(1f) else 0f
    alpha = vanish * (1f - gathered)
}

/**
 * Horizontal swipe on the strip. Only clearly horizontal drags are claimed (the
 * vertical drag collapses the player). Release: a fling in a direction decides
 * on its own; otherwise a quarter page decides; otherwise it springs back.
 */
@Composable
internal fun Modifier.trackCarouselSwipe(
    state: TrackCarouselState,
    canGoNext: () -> Boolean,
    canGoPrevious: () -> Boolean,
    skip: (direction: Int) -> Unit,
): Modifier =
    pointerInput(state) {
        val fling = SKIP_FLING_VELOCITY.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val (start, slop) = awaitHorizontalSwipeStart(down) ?: return@awaitEachGesture
            if (slop == Offset.Zero) return@awaitEachGesture
            val tracker = VelocityTracker()
            tracker.addPosition(down.uptimeMillis, down.position)
            start.historical.forEach { tracker.addPosition(it.uptimeMillis, it.position) }
            tracker.addPosition(start.uptimeMillis, start.position)
            state.startDrag()
            var travelled = slop.x
            state.dragBy(slop.x, canGoNext(), canGoPrevious())
            if (start.pressed) {
                trackDrag(start.id, tracker) { delta ->
                    travelled += delta.x
                    state.dragBy(delta.x, canGoNext(), canGoPrevious())
                }
            }
            val velocity = tracker.calculateVelocity().x
            val wanted =
                when {
                    abs(velocity) > fling -> if (velocity < 0) 1 else -1
                    abs(travelled) > state.page * SKIP_DISTANCE_FRACTION -> if (travelled < 0) 1 else -1
                    else -> 0
                }
            val allowed = (wanted == 1 && canGoNext()) || (wanted == -1 && canGoPrevious())
            state.release(if (allowed) wanted else 0, velocity, skip)
        }
    }

private suspend fun AwaitPointerEventScope.awaitHorizontalSwipeStart(down: PointerInputChange): Pair<PointerInputChange, Offset>? {
    var slop = Offset.Zero
    val start =
        awaitTouchSlopOrCancellation(down.id) { change, over ->
            if (abs(over.x) > abs(over.y)) {
                change.consume()
                slop = over
            }
        } ?: currentEvent.changes.firstOrNull { change ->
            // A busy UI thread can deliver all of a fast swipe in its up event.
            // Foundation cancels slop detection on up before inspecting movement.
            val completed = completedHorizontalSwipe(down, change, viewConfiguration.touchSlop)
            if (completed != null && currentEvent.changes.none { it.pressed }) {
                slop = completed
                change.consume()
                true
            } else {
                false
            }
        }
    return start?.let { it to slop }
}

/** Movement beyond slop when recognition first sees a completed, unclaimed horizontal swipe. */
internal fun completedHorizontalSwipe(
    down: PointerInputChange,
    up: PointerInputChange,
    touchSlop: Float,
): Offset? {
    if (up.id != down.id || up.pressed || up.isConsumed) return null
    val travel = up.position - down.position
    val distance = travel.getDistance()
    return if (distance > touchSlop && abs(travel.x) > abs(travel.y)) travel * (1f - touchSlop / distance) else null
}
