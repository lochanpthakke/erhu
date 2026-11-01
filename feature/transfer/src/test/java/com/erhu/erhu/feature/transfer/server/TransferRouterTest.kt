package com.erhu.erhu.feature.transfer.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.URLEncoder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class TransferRouterTest {
    private val host = "192.168.1.20:8770"
    private val session = TransferSession.create(nowMs = 0)
    private val sink = FakeSink()
    private val registry = TransferRegistry()
    private var saved = 0
    private val events =
        object : TransferEvents {
            override fun onFileSaved() {
                saved++
            }
        }
    private val router = TransferRouter(session, host, sink, registry, TransferLimits(maxFileBytes = 1_000_000), events) { 1L }

    private fun request(
        method: String,
        path: String,
        headers: Map<String, String> = emptyMap(),
        body: ByteArray = ByteArray(0),
    ): HttpRequest {
        val all = linkedMapOf("Host" to host) + headers
        val head = "$method $path HTTP/1.1\r\n" + all.entries.joinToString("") { "${it.key}: ${it.value}\r\n" } + "\r\n"
        return HttpParser.readHead(ByteArrayInputStream(head.toByteArray() + body))
    }

    private fun api(
        token: String? = session.token,
        extra: Map<String, String> = emptyMap(),
    ) = buildMap {
        put(TransferRouter.CLIENT_HEADER, TransferRouter.CLIENT_VALUE)
        if (token != null) put(TransferRouter.TOKEN_HEADER, token)
        putAll(extra)
    }

    private fun upload(
        name: String,
        body: ByteArray,
        token: String? = session.token,
        stream: InputStream = ByteArrayInputStream(body),
        length: Long = body.size.toLong(),
    ): HttpResponse? {
        val headers = api(token, mapOf(TransferRouter.NAME_HEADER to URLEncoder.encode(name, "UTF-8"), "Content-Length" to "$length"))
        return router.handle(request("POST", "/api/upload", headers), stream)
    }

    private fun HttpResponse.text() = String(body, Charsets.UTF_8)

    @Test
    fun `the page is served without credentials but holds none`() {
        val page = router.handle(request("GET", "/"), ByteArrayInputStream(ByteArray(0)))!!
        assertEquals(200, page.status)
        assertTrue(!page.text().contains(session.token) && !page.text().contains(session.pairingCode))
        assertEquals(404, router.handle(request("GET", "/../../etc/passwd"), ByteArrayInputStream(ByteArray(0)))!!.status)
        assertEquals(404, router.handle(request("GET", "/api/files"), ByteArrayInputStream(ByteArray(0)))!!.status)
        assertEquals(405, router.handle(request("DELETE", "/"), ByteArrayInputStream(ByteArray(0)))!!.status)
        assertEquals(405, router.handle(request("GET", "/api/upload", api()), ByteArrayInputStream(ByteArray(0)))!!.status)
    }

    @Test
    fun `uploads need the session token, the page header, the right host and origin`() {
        val body = mp3Bytes(1000)
        assertEquals(401, upload("a.mp3", body, token = null)!!.status)
        assertEquals(401, upload("a.mp3", body, token = "forged")!!.status)

        val noClientHeader = mapOf(TransferRouter.TOKEN_HEADER to session.token, TransferRouter.NAME_HEADER to "a.mp3", "Content-Length" to "1000")
        assertEquals(403, router.handle(request("POST", "/api/upload", noClientHeader), ByteArrayInputStream(body))!!.status)

        val evilOrigin = api(extra = mapOf("Origin" to "http://evil.example", TransferRouter.NAME_HEADER to "a.mp3", "Content-Length" to "1000"))
        assertEquals(403, router.handle(request("POST", "/api/upload", evilOrigin), ByteArrayInputStream(body))!!.status)

        val rebound = HttpParser.readHead(ByteArrayInputStream("POST /api/check HTTP/1.1\r\nHost: evil.example:8770\r\n\r\n".toByteArray()))
        assertEquals(421, router.handle(rebound, ByteArrayInputStream(ByteArray(0)))!!.status)

        session.close()
        assertEquals(401, upload("a.mp3", body)!!.status)
        assertTrue(sink.committed.isEmpty() && registry.items.value.isEmpty())
    }

    @Test
    fun `a valid upload is saved once and reported`() {
        val body = mp3Bytes(200_000)
        val response = upload("../../Music/My Song.mp3", body)!!
        assertEquals(200, response.status)
        assertTrue(response.text().contains("\"saved\""))
        assertTrue(sink.committed.getValue("My Song.mp3").contentEquals(body))
        assertEquals(1, sink.pending.single().commits)
        assertEquals(0, sink.pending.single().aborts)
        assertEquals(1, saved)
        val item = registry.items.value.single()
        assertEquals(TransferStatus.COMPLETED, item.status)
        assertEquals(200_000L, item.receivedBytes)
    }

    @Test
    fun `duplicates are skipped and name clashes get a numbered name`() {
        val body = mp3Bytes(5_000)
        assertEquals(200, upload("Song.mp3", body)!!.status)
        // Same name and size: a duplicate, never overwritten.
        val dup = upload("Song.mp3", body)!!
        assertTrue(dup.text().contains("\"duplicate\""))
        assertEquals(TransferStatus.DUPLICATE, registry.items.value.last().status)
        // Same name, different content: kept side by side.
        assertEquals(200, upload("Song.mp3", mp3Bytes(6_000))!!.status)
        assertEquals(setOf("Song.mp3", "Song (2).mp3"), sink.committed.keys)
        assertEquals(1, sink.pending.count { it.savedName == "Song.mp3" })
    }

    @Test
    fun `the check endpoint answers before any byte is sent`() {
        sink.existing["Known.flac"] = 10
        fun check(
            name: String,
            size: Long,
        ) = router.handle(
            request("POST", "/api/check", api(extra = mapOf(TransferRouter.NAME_HEADER to name, TransferRouter.SIZE_HEADER to "$size"))),
            ByteArrayInputStream(ByteArray(0)),
        )!!
        assertTrue(check("Known.flac", 10).text().contains("duplicate"))
        assertTrue(check("Known.flac", 11).text().contains("Known (2).flac"))
        assertEquals(415, check("setup.exe", 10).status)
        assertEquals(413, check("big.mp3", 2_000_000).status)
        sink.freeBytes = 5
        assertEquals(507, check("new.mp3", 10).status)
    }

    @Test
    fun `invalid requests are refused before anything is written`() {
        assertEquals(413, upload("big.mp3", ByteArray(0), length = 2_000_000)!!.status)
        assertEquals(400, upload("empty.mp3", ByteArray(0))!!.status)
        assertEquals(415, upload("notes.txt", mp3Bytes(100))!!.status)
        assertEquals(415, upload("..", mp3Bytes(100))!!.status)
        val noLength = api(extra = mapOf(TransferRouter.NAME_HEADER to "a.mp3"))
        assertEquals(411, router.handle(request("POST", "/api/upload", noLength), ByteArrayInputStream(ByteArray(0)))!!.status)
        // A renamed non-audio file is caught by its content.
        val fake = upload("fake.mp3", "MZ this is not audio".toByteArray())!!
        assertEquals(415, fake.status)
        assertEquals(1, sink.pending.single().aborts)
        assertEquals(TransferStatus.FAILED, registry.items.value.single().status)
        assertTrue(sink.committed.isEmpty())
    }

    @Test
    fun `a cancelled or broken upload leaves nothing behind`() {
        val body = mp3Bytes(100_000)
        // The browser stops after half the bytes (the stream ends early).
        assertNull(upload("half.mp3", body, stream = ByteArrayInputStream(body.copyOf(50_000)), length = 100_000))
        // The connection drops mid-way.
        val broken =
            object : InputStream() {
                var left = 70_000

                override fun read(): Int = throw UnsupportedOperationException()

                override fun read(
                    b: ByteArray,
                    off: Int,
                    len: Int,
                ): Int {
                    if (left <= 0) throw IOException("Connection reset")
                    val n = minOf(len, left)
                    "ID3".toByteArray().copyInto(b, off, 0, minOf(3, n))
                    left -= n
                    return n
                }
            }
        assertNull(upload("reset.mp3", body, stream = broken, length = 100_000))
        assertTrue(sink.committed.isEmpty())
        assertTrue(sink.pending.all { it.aborts == 1 && it.commits == 0 })
        assertTrue(registry.items.value.all { it.status == TransferStatus.CANCELLED })
        assertEquals(0, saved)
    }

    @Test
    fun `a full disk fails that file only`() {
        assertEquals(200, upload("first.mp3", mp3Bytes(1_000))!!.status)
        sink.failWriteAfter = 10_000
        assertEquals(507, upload("second.mp3", mp3Bytes(50_000))!!.status)
        assertEquals(setOf("first.mp3"), sink.committed.keys)
        assertEquals(listOf(TransferStatus.COMPLETED, TransferStatus.FAILED), registry.items.value.map { it.status })
        assertTrue(registry.items.value.last().message!!.contains("storage"))
    }

    @Test
    fun `more parallel uploads than allowed are asked to retry`() {
        val gate = CountDownLatch(1)
        val started = CountDownLatch(2)
        val body = mp3Bytes(1_000)
        fun slowStream() =
            object : InputStream() {
                private val inner = ByteArrayInputStream(body)
                private var first = true

                override fun read(): Int = inner.read()

                override fun read(
                    b: ByteArray,
                    off: Int,
                    len: Int,
                ): Int {
                    if (first) {
                        first = false
                        started.countDown()
                        gate.await(5, TimeUnit.SECONDS)
                    }
                    return inner.read(b, off, len)
                }
            }
        val results = mutableListOf<Int?>()
        val workers = List(2) { i -> thread {
                val status = upload("slow$i.mp3", body, stream = slowStream())?.status
                synchronized(results) { results += status }
            } }
        assertTrue(started.await(5, TimeUnit.SECONDS))
        assertEquals(429, upload("third.mp3", body)!!.status)
        gate.countDown()
        workers.forEach { it.join(5_000) }
        assertEquals(listOf(200, 200), results)
        assertEquals(2, sink.committed.size)
    }

    @Test
    fun `pairing by code returns the token and locks after wrong codes`() {
        fun pair(code: String) =
            router.handle(
                request("POST", "/api/pair", api(token = null, extra = mapOf(TransferRouter.CODE_HEADER to code))),
                ByteArrayInputStream(ByteArray(0)),
            )!!
        val wrong = if (session.pairingCode == "123456") "654321" else "123456"
        assertEquals(403, pair(wrong).status)
        val ok = pair(session.pairingCode)
        assertEquals(200, ok.status)
        assertTrue(ok.text().contains(session.token))
        repeat(TransferSession.DEFAULT_MAX_PAIR_ATTEMPTS) { pair(wrong) }
        assertEquals(429, pair(session.pairingCode).status)
    }

    @Test
    fun `malformed requests are rejected by the parser`() {
        fun parse(raw: String) =
            runCatching { HttpParser.readHead(ByteArrayInputStream(raw.toByteArray())) }.exceptionOrNull() as? HttpException
        assertEquals(400, parse("GARBAGE\r\n\r\n")?.status)
        assertEquals(400, parse("GET http://evil/ HTTP/1.1\r\n\r\n")?.status)
        assertEquals(400, parse("POST / HTTP/1.1\r\nContent-Length: -5\r\n\r\n")?.status)
        assertEquals(400, parse("POST / HTTP/1.1\r\nContent-Length: 5\r\nContent-Length: 6\r\n\r\n")?.status)
        assertEquals(411, parse("POST / HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n")?.status)
        assertEquals(431, parse("GET / HTTP/1.1\r\nX: " + "a".repeat(20_000) + "\r\n\r\n")?.status)
        assertEquals(400, parse("GET / HTTP/1.1\r\n")?.status) // never finished
    }
}
