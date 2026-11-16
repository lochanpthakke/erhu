package com.erhu.erhu.feature.library

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

/** The bottom bar sends requests only to the active destination. */
val LocalLibraryScrollToTopRequests = staticCompositionLocalOf<Flow<Unit>?> { null }

@Composable
internal fun ScrollToTopOnReselect(state: LazyListState) {
    val requests = LocalLibraryScrollToTopRequests.current ?: return
    LaunchedEffect(requests, state) {
        requests.collectLatest {
            if (state.canScrollBackward) state.animateScrollToItem(0)
        }
    }
}

@Composable
internal fun ScrollToTopOnReselect(state: LazyGridState) {
    val requests = LocalLibraryScrollToTopRequests.current ?: return
    LaunchedEffect(requests, state) {
        requests.collectLatest {
            if (state.canScrollBackward) state.animateScrollToItem(0)
        }
    }
}
