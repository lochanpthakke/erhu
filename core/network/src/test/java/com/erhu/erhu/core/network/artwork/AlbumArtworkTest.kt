package com.erhu.erhu.core.network.artwork

import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

internal object JvmArtworkInspector : ArtworkImageInspector {
    override fun inspect(bytes: ByteArray): ArtworkImageFacts? = runCatching {
        val image = ImageIO.read(ByteArrayInputStream(bytes)) ?: return null
        if (image.width !in 64..12000 || image.height !in 64..12000 || image.width.toDouble() / image.height !in .60..1.65) return null
        val scaled = BufferedImage(9, 8, BufferedImage.TYPE_INT_RGB)
        scaled.createGraphics().apply { drawImage(image, 0, 0, 9, 8, null); dispose() }
        ArtworkImageFacts(image.width, image.height, artworkVisualHash(scaled.getRGB(0, 0, 9, 8, null, 0, 9)))
    }.getOrNull()
}

class AlbumArtworkTest {
    private val query = AlbumArtworkQuery("Discovery", "Daft Punk", kind = ArtworkReleaseKind.ALBUM)
    private fun candidate(album: String = "Discovery", artist: String = "Daft Punk") = AlbumArtworkCandidate("https://example.test/cover", "iTunes / Apple Search", album, artist, kind = ArtworkReleaseKind.ALBUM, width = 1000, height = 1000, verifiedImage = true)

