package com.lochanpthakke.empee3.feature.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.util.lerp
import com.lochanpthakke.empee3.core.designsystem.component.BottomChrome
import com.lochanpthakke.empee3.core.designsystem.foundation.ComponentSize
import com.lochanpthakke.empee3.core.designsystem.foundation.Motion
import com.lochanpthakke.empee3.core.designsystem.foundation.Spacing
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The shell's bottom chrome on the Library: mini player, section navigation
 * and Search as one floating system that transforms continuously with
 * [state] (driven by scrolling the library):
 *
 * expanded (0):  [        mini player        ]
 *                [ sections            ][ ⌕ ]
 *
 * compact  (1):  [ ♪ ][  mini player   ][ ⌕ ]
 *
 * As it compacts the section bar narrows into a single button showing the
 * current section, the mini player rises into the row beside it (its skip
 * buttons fold away) and Search stays anchored. All of it happens in layout
 * and draw from [LibraryChromeState.fraction]: no recomposition per frame.
 * The chrome dynamically shrinks its layout height as it condenses into a single
 * row, ensuring the background backdrop wraps snugly without leaving empty space.
 *
 * Without [showNavigation] (other screens) it is just the mini player.
 */
@Composable
@Suppress("LongMethod")
fun LibraryBottomChrome(
    state: LibraryChromeState,
    showNavigation: Boolean,
    selected: LibrarySection,
    onSelect: (LibrarySection) -> Unit,
    onSearch: () -> Unit,
    onExpand: () -> Unit,
    searching: Boolean,
    miniPlayer: @Composable (compact: () -> Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val compact = { if (showNavigation) state.fraction else 0f }
    val stepAside = Modifier.stepAsideForPlayer(LocalPlayerTransition.current)
    Layout(
        modifier = modifier.fillMaxWidth(),
        // Each slot is wrapped in a Box, so the layout always has exactly three
        // children even while a slot's content is hidden (it then measures 0 high).
        content = {
            Box { miniPlayer(compact) }
            Box(modifier = stepAside) {
                AnimatedVisibility(
                    visible = showNavigation,
                    enter = fadeIn(Motion.fade(Motion.CONTENT)),
                    exit = fadeOut(Motion.fade(Motion.MICRO)),
                ) {
                    LibraryNavigationBar(
                        selected = selected,
                        onSelect = onSelect,
                        searching = searching,
                        compact = compact,
                        onExpand = onExpand,
                    )
                }
            }
            Box(modifier = stepAside) {
                AnimatedVisibility(
                    visible = showNavigation,
                    enter = fadeIn(Motion.fade(Motion.CONTENT)),
                    exit = fadeOut(Motion.fade(Motion.MICRO)),
                ) {
                    LibrarySearchButton(onClick = onSearch, active = searching)
                }
            }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val margin = BottomChrome.horizontalMargin.roundToPx()
        val gap = Spacing.sm.roundToPx()
        val action = ComponentSize.chromeAction.roundToPx()
        val bottomPad = Spacing.sm.roundToPx()
        val (miniMeasurable, navMeasurable, searchMeasurable) = measurables

        // Each child is measured once, at its width for the current state.
        val miniCompactWidth = (width - 2 * (action + gap)).coerceAtLeast(0)
        val requested = if (showNavigation) compact().coerceIn(0f, 1f) else 0f
        val mini = miniMeasurable.measure(Constraints.fixedWidth(lerp(width.toFloat(), miniCompactWidth.toFloat(), requested).roundToInt()))
        // Nothing playing: there is no mini player to bring into the row, so stay expanded.
        val c = if (mini.height > 0) requested else 0f
        val navExpandedWidth = (width - 2 * margin - action - gap).coerceAtLeast(0)
        val nav = navMeasurable.measure(Constraints.fixedWidth(lerp(navExpandedWidth.toFloat(), action.toFloat(), c).roundToInt()))
        val hasNav = nav.height > 0
        val search = searchMeasurable.measure(if (hasNav) Constraints.fixed(action, nav.height) else Constraints.fixed(0, 0))

        val expandedHeight = mini.height + if (hasNav) nav.height + bottomPad else 0
        val condensedHeight =
            if (hasNav && mini.height > 0) {
                max(mini.height, nav.height)
            } else {
                expandedHeight
            }
        val currentHeight = lerp(expandedHeight.toFloat(), condensedHeight.toFloat(), c).roundToInt()

        layout(width, currentHeight) {
            val navExpandedY = mini.height
            val navCondensedY = (condensedHeight - nav.height) / 2
            val navY = lerp(navExpandedY.toFloat(), navCondensedY.toFloat(), c).roundToInt()
            nav.placeRelative(margin, navY)

            val searchExpandedY = mini.height
            val searchCondensedY = (condensedHeight - search.height) / 2
            val searchY = lerp(searchExpandedY.toFloat(), searchCondensedY.toFloat(), c).roundToInt()
            search.placeRelative(width - margin - action, searchY)

            val miniExpandedY = 0
            val miniCondensedY = (condensedHeight - mini.height) / 2
            val miniY = lerp(miniExpandedY.toFloat(), miniCondensedY.toFloat(), c).roundToInt()
            val miniX = lerp(0f, (action + gap).toFloat(), c).roundToInt()
            mini.placeRelative(miniX, miniY)
        }
    }
}

/** The library controls fade over this part of the player expansion. */
private const val CONTROLS_FADE_SPAN = 0.3f

/** As the player expands, the library controls sink and fade: the player owns the screen. */
private fun Modifier.stepAsideForPlayer(transition: PlayerTransitionState?): Modifier =
    graphicsLayer {
        val p = transition?.progress?.value ?: 0f
        translationY = p * size.height
        alpha = (1f - p / CONTROLS_FADE_SPAN).coerceIn(0f, 1f)
    }
