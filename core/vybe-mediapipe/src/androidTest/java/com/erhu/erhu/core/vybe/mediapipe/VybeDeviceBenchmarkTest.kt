package com.erhu.erhu.core.vybe.mediapipe

import android.os.Debug
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.erhu.erhu.core.vybe.EmbeddingRole
import com.erhu.erhu.core.vybe.IndexedSong
import com.erhu.erhu.core.vybe.VybeIndexFile
import com.erhu.erhu.core.vybe.cosineSimilarity
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale
import java.util.Random

/**
 * Device measurements for VYBE (logged under [TAG]); diagnostic only, skipped without the model
 * in the test app's external files directory (see core/vybe/README.md).
 */
@RunWith(AndroidJUnit4::class)
class VybeDeviceBenchmarkTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val model = File(context.getExternalFilesDir(null), "embedding_gemma.task")

    private val documents =
        listOf(
            "Artist: Drake. Genres: Hip-Hop, R&B",
            "Artist: \$uicideboy\$. Genres: Hip-Hop, Horrorcore. Lyrics: dark clouds over me",
            "Artist: 1986 OMEGA TRIBE. Genres: City Pop, J-Pop",
            "Artist: Fleetwood Mac. Genres: Soft Rock, Pop Rock. Tempo: 120 BPM",
            "Artist: Tyler, The Creator. Genres: Hip-Hop, Neo Soul. Lyrics: flowers in the garden, boredom all summer",
        )
    private val queries = listOf("late night music", "energetic workout songs", "japanese city pop", "sad rainy day music")

    @Test
    fun cpuEmbedder() {
        assumeTrue(model.isFile)
        val before = memory()
        val start = SystemClock.elapsedRealtime()
        MediaPipeTextEmbedder.fromFile(context, model).use { embedder ->
            val loadMs = SystemClock.elapsedRealtime() - start
            val loaded = memory()
            embedder.embed("warm up", EmbeddingRole.QUERY)
            val doc = timings(ROUNDS) { documents.forEach { embedder.embed(it, EmbeddingRole.DOCUMENT) } }.map { it / documents.size }
            val query = timings(ROUNDS) { queries.forEach { embedder.embed(it, EmbeddingRole.QUERY) } }.map { it / queries.size }
            val after = memory()
            Log.i(
                TAG,
                "cpu load_ms=$loadMs model_mb=${model.length() / MB} doc_ms p50=${p(doc, 50)} p90=${p(doc, 90)} " +
                    "query_ms p50=${p(query, 50)} p90=${p(query, 90)} pss_mb before=${before.first} loaded=${loaded.first} " +
                    "after=${after.first} native_mb before=${before.second} loaded=${loaded.second} after=${after.second}",
            )
        }
    }

    @Test
    fun gpuEmbedder() {
        assumeTrue(model.isFile)
        val start = SystemClock.elapsedRealtime()
        val embedder =
            try {
                ParcelFileDescriptor.open(model, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                    TextEmbedder.createFromOptions(
                        context,
                        TextEmbedder.TextEmbedderOptions
                            .builder()
                            .setBaseOptions(
                                BaseOptions.builder().setModelAssetFileDescriptor(descriptor.fd).setDelegate(Delegate.GPU).build(),
                            ).setL2Normalize(true)
                            .build(),
                    )
                }
            } catch (e: RuntimeException) {
                Log.i(TAG, "gpu unavailable: ${e.message}")
                return
            }
        embedder.use {
            val loadMs = SystemClock.elapsedRealtime() - start
            it.embed("warm up")
            val doc = timings(ROUNDS) { documents.forEach { text -> it.embed(text) } }.map { ms -> ms / documents.size }
            Log.i(TAG, "gpu load_ms=$loadMs doc_ms p50=${p(doc, 50)} p90=${p(doc, 90)}")
        }
    }

    @Test
    fun indexRanking() {
        val dimension = 768
        val random = Random(1)
        for (songs in listOf(1_000, 10_000)) {
            val file = File(context.cacheDir, "bench-$songs.idx").apply { delete() }
            val index = VybeIndexFile(file, "bench", dimension)
            index.append(List(songs) { IndexedSong(it.toLong(), it.toLong(), FloatArray(dimension) { random.nextGaussian().toFloat() }) })
            val live = (0 until songs).associate { it.toLong() to it.toLong() }
            val query = FloatArray(dimension) { random.nextGaussian().toFloat() }
            val ms = timings(ROUNDS) { index.rank(live, LIMIT) { _, v -> cosineSimilarity(query, v) } }
            Log.i(TAG, "rank songs=$songs file_kb=${file.length() / KB} ms p50=${p(ms, 50)} p90=${p(ms, 90)}")
            file.delete()
        }
    }

    private fun timings(
        rounds: Int,
        block: () -> Unit,
    ): List<Double> =
        List(rounds) {
            val start = SystemClock.elapsedRealtimeNanos()
            block()
            (SystemClock.elapsedRealtimeNanos() - start) / NANOS_PER_MS
        }

    private fun p(
        values: List<Double>,
        percentile: Int,
    ): String {
        val sorted = values.sorted()
        return String.format(Locale.ROOT, "%.1f", sorted[(sorted.size - 1) * percentile / 100])
    }

    /** Process PSS and native heap, in MB. */
    private fun memory(): Pair<Long, Long> {
        val info = Debug.MemoryInfo().also(Debug::getMemoryInfo)
        return info.totalPss / KB to Debug.getNativeHeapAllocatedSize() / MB
    }

    private companion object {
        const val TAG = "VybeBench"
        const val ROUNDS = 10
        const val LIMIT = 50
        const val KB = 1024L
        const val MB = 1024L * 1024L
        const val NANOS_PER_MS = 1_000_000.0
    }
}
