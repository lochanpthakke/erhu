package com.erhu.erhu.core.network.artwork

import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in live evaluation: same production pipeline, with a JVM image decoder. No music writes. */
class ArtworkAccuracyRunTest {
    @Test fun evaluateLiveDataset() = runBlocking {
        val path = System.getenv("ERHU_ARTWORK_ACCURACY_DATASET")
        assumeTrue("Live network evaluation is opt-in", path != null)
        val dataset = File(requireNotNull(path))
        val output = (System.getenv("ERHU_ARTWORK_ACCURACY_OUTPUT")?.let(::File) ?: File(dataset.parentFile, "results")).apply { mkdirs() }
        val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
        val finder = AlbumArtworkFinder(OkHttpClient(), json, AlbumArtworkCache(File(output, "cache")), JvmArtworkInspector, audioDbKey = "123")
        val cases = json.parseToJsonElement(dataset.readText()).jsonArray
        for ((index, element) in cases.withIndex()) {
            val row = element.jsonObject
            val query = json.decodeFromJsonElement<AlbumArtworkQuery>(row.getValue("query"))
            val result = finder.search(query)
            val report = buildJsonObject {
                put("case", element)
                put("elapsedMs", result.elapsedMs)
                put("fromCache", result.fromCache)
                put("recommended", result.recommended?.let { json.encodeToJsonElement(it) } ?: JsonNull)
                put("candidates", json.encodeToJsonElement(result.candidates))
                putJsonArray("outcomes") { result.outcomes.forEach { outcome -> addJsonObject { put("provider", outcome.providerName); put("status", outcome.status.name); put("count", outcome.candidateCount); put("error", outcome.errorMessage) } } }
            }
            File(output, "%02d.json".format(index)).writeText(report.toString())
            println("ARTWORK CASE $index: ${query.album}: ${result.candidates.size} candidates, ${result.elapsedMs}ms")
        }
    }
}