    @Test fun exactTitleArtistCanRecommendButWrongArtistAndAlbumAreRejected() {
        assertTrue(AlbumArtworkRanking.rank(query, candidate())!!.canRecommend)
        assertNull(AlbumArtworkRanking.rank(query, candidate(artist = "Queen")))
        assertNull(AlbumArtworkRanking.rank(query, candidate(album = "Random Access Memories")))
    }
    @Test fun boxSetsAndDistantReleaseYearsRequireReview() {
        assertFalse(AlbumArtworkRanking.rank(query.copy(year = 1973), candidate().copy(year = 2011, edition = "Immersion box set"))!!.canRecommend)
        assertFalse(AlbumArtworkRanking.rank(query.copy(year = 1973), candidate().copy(year = 2011))!!.canRecommend)
        assertFalse(AlbumArtworkRanking.rank(query, candidate().copy(edition = "Promotion"))!!.canRecommend)
    }
    @Test fun missingAndIncorrectArtistsRequireReview() {
        assertFalse(AlbumArtworkRanking.rank(query.copy(artist = "Unknown artist"), candidate())!!.canRecommend)
        assertFalse(AlbumArtworkRanking.rank(query.copy(artist = "Various Artists"), candidate())!!.canRecommend)
    }
    @Test fun editionsAreRetainedForManualChoiceAndNeverRecommendedAsExact() {
        val alternate = AlbumArtworkRanking.rank(query, candidate("Discovery (Deluxe Edition)"))!!
        assertTrue("Alternate edition" in alternate.reviewReasons)
        assertFalse(alternate.canRecommend)
        assertFalse(AlbumArtworkRanking.rank(query.copy(album = "Discovery (2011 Remaster)"), candidate("Discovery (1997 Remaster)"))!!.canRecommend)
    }
    @Test fun albumSingleAndLowResolutionDoNotAutoSelect() {
        assertFalse(AlbumArtworkRanking.rank(query, candidate().copy(kind = ArtworkReleaseKind.SINGLE))!!.canRecommend)
        assertFalse(AlbumArtworkRanking.rank(query, candidate().copy(width = 100, height = 100))!!.canRecommend)
    }
    @Test fun punctuationUnicodeAndFeaturingAreNormalized() {
        assertEquals(1.0, AlbumArtworkRanking.similarity("Beyonc\u00e9", "Beyonce"), .001)
        assertTrue(AlbumArtworkRanking.rank(query.copy(artist = "Daft Punk feat. Pharrell"), candidate())!!.canRecommend)
        assertEquals("Discovery", AlbumArtworkRanking.searchTitle("Discovery (Deluxe Edition)"))
        assertEquals("Get Lucky", AlbumArtworkRanking.searchTitle("Get Lucky - Single"))
    }
    @Test fun duplicatesMergeSourcesWithoutMergingAlternateEditions() {
        val first = candidate().copy(contentHash = "same", score = 1.0)
        val second = first.copy(source = "Deezer")
        val deluxe = first.copy(album = "Discovery (Deluxe Edition)")
        val result = AlbumArtworkRanking.deduplicate(listOf(first, second, deluxe))
        assertEquals(2, result.size)
        assertEquals(listOf("Deezer"), result.first().otherSources)
    }
    @Test fun cacheSurvivesRecreationButNotChangedMetadataAndNeverUsesUnvalidatedImages() {
        val directory = kotlin.io.path.createTempDirectory("art-cache").toFile()
        val cache = AlbumArtworkCache(directory)
        val key = cache.key(query, "test")
        cache.saveImage(candidate().imageUrl, byteArrayOf(1))
        cache.write(key, listOf(candidate()))
        assertEquals(1, AlbumArtworkCache(directory).read(key)!!.size)
        assertNotEquals(key, cache.key(query.copy(artist = "Other"), "test"))
        cache.write(key, listOf(candidate().copy(verifiedImage = false)))
        assertNull(cache.read(key))
    }
    @Test fun brokenExactCoverFallsBackToGroupAndFailuresDoNotStopKeylessApple() = runBlocking {
        val server = MockWebServer()
        val id = "11111111-1111-1111-1111-111111111111"
        val group = "22222222-2222-2222-2222-222222222222"
        val png = ByteArrayOutputStream().apply { ImageIO.write(BufferedImage(600, 600, BufferedImage.TYPE_INT_RGB), "png", this) }.toByteArray()
        assertNotNull("Fixture PNG must decode", JvmArtworkInspector.inspect(png))
        server.start()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl!!.encodedPath
                return when {
                    path == "/mb/ws/2/recording/$id" -> MockResponse().setBody("""{"artist-credit":[{"name":"Different recording artist"}],"releases":[{"id":"$id","title":"Discovery","artist-credit":[{"name":"Daft Punk"}],"release-group":{"id":"$group","primary-type":"Album"}}]}""")
                    path == "/mb/ws/2/release/$id" -> MockResponse().setBody("""{"id":"$id","title":"Discovery","artist-credit":[{"name":"Daft Punk"}],"release-group":{"id":"$group","primary-type":"Album"}}""")
                    path == "/caa/release/$id" -> MockResponse().setBody("""{"images":[{"front":true,"back":false,"approved":true,"image":"${server.url("/bad.png")}"},{"front":false,"back":true,"approved":true,"image":"${server.url("/back.png")}"}]}""")
                    path == "/caa/release-group/$group" -> MockResponse().setBody("""{"images":[{"front":true,"back":false,"approved":true,"image":"${server.url("/good.png")}"}]}""")
                    path == "/bad.png" -> MockResponse().setResponseCode(404)
                    path == "/good.png" -> MockResponse().setHeader("Content-Type", "image/png").setBody(Buffer().write(png))
                    path == "/deezer/search/album" -> MockResponse().setBody("malformed JSON")
                    path == "/apple/search" -> MockResponse().setBody("""{"results":[{"collectionType":"Album","collectionName":"Discovery","artistName":"Daft Punk","artworkUrl100":"${server.url("/good.png")}"}]}""")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        try {
            val cache = AlbumArtworkCache(kotlin.io.path.createTempDirectory("pipeline").toFile())
            val endpoints = AlbumArtworkFinder.Endpoints(server.url("/mb/").toString(), server.url("/caa/").toString(), server.url("/deezer/").toString(), server.url("/apple/").toString(), server.url("/audiodb/").toString())
            val finder = AlbumArtworkFinder(OkHttpClient(), Json { ignoreUnknownKeys = true }, cache, JvmArtworkInspector, endpoints = endpoints, musicBrainzRequest = { it() })
            val input = query.copy(releaseId = id)
            val a = async { finder.search(input) }; val b = async { finder.search(input) }
            val result = a.await()
            assertTrue("No image: ${result.outcomes}", result.candidates.isNotEmpty())
            assertEquals("Unexpected candidates: ${result.candidates}", "Cover Art Archive release-group", result.candidates.first().source)
            assertEquals(600, result.candidates.first().width)
            assertNull(result.candidates.first().releaseId)
            assertFalse(result.candidates.first().canRecommend)
            assertTrue("Missing failure: ${result.outcomes}", result.outcomes.any { it.providerName == "Deezer" && it.status.name == "FAILED" })
            assertTrue(b.await().fromCache)
            val recording = finder.search(query.copy(recordingId = id))
            assertTrue("Recording credits must not overwrite an album's own artist", recording.outcomes.any { it.providerName == "MusicBrainz release identification" && it.candidateCount == 1 })
            val count = server.requestCount
            server.shutdown()
            assertTrue(finder.search(input).fromCache)
            assertEquals(count, server.requestCount)
        } finally { runCatching { server.shutdown() } }
    }
    @Test fun malformedImageAndRateLimitedAppleDoNotBecomeCandidates() = runBlocking {
        val server = MockWebServer()
        server.start()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path!!.startsWith("/mb/") -> MockResponse().setBody("{}")
                request.path!!.startsWith("/deezer/") -> MockResponse().setBody("""{"data":[{"title":"Discovery","artist":{"name":"Daft Punk"},"cover_xl":"${server.url("/html")}"}]}""")
                request.path!!.startsWith("/apple/") -> MockResponse().setResponseCode(429).setHeader("Retry-After", "60")
                else -> MockResponse().setHeader("Content-Type", "text/html").setBody("not an image")
            }
        }
        try {
            val endpoints = AlbumArtworkFinder.Endpoints(server.url("/mb/").toString(), server.url("/caa/").toString(), server.url("/deezer/").toString(), server.url("/apple/").toString())
            val finder = AlbumArtworkFinder(OkHttpClient(), Json, AlbumArtworkCache(kotlin.io.path.createTempDirectory("invalid-art").toFile()), JvmArtworkInspector, endpoints = endpoints, musicBrainzRequest = { it() })
            val result = finder.search(query)
            assertTrue(result.candidates.isEmpty())
            assertNull(result.recommended)
            assertTrue(result.outcomes.any { it.providerName == "iTunes / Apple Search" && it.status.name == "RATE_LIMITED" })
        } finally { server.shutdown() }
    }

    @Test fun cancellingSearchCancelsTheHttpCall() = runBlocking {
        val server = MockWebServer()
        server.start()
        server.enqueue(MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.NO_RESPONSE))
        try {
            val endpoints = AlbumArtworkFinder.Endpoints(musicBrainz = server.url("/").toString())
            val finder = AlbumArtworkFinder(OkHttpClient(), Json, AlbumArtworkCache(kotlin.io.path.createTempDirectory("cancel-art").toFile()), JvmArtworkInspector, endpoints = endpoints, musicBrainzRequest = { it() })
            val job = async { finder.search(query) }
            kotlinx.coroutines.delay(150)
            val start = System.nanoTime()
            job.cancel()
            job.join()
            assertTrue("Cancellation must not wait for the six-second timeout", (System.nanoTime() - start) / 1000000 < 1500)
        } finally { server.shutdown() }
    }
}
