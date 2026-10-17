package com.lochanpthakke.empee3.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import com.lochanpthakke.empee3.core.common.perf.Interactions

/*
 * Compose side of the frame diagnostics (see Interactions). Everything here is skipped when no
 * diagnostics sink is installed, i.e. outside debug builds.
 */

/**
 * Reports [key] to frame diagnostics with the label [value] computes (null = not active) while
 * this is composed. [value] is read in an effect, not in composition, so it may read state that
 * changes every frame (e.g. the player's progress) without recomposing the caller.
 */
@Composable
fun ReportInteraction(
    key: String,
    value: () -> String?,
) {
    if (Interactions.sink == null) return
    val current by rememberUpdatedState(value)
    LaunchedEffect(key) {
        try {
            snapshotFlow { current() }.collect { Interactions.put(key, it) }
        } finally {
            Interactions.put(key, null)
        }
    }
}

/**
 * Reports any list or grid inside this one being dragged or flung ([Interactions.SCROLL]):
 * nested scrolling passes every child scroll through here, so one modifier on the shell covers
 * all screens. Which list it was comes from [Interactions.SCREEN].
 */
fun Modifier.reportScrolling(): Modifier = if (Interactions.sink == null) this else nestedScroll(ScrollReporter)

private object ScrollReporter : NestedScrollConnection {
    private var active: String? = null

    override fun onPreScroll(
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        if (source == NestedScrollSource.UserInput) report("drag")
        return Offset.Zero
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        report("fling")
        return Velocity.Zero
    }

    override suspend fun onPostFling(
        consumed: Velocity,
        available: Velocity,
    ): Velocity {
        report(null)
        return Velocity.Zero
    }

    private fun report(state: String?) {
        if (state == active) return
        active = state
        Interactions.put(Interactions.SCROLL, state)
    }
}
