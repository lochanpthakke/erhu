package com.lochanpthakke.empee3.feature.library

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.lochanpthakke.empee3.core.designsystem.foundation.Motion

/** Keeps each library section/shelf's scroll state while its replacement fades in. */
@Composable
internal fun LibraryContentMotion(
    section: LibrarySection,
    shelf: LibraryShelf?,
    stateHolder: SaveableStateHolder,
    content: @Composable (LibrarySection, LibraryShelf?) -> Unit,
) {
    val destination = section to shelf
    Crossfade(
        targetState = destination,
        animationSpec = Motion.fade(Motion.CONTENT),
        label = "libraryContent",
        modifier = Modifier.fillMaxSize(),
    ) { shown ->
        // An outgoing list is visible briefly, but cannot accept another tap
        // or retain accessibility focus after the selected destination changes.
        val outgoing =
            if (shown != destination) {
                Modifier.clearAndSetSemantics {}.pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                        }
                    }
                }
            } else {
                Modifier
            }
        stateHolder.SaveableStateProvider("${shown.first.name}:${shown.second?.name.orEmpty()}") {
            Box(modifier = Modifier.fillMaxSize().then(outgoing)) {
                content(shown.first, shown.second)
            }
        }
    }
}
