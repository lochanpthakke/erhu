package com.erhu.erhu.core.network.provider

import com.erhu.erhu.core.common.logging.Logger
import com.erhu.erhu.core.network.api.ITunesApi
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** Real Retrofit serialization and request parameters, without credentials or external network calls. */
class ITunesProviderTest {
    private val server = MockWebServer()
    private lateinit var provider: ITunesProvider

    private object SilentLogger : Logger {
        override fun v(tag: String, message: String, throwable: Throwable?) = Unit
        override fun d(tag: String, message: String, throwable: Throwable?) = Unit
        override fun i(tag: String, message: String, throwable: Throwable?) = Unit
        override fun w(tag: String, message: String, throwable: Throwable?) = Unit
        override fun e(tag: String, message: String, throwable: Throwable?) = Unit
    }

    @Before fun setUp() {
        server.start()
        val api = Retrofit.Builder().baseUrl(server.url("/"))
            .addConverterFactory(Json { ignoreUnknownKeys = true }.asConverterFactory("application/json".toMediaType()))
            .build().create(ITunesApi::class.java)
        provider = ITunesProvider(api, SilentLogger)
    }

    @After fun tearDown() = server.shutdown()

    @Test fun searchRetainsOfficialGenreAndCleansBrokenArtistSeparators() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"resultCount":1,"results":[{"trackId":7,"trackName":"Track","artistName":"Artist","collectionName":"Album","primaryGenreName":" R&B "}]}"""))
        val result = provider.searchTrack("Track","Artist/ Guest",null).single()
        assertEquals("R&B",result.genre)
        assertEquals("Artist Track",server.takeRequest().requestUrl!!.queryParameter("term"))
    }

    @Test fun trackSearchReturnsUsableMetadataWithoutCredentials() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"resultCount":1,"results":[{"trackId":1,"trackName":"Get Lucky","artistName":"Daft Punk","collectionName":"Random Access Memories","trackTimeMillis":369000,"releaseDate":"2013-05-17T00:00:00Z","artworkUrl100":"https://example.test/100x100bb.jpg"}]}"""))
        val result = provider.searchTrack("Get Lucky", "Daft Punk", "Random Access Memories").single()
        assertEquals("Get Lucky", result.title)
        assertEquals("Random Access Memories", result.album)
        assertEquals(2013, result.year)
        assertEquals("https://example.test/1000x1000bb.jpg", result.coverArtUrl)
        val request = server.takeRequest()
        assertEquals("song", request.requestUrl!!.queryParameter("entity"))
        assertEquals(setOf("term", "entity", "limit"), request.requestUrl!!.queryParameterNames)
        assertNull(request.getHeader("Authorization"))
    }

    @Test fun albumArtworkSearchRemainsKeyless() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"resultCount":1,"results":[{"collectionId":2,"artworkUrl100":"https://example.test/100x100bb.jpg"}]}"""))
        assertEquals(listOf("https://example.test/1000x1000bb.jpg"), provider.fetchAlbumArtList("Random Access Memories", "Daft Punk"))
        val request = server.takeRequest()
        assertEquals("album", request.requestUrl!!.queryParameter("entity"))
        assertEquals(setOf("term", "entity", "limit"), request.requestUrl!!.queryParameterNames)
        assertNull(request.getHeader("Authorization"))
    }
}
