package com.erhu.erhu.core.playback

import kotlin.random.Random

/**
 * The upcoming part of a queue (everything after [currentIndex]) in a new random order,
 * or null when fewer than two entries are upcoming. Entries are moved, never merged, so
 * duplicates survive; entries at or before [currentIndex] (the current and already-played
 * part) are not included and stay where they are.
 */
internal fun <T> shuffledUpcoming(
    queue: List<T>,
    currentIndex: Int,
    random: Random = Random.Default,
): List<T>? {
    val start = (currentIndex + 1).coerceAtLeast(0)
    if (queue.size - start < 2) return null
    return queue.subList(start, queue.size).shuffled(random)
}

/**
 * Where the upcoming part of a queue of [queueSize] entries starts: right after the current
 * entry (an out-of-range current index is clamped). The current and played entries stay put.
 */
internal fun upcomingStart(
    queueSize: Int,
    currentIndex: Int,
): Int = if (queueSize == 0) 0 else currentIndex.coerceIn(0, queueSize - 1) + 1

/** A queue entry taken out of the queue, kept so it can be put back exactly where it was. */
internal data class QueueRemoval<T>(
    val index: Int,
    val item: T,
)

/**
 * Undo stack of queue removals, newest last. Each entry keeps the removed item itself (not a
 * song id), so a duplicate comes back as the same entry with its own metadata. Undoing in
 * reverse order restores every position exactly; rapid removals simply stack up.
 */
internal class QueueRemovals<T>(
    private val capacity: Int = DEFAULT_CAPACITY,
) {
    private val stack = ArrayDeque<QueueRemoval<T>>()

    val size: Int get() = stack.size

    fun push(
        index: Int,
        item: T,
    ) {
        stack.addLast(QueueRemoval(index, item))
        while (stack.size > capacity) stack.removeFirst()
    }

    fun pop(): QueueRemoval<T>? = stack.removeLastOrNull()

    /** The queue was replaced as a whole: old positions mean nothing any more. */
    fun clear() = stack.clear()

    private companion object {
        const val DEFAULT_CAPACITY = 50
    }
}
