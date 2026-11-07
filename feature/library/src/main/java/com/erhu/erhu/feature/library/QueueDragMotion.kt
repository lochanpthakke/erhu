package com.erhu.erhu.feature.library

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.erhu.erhu.core.designsystem.foundation.Motion
import com.erhu.erhu.core.model.Song
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

private const val QUEUE_LANDING_THRESHOLD = 0.1f
private const val QUEUE_HANDOFF_TIMEOUT = 1000L

/** Visual-only reorder session: hold the old rows until landing and the real queue update agree. */
@Stable
internal class QueueDragMotion(
    private val rowHeight: Float,
) {
    var rows by mutableStateOf<List<Song>?>(null)
        private set
    var index by mutableStateOf<Int?>(null)
        private set
    var offset by mutableFloatStateOf(0f)
        private set
    var settling by mutableStateOf(false)
        private set
    var handoff by mutableIntStateOf(0)
        private set
    private var generation = 0

    fun begin(
        queue: List<Song>,
        at: Int,
    ) {
        if (index != null || at !in queue.indices) return
        generation++
        rows = queue.toList()
        index = at
        offset = 0f
    }

    fun drag(
        at: Int,
        delta: Float,
    ) {
        if (index == at && !settling && delta.isFinite()) offset += delta
    }

    fun queueChanged(queue: List<Song>) {
        if (!settling && rows != null && rows != queue) reset()
    }

    suspend fun finish(
        commit: Boolean,
        latestQueue: () -> List<Song>,
        reorder: suspend (Int, Int) -> Boolean,
    ) {
        val from = index
        val original = rows
        if (from == null || original == null || settling) return
        val session = generation
        settling = true
        val to = if (commit) queueDropIndex(from, offset, rowHeight, original.lastIndex) else from
        try {
            Animatable(offset).animateTo((to - from) * rowHeight, Motion.gestureSettle(QUEUE_LANDING_THRESHOLD)) {
                if (generation == session) offset = value
            }
            val canCommit = generation == session && commit && from != to
            if (canCommit && latestQueue() == original && reorder(from, to)) {
                val expected = original.toMutableList().apply { add(to, removeAt(from)) }
                // Keep the landed visual in place while the controller's queue reaches Compose.
                withTimeoutOrNull(QUEUE_HANDOFF_TIMEOUT) {
                    snapshotFlow { latestQueue() }.first { it == expected || it != original }
                }
                handoff++
            }
        } finally {
            if (generation == session) reset()
        }
    }

    private fun reset() {
        generation++
        rows = null
        index = null
        offset = 0f
        settling = false
    }
}

internal fun queueDropIndex(
    from: Int,
    offset: Float,
    rowHeight: Float,
    last: Int,
): Int = (from + (offset / rowHeight).roundToInt()).coerceIn(0, last)
