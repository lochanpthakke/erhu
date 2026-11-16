package com.erhu.erhu.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.erhu.erhu.core.common.locale.AppLocaleManager
import com.erhu.erhu.core.designsystem.foundation.Spacing
import com.erhu.erhu.core.designsystem.theme.ErhuGlass
import com.erhu.erhu.core.designsystem.theme.LocalGlassBackdrop
import com.erhu.erhu.core.designsystem.theme.glassBackdropSource
import com.erhu.erhu.core.designsystem.theme.liquidGlass
import com.erhu.erhu.core.designsystem.theme.rememberGlassBackdrop
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val SCRUB_WIDTH = 28.dp
private val LETTER_SIZE = 56.dp
private const val OTHER_INITIAL = "#"

private data class AlphabetStop(
    val letter: String,
    val index: Int,
)

private fun alphabetStops(labels: List<String>): List<AlphabetStop> {
    val seen = mutableSetOf<String>()
    return buildList {
        labels.forEachIndexed { index, label ->
            val start = label.trimStart()
            val letter =
                if (start.isNotEmpty() && Character.isLetter(start.codePointAt(0))) {
                    String(Character.toChars(start.codePointAt(0))).uppercase(Locale.ROOT)
                } else {
                    OTHER_INITIAL
                }
            if (seen.add(letter)) add(AlphabetStop(letter, index))
        }
    }
}

/** Jump by initial when indexed, or by position for lists ordered by something other than text. */
@Composable
internal fun LibraryFastScroll(
    labels: List<String>,
    topInset: Dp,
    bottomInset: Dp,
    onJump: (Int) -> Unit,
    modifier: Modifier = Modifier,
    itemCount: Int = labels.size,
    enabled: Boolean = true,
    showHandle: Boolean = false,
    content: @Composable () -> Unit,
) {
    val stops =
        remember(labels, itemCount) {
            if (labels.size == itemCount) alphabetStops(labels) else emptyList()
        }
    val byInitial = stops.size > 1
    val locale = AppLocaleManager.activeLocale(LocalContext.current)
    val percent = remember(locale) { NumberFormat.getPercentInstance(locale) }
    var activeIndex by remember(stops, itemCount) { mutableIntStateOf(-1) }
    var activeLabel by remember(stops, itemCount) { mutableStateOf<String?>(null) }
    var fingerY by remember { mutableFloatStateOf(0f) }
    var gestureBottom by remember { mutableFloatStateOf(0f) }
    val jump by rememberUpdatedState(onJump)
    val insetTop by rememberUpdatedState(topInset)
    val insetBottom by rememberUpdatedState(bottomInset)
    val density = LocalDensity.current
    val backdrop = rememberGlassBackdrop("alphabetScrubber")
    val canScrub = enabled && itemCount > 1
    val drag =
        if (canScrub) {
            Modifier.rightEdgeDrag(
                topInset = { insetTop },
                bottomInset = { insetBottom },
                onDrag = { y, top, bottom ->
                    fingerY = y.coerceIn(top, bottom)
                    gestureBottom = bottom
                    val fraction = (fingerY - top) / (bottom - top)
                    val stop = if (byInitial) stops[(fraction * stops.size).toInt().coerceIn(stops.indices)] else null
                    val index = stop?.index ?: (fraction * (itemCount - 1)).roundToInt()
                    if (index != activeIndex) {
                        activeIndex = index
                        activeLabel =
                            stop?.letter ?: stops.singleOrNull()?.letter ?: percent.format(index.toDouble() / (itemCount - 1))
                        jump(index)
                    }
                },
                reset = {
                    activeIndex = -1
                    activeLabel = null
                },
                key = stops to itemCount,
            )
        } else {
            Modifier
        }

    Box(modifier.then(drag)) {
        Box(Modifier.matchParentSize().glassBackdropSource(backdrop, enabled = activeLabel != null)) {
            content()
        }
        if (canScrub && showHandle) {
            Box(
                Modifier
                    .matchParentSize()
                    .absolutePadding(top = topInset, right = Spacing.xs, bottom = bottomInset),
                contentAlignment = AbsoluteAlignment.CenterRight,
            ) {
                Box(
                    Modifier
                        .size(
                            width = 4.dp,
                            height = 32.dp,
                        ).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f), CircleShape),
                )
            }
        }
        activeLabel?.let { label ->
            CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
                val radius = with(density) { LETTER_SIZE.toPx() / 2f }
                val minimumY = with(density) { topInset.toPx() }
                Surface(
                    modifier =
                        Modifier
                            .align(AbsoluteAlignment.TopRight)
                            .absolutePadding(right = SCRUB_WIDTH + Spacing.xs)
                            .offset {
                                val maxY = (gestureBottom - radius * 2).coerceAtLeast(minimumY)
                                IntOffset(0, (fingerY - radius).coerceIn(minimumY, maxY).roundToInt())
                            }.size(LETTER_SIZE)
                            .liquidGlass(CircleShape),
                    shape = CircleShape,
                    color = ErhuGlass.containerColor(MaterialTheme.colorScheme.surfaceVariant),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            label,
                            style = if (stops.isEmpty()) MaterialTheme.typography.labelLarge else MaterialTheme.typography.headlineSmall,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/** Intercept only a vertical drag that starts at the edge. Taps still reach the list. */
private fun Modifier.rightEdgeDrag(
    topInset: () -> Dp,
    bottomInset: () -> Dp,
    onDrag: (y: Float, top: Float, bottom: Float) -> Unit,
    reset: () -> Unit,
    key: Pair<List<AlphabetStop>, Int>,
): Modifier =
    pointerInput(key) {
        try {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val top = topInset().toPx()
                val bottom = size.height - bottomInset().toPx()
                if (bottom <= top || down.position.y !in top..bottom) return@awaitEachGesture
                if (down.position.x < size.width - SCRUB_WIDTH.toPx()) return@awaitEachGesture

                var dragging = false
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed || change.isConsumed) break
                    if (!dragging) {
                        val delta = change.position - down.position
                        val x = abs(delta.x)
                        val y = abs(delta.y)
                        val slop = viewConfiguration.touchSlop
                        if (x > slop && x >= y) break
                        dragging = y > slop && y > x
                    }
                    if (dragging) {
                        change.consume()
                        onDrag(change.position.y, top, bottom)
                    }
                }
                reset()
            }
        } finally {
            reset()
        }
    }
