package com.lochanpthakke.empee3.core.playback

import com.lochanpthakke.empee3.core.model.PersistedPlaybackState
import com.lochanpthakke.empee3.core.model.RepeatMode
import com.lochanpthakke.empee3.core.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RestoredQueueTest {
    private fun song(id: Long) = Song(id = id, title = "t$id", artist = "a", album = "b", durationMs = 1000, uri = "content://x/$id")

    private fun state(
        ids: List<Long>,
        index: Int,
        position: Long = 42_000L,
    ) = PersistedPlaybackState(ids, index, position, shuffleModeEnabled = false, repeatMode = RepeatMode.OFF)

    @Test
    fun emptySnapshot_restoresNothing() {
        assertNull(buildRestoredQueue(state(emptyList(), 0), listOf(song(1))))
    }

    @Test
    fun allSongsGone_restoresNothing() {
        assertNull(buildRestoredQueue(state(listOf(1, 2), 0), emptyList()))
    }

    @Test
    fun keepsQueueOrder_regardlessOfLookupOrder() {
        val restored = buildRestoredQueue(state(listOf(3, 1, 2), 1), listOf(song(1), song(2), song(3)))!!
        assertEquals(listOf(3L, 1L, 2L), restored.songs.map { it.id })
        assertEquals(1, restored.startIndex)
        assertEquals(42_000L, restored.startPositionMs)
    }

    @Test
    fun deletedSongsBeforeCurrent_shiftIndexButKeepPosition() {
        val restored = buildRestoredQueue(state(listOf(1, 2, 3, 4), 2), listOf(song(1), song(3), song(4)))!!
        assertEquals(listOf(1L, 3L, 4L), restored.songs.map { it.id })
        assertEquals(1, restored.startIndex)
        assertEquals(42_000L, restored.startPositionMs)
    }

    @Test
    fun deletedCurrentSong_startsNextSurvivorFromZero() {
        val restored = buildRestoredQueue(state(listOf(1, 2, 3), 1), listOf(song(1), song(3)))!!
        assertEquals(listOf(1L, 3L), restored.songs.map { it.id })
        assertEquals(1, restored.startIndex)
        assertEquals(0L, restored.startPositionMs)
    }

    @Test
    fun deletedLastCurrentSong_fallsBackToFirst() {
        val restored = buildRestoredQueue(state(listOf(1, 2, 3), 2), listOf(song(1), song(2)))!!
        assertEquals(0, restored.startIndex)
        assertEquals(0L, restored.startPositionMs)
    }

    @Test
    fun duplicateSongsInQueue_resolveToTheRightOccurrence() {
        val restored = buildRestoredQueue(state(listOf(5, 6, 5), 2), listOf(song(5), song(6)))!!
        assertEquals(listOf(5L, 6L, 5L), restored.songs.map { it.id })
        assertEquals(2, restored.startIndex)
    }

    @Test
    fun outOfRangeIndex_fallsBackToFirstWithoutCrashing() {
        val restored = buildRestoredQueue(state(listOf(1, 2), 9), listOf(song(1), song(2)))!!
        assertEquals(0, restored.startIndex)
        assertEquals(0L, restored.startPositionMs)
    }

    @Test
    fun negativePosition_isClamped() {
        val restored = buildRestoredQueue(state(listOf(1), 0, position = -5), listOf(song(1)))!!
        assertEquals(0L, restored.startPositionMs)
    }
}
