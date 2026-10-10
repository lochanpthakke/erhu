package com.lochanpthakke.empee3.core.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

class QueueEditsTest {
    @Test
    fun `shuffle upcoming keeps the current and played entries in place`() {
        // Entries are (song id, entry #) so duplicates of song 7 stay distinguishable.
        val queue = listOf(1 to 0, 2 to 1, 7 to 2, 7 to 3, 3 to 4, 7 to 5, 4 to 6)
        val current = 2
        repeat(20) { seed ->
            val upcoming = shuffledUpcoming(queue, current, Random(seed))!!
            val result = queue.subList(0, current + 1) + upcoming
            assertEquals(queue.subList(0, current + 1), result.subList(0, current + 1))
            // Every upcoming entry survives exactly once, duplicates included.
            assertEquals(queue.subList(current + 1, queue.size).sortedBy { it.second }, upcoming.sortedBy { it.second })
            assertEquals(queue.size, result.size)
        }
    }

    @Test
    fun `shuffle upcoming needs at least two upcoming entries`() {
        assertNull(shuffledUpcoming(listOf(1, 2, 3), currentIndex = 2))
        assertNull(shuffledUpcoming(listOf(1, 2, 3), currentIndex = 1))
        assertNull(shuffledUpcoming(emptyList<Int>(), currentIndex = -1))
    }

    @Test
    fun `shuffle with no current item shuffles the whole queue`() {
        val upcoming = shuffledUpcoming(listOf(1, 2, 3), currentIndex = -1, random = Random(1))!!
        assertEquals(listOf(1, 2, 3), upcoming.sorted())
    }

    @Test
    fun `undoing rapid removals in reverse restores the exact queue`() {
        val original = listOf("a", "b#1", "c", "b#2", "d")
        val queue = original.toMutableList()
        val removals = QueueRemovals<String>()
        // Remove both duplicates and another entry quickly, by index as the UI does.
        listOf(3, 1, 2).forEach { index -> removals.push(index, queue.removeAt(index)) }
        assertEquals(listOf("a", "c"), queue)

        while (true) {
            val removal = removals.pop() ?: break
            queue.add(removal.index.coerceIn(0, queue.size), removal.item)
        }
        assertEquals(original, queue)
    }

    @Test
    fun `removals are bounded and cleared with the queue`() {
        val removals = QueueRemovals<Int>(capacity = 2)
        removals.push(0, 1)
        removals.push(0, 2)
        removals.push(0, 3)
        assertEquals(2, removals.size)
        assertEquals(3, removals.pop()?.item)
        removals.clear()
        assertNull(removals.pop())
    }
}
