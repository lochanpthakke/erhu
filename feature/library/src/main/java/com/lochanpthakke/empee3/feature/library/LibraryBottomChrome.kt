package com.lochanpthakke.empee3.feature.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.util.lerp
import com.lochanpthakke.empee3.core.designsystem.component.BottomChrome
import com.lochanpthakke.empee3.core.designsystem.foundation.ComponentSize
import com.lochanpthakke.empee3.core.designsystem.foundation.Motion
import com.lochanpthakke.empee3.core.designsystem.foundation.Spacing
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
 * The chrome reports its expanded height throughout, so content padding does
 * not change while it moves.
 *
 * Without [showNavigation] (other screens) it is just the mini player.
 */
@Composable
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
    Layout(
        modifier = modifier.fillMaxWidth(),
        // Each slot is wrapped in a Box, so the layout always has exactly three
        // children even while a slot's content is hidden (it then measures 0 high).
        content = {
            Box { miniPlayer(compact) }
            Box {
                AnimatedVisibility(
                    visible = showNavigation,
                    enter = fadeIn(Motion.fade(Motion.DURATION_MEDIUM)),
                    exit = fadeOut(Motion.fade(Motion.DURATION_QUICK)),
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
            Box {
                AnimatedVisibility(
                    visible = showNavigation,
                    enter = fadeIn(Motion.fade(Motion.DURATION_MEDIUM)),
                    exit = fadeOut(Motion.fade(Motion.DURATION_QUICK)),
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

        // Each child is measured once, at its width for the current state. Heights do
        // not depend on width (single-line content), so the chrome's height is the
        // same in every state and content padding never changes while it moves.
        val miniCompactWidth = (width - 2 * (action + gap)).coerceAtLeast(0)
        val requested = if (showNavigation) compact().coerceIn(0f, 1f) else 0f
        val mini = miniMeasurable.measure(Constraints.fixedWidth(lerp(width.toFloat(), miniCompactWidth.toFloat(), requested).roundToInt()))
        // Nothing playing: there is no mini player to bring into the row, so stay expanded.
        val c = if (mini.height > 0) requested else 0f
        val miniC = c
        val navExpandedWidth = (width - 2 * margin - action - gap).coerceAtLeast(0)
        val nav = navMeasurable.measure(Constraints.fixedWidth(lerp(navExpandedWidth.toFloat(), action.toFloat(), c).roundToInt()))
        val hasNav = nav.height > 0
        val search = searchMeasurable.measure(if (hasNav) Constraints.fixed(action, nav.height) else Constraints.fixed(0, 0))

        val height = mini.height + if (hasNav) nav.height + bottomPad else 0
        layout(width, height) {
            nav.placeRelative(margin, mini.height)
            search.placeRelative(width - margin - action, mini.height)
            // The mini player rises from above the row into the row, between the two buttons.
            val miniCompactY = mini.height + nav.height / 2 - mini.height / 2
            mini.placeRelative(
                x = lerp(0f, (action + gap).toFloat(), miniC).roundToInt(),
                y = lerp(0f, miniCompactY.toFloat(), miniC).roundToInt(),
            )
        }
    }
}
