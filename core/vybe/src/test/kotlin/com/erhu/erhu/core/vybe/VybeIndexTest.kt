package com.erhu.erhu.core.vybe

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VybeIndexTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun index(
        model: String = "gemma",
        dimension: Int = 3,
    ) = VybeIndexFile(tmp.root.resolve("vybe.idx"), model, dimension)

    private fun song(
        id: Long,
        hash: Long,
        vararg v: Float,
    ) = IndexedSong(id, hash, v)

    @Test
    fun recordsAreAppended_andTheLastOneOfASongWins() {
        val index = index()
        index.append(listOf(song(1, 10, 1f, 0f, 0f), song(2, 20, 0f, 1f, 0f)))
        index.append(listOf(song(1, 11, 0f, 0f, 1f)))
        assertEquals(mapOf(1L to 11L, 2L to 20L), index.entries())
        assertArrayEquals(floatArrayOf(0f, 0f, 1f), index.vectorOf(1), INT8_TOLERANCE)
        assertEquals(3, index.recordCount())
    }

    @Test
    fun ranking_usesOnlyLiveCurrentVectors_bestFirst() {
        val index = index()
        index.append(listOf(song(1, 10, 1f, 0f, 0f), song(2, 20, 0.9f, 0.1f, 0f), song(3, 30, 0f, 1f, 0f)))
        index.append(listOf(song(1, 11, 0f, 0f, 1f)))
        val query = floatArrayOf(1f, 0f, 0f)
        // Song 1's current text (hash 11) points away; song 3 is no longer in the library.
        val live = mapOf(1L to 11L, 2L to 20L)
        val ranked = index.rank(live, limit = 5) { _, v -> cosineSimilarity(query, v) }
        assertEquals(listOf(2L, 1L), ranked.map { it.songId })
        // A song whose stored hash is stale (its text changed) isn't ranked until re-embedded.
        assertEquals(listOf(2L), index.rank(mapOf(1L to 12L, 2L to 20L), 5) { _, v -> cosineSimilarity(query, v) }.map { it.songId })
        assertEquals(1, index.rank(live, limit = 1) { _, v -> cosineSimilarity(query, v) }.size)
    }

    @Test
    fun compaction_keepsOnlyLiveRecords() {
        val index = index()
        index.append(listOf(song(1, 10, 1f, 0f, 0f), song(2, 20, 0f, 1f, 0f)))
        index.append(listOf(song(1, 11, 0f, 0f, 1f)))
        index.compact(mapOf(1L to 11L))
        assertEquals(1, index.recordCount())
        assertEquals(mapOf(1L to 11L), index.entries())
        assertArrayEquals(floatArrayOf(0f, 0f, 1f), index.vectorOf(1), INT8_TOLERANCE)
    }

    @Test
    fun anotherModelsFile_isIgnoredAndReplaced() {
        index(model = "other").append(listOf(song(1, 10, 1f, 0f, 0f)))
        val index = index(model = "gemma")
        assertTrue(index.entries().isEmpty())
        index.append(listOf(song(2, 20, 0f, 1f, 0f)))
        assertEquals(mapOf(2L to 20L), index.entries())
        assertTrue(index(dimension = 4).entries().isEmpty())
    }

    @Test
    fun aRecordCutShortByAnInterruptedWrite_isIgnored() {
        val index = index()
        index.append(listOf(song(1, 10, 1f, 0f, 0f), song(2, 20, 0f, 1f, 0f)))
        val file = tmp.root.resolve("vybe.idx")
        file.writeBytes(file.readBytes().copyOf(file.length().toInt() - 5))
        assertEquals(mapOf(1L to 10L), index.entries())
        assertNull(index.vectorOf(2))
    }

    @Test
    fun textHash_followsTheText() {
        assertEquals(textHash("Genres: trap"), textHash("Genres: trap"))
        assertTrue(textHash("Genres: trap") != textHash("Genres: trap metal"))
    }

    @Test
    fun vectorsAreStoredAsInt8_aQuarterOfFloat32() {
        val dimension = 768
        val index = index(dimension = dimension)
        index.append(List(10) { song(it.toLong(), it.toLong(), *unitVector(dimension, it)) })
        val header = 8 + 4 + 4 + 2 + "gemma".length
        assertEquals((header + 10 * (16 + 4 + dimension)).toLong(), tmp.root.resolve("vybe.idx").length())
    }

    @Test
    fun int8Vectors_keepCosineScoresWithinTwoThousandths() {
        val dimension = 768
        val songs = List(200) { song(it.toLong(), it.toLong(), *unitVector(dimension, it)) }
        val index = index(dimension = dimension)
        index.append(songs)
        val query = unitVector(dimension, 1_000)
        val live = songs.associate { it.songId to it.textHash }
        val stored = index.vectorsOf(live, live.keys)
        songs.forEach { song ->
            val exact = cosineSimilarity(query, song.vector)!!
            val quantized = cosineSimilarity(query, stored.getValue(song.songId))!!
            assertEquals(exact, quantized, 2e-3f)
        }
    }

    @Test
    fun aFloat32FileOfTheSameModel_isConvertedInPlace_withoutReEmbedding() {
        val file = tmp.root.resolve("vybe.idx")
        java.io.DataOutputStream(file.outputStream()).use { out ->
            out.writeBytes("VYBEIDX1")
            out.writeInt(1)
            out.writeInt(3)
            out.writeUTF("gemma")
            listOf(1L to floatArrayOf(1f, 0f, 0f), 2L to floatArrayOf(0f, 0.6f, 0.8f)).forEach { (id, vector) ->
                out.writeLong(id)
                out.writeLong(id * 10)
                vector.forEach(out::writeFloat)
            }
        }
        val legacyLength = file.length()
        assertEquals(3, VybeIndexFile.storedDimension(file, "gemma"))
        val index = index()
        assertEquals(mapOf(1L to 10L, 2L to 20L), index.entries())
        assertArrayEquals(floatArrayOf(0f, 0.6f, 0.8f), index.vectorOf(2), INT8_TOLERANCE)
        assertTrue(file.length() < legacyLength)
        assertEquals(2, index.recordCount())
        // Appending after the conversion keeps the converted records.
        index.append(listOf(song(3, 30, 0f, 0f, 1f)))
        assertEquals(setOf(1L, 2L, 3L), index.entries().keys)
    }

    @Test
    fun aZeroVector_staysZero() {
        val index = index()
        index.append(listOf(song(1, 10, 0f, 0f, 0f)))
        assertArrayEquals(floatArrayOf(0f, 0f, 0f), index.vectorOf(1), 0f)
    }

    /** A deterministic pseudo-random unit vector, like a real (L2-normalised) embedding. */
    private fun unitVector(
        dimension: Int,
        seed: Int,
    ): FloatArray {
        val random = java.util.Random(seed.toLong())
        val v = FloatArray(dimension) { random.nextGaussian().toFloat() }
        val norm = kotlin.math.sqrt(v.sumOf { it.toDouble() * it }).toFloat()
        return FloatArray(dimension) { v[it] / norm }
    }

    private companion object {
        const val INT8_TOLERANCE = 0.01f
    }
}
