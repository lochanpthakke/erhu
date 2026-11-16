package com.erhu.erhu.core.network.remote.drive

import com.erhu.erhu.core.model.RemoteAccessException
import com.erhu.erhu.core.model.RemoteProvider
import com.erhu.erhu.core.model.RemoteRef
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class DriveClientTest {
    private val server = MockWebServer()
    private lateinit var client: DriveClient

    @Before
    fun setUp() {
        server.start()
        client = DriveClient(OkHttpClient(), server.url("/"))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `a folder listing keeps folders and playable audio, resolves shortcuts and passes the page token on`() =
        runBlocking {
            server.enqueue(
                MockResponse().setBody(
                    """{"nextPageToken":"p2","files":[
                    {"id":"f1","name":"Albums","mimeType":"application/vnd.google-apps.folder"},
                    {"id":"a1","name":"01 Intro.mp3","mimeType":"audio/mpeg","size":"4096"},
                    {"id":"a2","name":"Live.flac","mimeType":"application/octet-stream"},
                    {"id":"d1","name":"Notes.pdf","mimeType":"application/pdf"},
                    {"id":"v1","name":"Clip.mp4","mimeType":"video/mp4"},
                    {"id":"w1","name":"Old.wma","mimeType":"audio/x-ms-wma"},
                    {"id":"s1","name":"Link","mimeType":"application/vnd.google-apps.shortcut",
                     "shortcutDetails":{"targetId":"real","targetMimeType":"application/vnd.google-apps.folder"}}]}""",
                ),
            )
            val page = client.children("tok", "it's", null, DriveOrder.NEWEST, "p1")

            assertEquals(listOf("f1", "a1", "a2", "real"), page.items.map { it.id })
            assertEquals(listOf(true, false, false, true), page.items.map { it.isFolder })
            assertEquals(4096L, page.items[1].sizeBytes)
            assertEquals("p2", page.nextPageToken)

            val request = server.takeRequest()
            val url = request.requestUrl!!
            assertEquals("GET", request.method)
            assertEquals("Bearer tok", request.getHeader("Authorization"))
            // The token is in the header only, and the folder id is escaped inside the query.
            assertFalse(url.toString().contains("tok"))
            assertEquals("'it\\'s' in parents and trashed = false", url.queryParameter("q"))
            assertEquals("p1", url.queryParameter("pageToken"))
            assertEquals("folder,modifiedTime desc", url.queryParameter("orderBy"))
            assertEquals("true", url.queryParameter("includeItemsFromAllDrives"))
            assertNull(url.queryParameter("corpora"))
        }

    @Test
    fun `a shared drive listing names the drive and search covers all drives`() =
        runBlocking {
            server.enqueue(MockResponse().setBody("""{"files":[]}"""))
            server.enqueue(MockResponse().setBody("""{"files":[]}"""))
            server.enqueue(MockResponse().setBody("""{"drives":[{"id":"D1","name":"Team"}]}"""))
            assertNull(client.children("t", "folder", "D1", DriveOrder.NAME, null).nextPageToken)
            client.search("t", "night", null)
            val drives = client.sharedDrives("t", null)

            val inDrive = server.takeRequest().requestUrl!!
            assertEquals("drive", inDrive.queryParameter("corpora"))
            assertEquals("D1", inDrive.queryParameter("driveId"))
            val search = server.takeRequest().requestUrl!!
            assertEquals("allDrives", search.queryParameter("corpora"))
            assertEquals("name contains 'night' and trashed = false", search.queryParameter("q"))
            assertEquals("D1", drives.items.single().driveId)
            assertTrue(drives.items.single().isFolder)
        }

    @Test
    fun `the account is read from about`() =
        runBlocking {
            server.enqueue(MockResponse().setBody("""{"user":{"displayName":"Ada","emailAddress":"ada@example.com"}}"""))
            assertEquals(DriveAccount("ada@example.com", "Ada"), client.about("t"))
            assertEquals("/drive/v3/about", server.takeRequest().requestUrl!!.encodedPath)
        }

    @Test
    fun `an expired token, a lost permission, a missing folder and a rate limit are told apart`() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(401))
            server.enqueue(MockResponse().setResponseCode(403).setBody("""{"error":{"errors":[{"reason":"insufficientPermissions"}]}}"""))
            server.enqueue(MockResponse().setResponseCode(404))
            server.enqueue(MockResponse().setResponseCode(403).setBody("""{"error":{"errors":[{"reason":"userRateLimitExceeded"}]}}"""))

            assertEquals(RemoteAccessException.Reason.AUTH_REQUIRED, reasonOf { client.about("t") })
            assertEquals(RemoteAccessException.Reason.AUTH_REQUIRED, reasonOf { client.about("t") })
            assertEquals(RemoteAccessException.Reason.NOT_FOUND, reasonOf { client.children("t", "gone", null, DriveOrder.NAME, null) })
            // A rate limit is an ordinary failure: it must not send the user back to sign-in.
            assertNull(reasonOf { client.about("t") })
        }

    private suspend fun reasonOf(block: suspend () -> Unit): RemoteAccessException.Reason? =
        try {
            block()
            null
        } catch (e: RemoteAccessException) {
            e.reason
        } catch (e: IOException) {
            null
        }

    @Test
    fun `playable audio is decided by extension first, then by media type`() {
        assertTrue(isPlayableAudio("a.mp3", "audio/mpeg"))
        assertTrue(isPlayableAudio("a.FLAC", "application/octet-stream"))
        assertTrue(isPlayableAudio("a.m4a", "video/mp4"))
        assertTrue(isPlayableAudio("a.opus", "audio/ogg"))
        assertTrue(isPlayableAudio("voice memo", "audio/mp4"))
        assertTrue(isPlayableAudio("a.ogg", "application/ogg"))
        assertFalse(isPlayableAudio("a.wma", "audio/x-ms-wma"))
        assertFalse(isPlayableAudio("a.mid", "audio/midi"))
        assertFalse(isPlayableAudio("a.mp4", "video/mp4"))
        assertFalse(isPlayableAudio("a.pdf", "application/pdf"))
        assertFalse(isPlayableAudio("a.mp3", "video/mpeg"))
    }

    @Test
    fun `a file becomes a track titled by its name and streams with a fresh token in the header`() {
        val track = DriveItem("id 1", "My.Song.flac", isFolder = false, mimeType = "audio/flac").toTrack()
        assertEquals("My.Song", track.title)
        assertEquals("id 1", track.remoteId)

        val issued = mutableListOf<Long>()
        val tokens =
            object : DriveTokenProvider {
                override fun token(sourceId: Long): String = "token-${issued.size}".also { issued += sourceId }

                override fun invalidate(sourceId: Long) = Unit
            }
        val resolver = DriveStreamResolver(tokens, client)
        val ref = RemoteRef(RemoteProvider.GOOGLE_DRIVE, 7, "id 1")
        val first = resolver.resolve(ref)
        val second = resolver.resolve(ref)

        val url = first.url.toHttpUrl()
        assertEquals("/drive/v3/files/id%201", url.encodedPath)
        assertEquals("media", url.queryParameter("alt"))
        assertFalse(first.url.contains("token"))
        // Asked again on every open, so a renewed token is picked up after a seek or retry.
        assertEquals("Bearer token-0", first.headers["Authorization"])
        assertEquals("Bearer token-1", second.headers["Authorization"])
        assertEquals(listOf(7L, 7L), issued)
        assertFalse(first.toString().contains("token"))
    }
}
