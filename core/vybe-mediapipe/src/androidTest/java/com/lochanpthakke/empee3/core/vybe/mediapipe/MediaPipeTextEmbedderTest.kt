package com.lochanpthakke.empee3.core.vybe.mediapipe

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lochanpthakke.empee3.core.vybe.EmbeddingRole
import com.lochanpthakke.empee3.core.vybe.SongText
import com.lochanpthakke.empee3.core.vybe.VybeSearch
import com.lochanpthakke.empee3.core.vybe.cosineSimilarity
import org.junit.AfterClass
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.sqrt

/**
 * Runs the real model on a device. Skipped unless the user has placed the model bundle at
 * `Android/data/<test package>/files/embedding_gemma.task` (see core/vybe/README.md).
 */
@RunWith(AndroidJUnit4::class)
class MediaPipeTextEmbedderTest {
    @Test
    fun embeddingsHaveTheModelDimensionAndUnitLength() {
        val vector = embedder.embed("late night music", EmbeddingRole.QUERY)
        assertEquals(EXPECTED_DIMENSION, embedder.dimension)
        assertEquals(EXPECTED_DIMENSION, vector.size)
        val norm = sqrt(vector.sumOf { it.toDouble() * it })
        assertEquals(1.0, norm, NORM_TOLERANCE)
    }

    @Test
    fun theSameTextGivesTheSameVector() {
        assertArrayEquals(
            embedder.embed("Title: Midnight Drive", EmbeddingRole.DOCUMENT),
            embedder.embed("Title: Midnight Drive", EmbeddingRole.DOCUMENT),
            0f,
        )
    }

    @Test
    fun relatedTextIsCloserThanUnrelatedText() {
        val query = embedder.embed("late night music", EmbeddingRole.QUERY)
        val related = embedder.embed("Title: Midnight Drive. Genre: Synthwave. Lyrics: city lights after dark", EmbeddingRole.DOCUMENT)
        val unrelated = embedder.embed("Title: Morning Workout. Genre: EDM. Lyrics: wake up and run", EmbeddingRole.DOCUMENT)
        assertTrue(cosineSimilarity(query, related)!! > cosineSimilarity(query, unrelated)!!)
    }

    @Test
    fun searchRanksTheMatchingSongFirst() {
        val songs =
            listOf(
                SongText(1, "Morning Workout", "A", "Gym", genre = "EDM", lyrics = "wake up and run"),
                SongText(2, "Midnight Drive", "B", "After Dark", genre = "Synthwave", lyrics = "city lights after dark"),
                SongText(3, "Sunday Brunch", "C", "Daylight", genre = "Acoustic"),
            )
        assertEquals(2L, VybeSearch(embedder).search("late night music", songs, limit = 3).first().songId)
    }

    companion object {
        private const val MODEL_FILE = "embedding_gemma.task"
        private const val EXPECTED_DIMENSION = 768
        private const val NORM_TOLERANCE = 1e-3

        private var loaded: MediaPipeTextEmbedder? = null

        @AfterClass
        @JvmStatic
        fun close() {
            loaded?.close()
        }
    }

    private val embedder: MediaPipeTextEmbedder get() = loaded!!

    /** Per test (not per class): the Android runner expects a result for every test, skipped or not. */
    @Before
    fun load() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val model = File(context.getExternalFilesDir(null), MODEL_FILE)
        assumeTrue("Model not placed at ${model.path}; skipping real-model tests", model.isFile)
        if (loaded == null) loaded = MediaPipeTextEmbedder.fromFile(context, model)
    }
}
