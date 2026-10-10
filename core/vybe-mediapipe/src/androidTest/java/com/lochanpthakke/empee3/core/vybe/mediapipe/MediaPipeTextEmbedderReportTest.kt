package com.lochanpthakke.empee3.core.vybe.mediapipe

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lochanpthakke.empee3.core.vybe.EmbeddingRole
import com.lochanpthakke.empee3.core.vybe.SongDescriber
import com.lochanpthakke.empee3.core.vybe.SongText
import com.lochanpthakke.empee3.core.vybe.cosineSimilarity
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale
import kotlin.math.sqrt

/**
 * Measures the real model (logged under [TAG]): dimensions, norms, finiteness, latency, and a
 * small query × song similarity table. Diagnostic only; skipped without the model.
 */
@RunWith(AndroidJUnit4::class)
class MediaPipeTextEmbedderReportTest {
    @Test
    fun reportRealEmbeddings() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val model = File(context.getExternalFilesDir(null), "embedding_gemma.task")
        assumeTrue(model.isFile)
        val loadStart = SystemClock.elapsedRealtime()
        MediaPipeTextEmbedder.fromFile(context, model).use { embedder ->
            Log.i(TAG, "load ms=${SystemClock.elapsedRealtime() - loadStart} dimension=${embedder.dimension}")
            val songs =
                listOf(
                    SongText(1, "Midnight Drive", "B", "After Dark", genre = "Synthwave", lyrics = "city lights after dark"),
                    SongText(2, "Morning Workout", "A", "Gym", genre = "EDM", lyrics = "wake up and run"),
                    SongText(3, "Rainy Window", "C", "Grey Days", genre = "Lo-fi", lyrics = "quiet rain on the glass, slow and sad"),
                    SongText(4, "Party Starter", "D", "Weekend", genre = "Dance", lyrics = "turn it up, everybody jump"),
                )
            val documents =
                songs.map { song ->
                    val start = SystemClock.elapsedRealtime()
                    val vector = embedder.embed(SongDescriber.describe(song), EmbeddingRole.DOCUMENT)
                    val ms = SystemClock.elapsedRealtime() - start
                    check(vector, "doc ${song.title}", ms)
                    vector
                }
            val queries = listOf("late night music", "energetic workout songs", "sad rainy day music", "songs to dance at a party")
            queries.forEach { query ->
                val start = SystemClock.elapsedRealtime()
                val vector = embedder.embed(query, EmbeddingRole.QUERY)
                check(vector, "query '$query'", SystemClock.elapsedRealtime() - start)
                val row =
                    songs.indices.joinToString("  ") { i ->
                        "${songs[i].title}=" + String.format(Locale.ROOT, "%.3f", cosineSimilarity(vector, documents[i]))
                    }
                Log.i(TAG, "similarity '$query': $row")
            }
        }
    }

    private fun check(
        vector: FloatArray,
        label: String,
        ms: Long,
    ) {
        val norm = sqrt(vector.sumOf { it.toDouble() * it })
        val finite = vector.all { it.isFinite() }
        Log.i(TAG, "$label: dim=${vector.size} norm=${String.format(Locale.ROOT, "%.5f", norm)} finite=$finite ms=$ms")
        assertTrue(finite)
    }

    private companion object {
        const val TAG = "VybeReport"
    }
}
