package com.erhu.erhu.core.vybe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VybeTasteTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val file get() = tmp.root.resolve("vybe.idx")

    private fun index(dimension: Int = 3) = VybeIndexFile(file, "gemma", dimension)

    private fun song(
        id: Long,
        vararg v: Float,
    ) = IndexedSong(id, id * 10, v)

    private val live = (1L..6L).associateWith { it * 10 }

    @Test
    fun theStoredDimensionIsReadWithoutTheModel_andOnlyForTheSameModel() {
        assertNull(VybeIndexFile.storedDimension(file, "gemma"))
        index(dimension = 3).append(listOf(song(1, 1f, 0f, 0f)))
        assertEquals(3, VybeIndexFile.storedDimension(file, "gemma"))
        assertNull(VybeIndexFile.storedDimension(file, "another-model"))
    }

    @Test
    fun neighboursFollowTheWeightedTaste_seedsLeftOut() {
        val index = index()
        index.append(
            listOf(
                song(1, 1f, 0f, 0f), // played a lot
                song(2, 0f, 1f, 0f), // played a little
                song(3, 0.95f, 0.05f, 0f), // close to song 1
                song(4, 0.05f, 0.95f, 0f), // close to song 2
                song(5, 0f, 0f, 1f), // unrelated
            ),
        )
        val ranked = tasteNeighbours(index, live, seeds = mapOf(1L to 5f, 2L to 1f), limit = 3).map { it.songId }
        assertEquals(listOf(3L, 4L, 5L), ranked)
        // Another listener's weights turn the order around.
        assertEquals(4L, tasteNeighbours(index, live, mapOf(1L to 1f, 2L to 5f), 3).first().songId)
    }

    @Test
    fun noUsableSeeds_noNeighbours() {
        val index = index()
        index.append(listOf(song(1, 1f, 0f, 0f), song(2, 0f, 1f, 0f)))
        assertTrue(tasteNeighbours(index, live, emptyMap(), 5).isEmpty())
        assertTrue(tasteNeighbours(index, live, mapOf(9L to 1f), 5).isEmpty())
        assertTrue(tasteNeighbours(index, live, mapOf(1L to 0f), 5).isEmpty())
    }

    @Test
    fun vectorsOfReadsOnlyCurrentRecordsInOnePass() {
        val index = index()
        index.append(listOf(song(1, 1f, 0f, 0f), song(2, 0f, 1f, 0f)))
        index.append(listOf(IndexedSong(1, 11, floatArrayOf(0f, 0f, 1f))))
        val found = index.vectorsOf(mapOf(1L to 11L, 2L to 20L), setOf(1L, 2L))
        assertEquals(listOf(0f, 0f, 1f), found.getValue(1).toList())
        assertEquals(listOf(0f, 1f, 0f), found.getValue(2).toList())
    }
}
