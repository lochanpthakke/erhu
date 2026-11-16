package com.erhu.erhu.core.network.remote.subsonic

import com.erhu.erhu.core.model.RemoteAccessException
import com.erhu.erhu.core.model.RemoteProvider
import com.erhu.erhu.core.model.RemoteSource
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.IOException

class SubsonicClientTest {
    private val server = MockWebServer()
    private val client = SubsonicClient(OkHttpClient())
    private lateinit var connection: SubsonicConnection

    @Before
    fun setUp() {
        server.start()
        connection = SubsonicConnection(server.url("/music/"), "ada", "sesame")
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun ok(body: String) = MockResponse().setBody("""{"subsonic-response":{"status":"ok","version":"1.16.1",$body}}""")

    @Test
    fun `requests carry a salted token and never the password`() =
        runBlocking {
            server.enqueue(MockResponse().setBody("""{"subsonic-response":{"status":"ok"}}"""))
            server.enqueue(MockResponse().setBody("""{"subsonic-response":{"status":"ok"}}"""))
            client.ping(connection)
            client.ping(connection)

            val first = server.takeRequest().requestUrl!!
            val second = server.takeRequest().requestUrl!!
            assertEquals("/music/rest/ping.view", first.encodedPath)
            assertEquals("ada", first.queryParameter("u"))
            assertEquals("json", first.queryParameter("f"))
            assertNull(first.queryParameter("p"))
            assertFalse(first.toString().contains("sesame"))
            val salt = first.queryParameter("s")!!
            assertEquals(md5Hex("sesame$salt"), first.queryParameter("t"))
            // A fresh salt per request: a captured token cannot be matched to the next one.
            assertNotEquals(salt, second.queryParameter("s"))
        }

    @Test
    fun `md5 matches the published Subsonic example`() {
        assertEquals("26719a1196d2a940705a59634eb18eab", md5Hex("sesame" + "c19b2d"))
    }

    @Test
    fun `album tracks are mapped and folders or id-less entries are dropped`() =
        runBlocking {
            server.enqueue(
                ok(
                    """"album":{"id":"al-1","name":"Blue","song":[
                    {"id":"s1","title":"One","artist":"A","album":"Blue","duration":201,"track":1,"discNumber":2,
                     "contentType":"audio/flac","coverArt":"al-1","starred":"2024-01-01T00:00:00Z"},
                    {"id":7,"title":"Numeric id","duration":"60"},
                    {"id":"d1","title":"Folder","isDir":true},
                    {"title":"No id"}]}""",
                ),
            )
            val tracks = client.albumTracks(connection, "al-1")

            assertEquals(listOf("s1", "7"), tracks.map { it.remoteId })
            val one = tracks.first()
            assertEquals(201_000L, one.durationMs)
            assertEquals(2, one.discNumber)
            assertEquals("audio/flac", one.mimeType)
            assertEquals("al-1", one.artworkKey)
            assertTrue(one.isFavorite)
            assertFalse(tracks[1].isFavorite)
            assertEquals(60_000L, tracks[1].durationMs)
            assertEquals("al-1", server.takeRequest().requestUrl!!.queryParameter("id"))
        }

    @Test
    fun `artists are flattened across the index and search returns all three kinds`() =
        runBlocking {
            server.enqueue(ok(""""artists":{"index":[{"name":"A","artist":[{"id":"a1","name":"Abba","albumCount":3}]},{"name":"B","artist":[{"id":"b1","name":"Blur"}]}]}"""))
            server.enqueue(
                ok(""""searchResult3":{"artist":[{"id":"a1","name":"Abba"}],"album":[{"id":"al","name":"Gold","artist":"Abba","year":1992}],"song":[{"id":"s","title":"SOS"}]}"""),
            )
            assertEquals(listOf("Abba", "Blur"), client.artists(connection).map { it.name })
            val found = client.search(connection, "ab ba")
            assertEquals(1, found.artists.size)
            assertEquals(1992, found.albums.single().year)
            assertEquals("SOS", found.tracks.single().title)
            server.takeRequest()
            assertEquals("ab ba", server.takeRequest().requestUrl!!.queryParameter("query"))
        }

    @Test
    fun `starring calls star or unstar with the song id`() =
        runBlocking {
            server.enqueue(MockResponse().setBody("""{"subsonic-response":{"status":"ok"}}"""))
            server.enqueue(MockResponse().setBody("""{"subsonic-response":{"status":"ok"}}"""))
            client.setStarred(connection, "s1", true)
            client.setStarred(connection, "s1", false)
            assertEquals("/music/rest/star.view", server.takeRequest().requestUrl!!.encodedPath)
            assertEquals("/music/rest/unstar.view", server.takeRequest().requestUrl!!.encodedPath)
        }

    @Test
    fun `wrong credentials, a missing item and a non-Subsonic server are told apart`() =
        runBlocking {
            server.enqueue(MockResponse().setBody("""{"subsonic-response":{"status":"failed","error":{"code":40,"message":"Wrong username or password"}}}"""))
            server.enqueue(MockResponse().setBody("""{"subsonic-response":{"status":"failed","error":{"code":70,"message":"not found"}}}"""))
            server.enqueue(MockResponse().setBody("<html>router login</html>"))
            server.enqueue(MockResponse().setResponseCode(401))

            assertEquals(RemoteAccessException.Reason.AUTH_REQUIRED, reasonOf { client.ping(connection) })
            assertEquals(RemoteAccessException.Reason.NOT_FOUND, reasonOf { client.albumTracks(connection, "x") })
            try {
                client.ping(connection)
                fail("expected a failure")
            } catch (e: IOException) {
                assertFalse(e is RemoteAccessException)
            }
            assertEquals(RemoteAccessException.Reason.AUTH_REQUIRED, reasonOf { client.ping(connection) })
        }

    private suspend fun reasonOf(block: suspend () -> Unit): RemoteAccessException.Reason? =
        try {
            block()
            null
        } catch (e: RemoteAccessException) {
            e.reason
        }

    @Test
    fun `the stream url asks for the original file and cover art is null unless an image comes back`() {
        val stream = client.streamUrl(connection, "s 1").toHttpUrl()
        assertEquals("/music/rest/stream.view", stream.encodedPath)
        assertEquals("s 1", stream.queryParameter("id"))
        assertEquals("raw", stream.queryParameter("format"))

        server.enqueue(MockResponse().setHeader("Content-Type", "image/jpeg").setBody("JPEGBYTES"))
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"subsonic-response":{"status":"failed"}}"""))
        assertEquals("JPEGBYTES", client.coverArt(connection, "al-1", 300)?.toString(Charsets.UTF_8))
        assertNull(client.coverArt(connection, "none", 300))
        assertEquals("300", server.takeRequest().requestUrl!!.queryParameter("size"))
    }

    @Test
    fun `typed addresses are normalised and unusable ones rejected`() {
        assertEquals("https://music.example.com/", parseServerUrl(" music.example.com ").toString())
        assertEquals("https://music.example.com/navidrome", parseServerUrl("https://music.example.com/navidrome/rest/").toString())
        assertEquals("http://192.168.1.5:4533/", parseServerUrl("http://192.168.1.5:4533").toString())
        assertNull(parseServerUrl(""))
        assertNull(parseServerUrl("ftp://music.example.com"))
        assertNull(parseServerUrl("my server"))
        // Credentials belong in their own fields, never in a stored address.
        assertNull(parseServerUrl("https://ada:sesame@music.example.com"))
    }

    @Test
    fun `plain http is refused unless the user allowed it for that server`() {
        val http = RemoteSource(1, RemoteProvider.SUBSONIC, "home", "http://192.168.1.5:4533/", "ada")
        try {
            connectionFor(http, "sesame")
            fail("expected a refusal")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("Unencrypted"))
        }
        assertTrue(connectionFor(http.copy(allowInsecure = true), "sesame").isInsecure)
        assertFalse(connectionFor(http.copy(baseUrl = "https://music.example.com/"), "sesame").isInsecure)
    }
}
