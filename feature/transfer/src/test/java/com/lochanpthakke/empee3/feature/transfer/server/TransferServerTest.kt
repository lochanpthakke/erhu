package com.lochanpthakke.empee3.feature.transfer.server

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.Socket
import java.net.URL

/** The real server over loopback: binding, request handling, and stop cutting everything off. */
class TransferServerTest {
    private val address = InetAddress.getLoopbackAddress()
    private val session = TransferSession.create(nowMs = System.currentTimeMillis())
    private val sink = FakeSink()
    private val registry = TransferRegistry()
    private val limits = TransferLimits(maxFileBytes = 10_000_000, readTimeoutMs = 2_000)
    private val server = TransferServer(address, limits, { hostPort -> TransferRouter(session, hostPort, sink, registry, limits) })
    private var port = 0

    @After
    fun tearDown() = server.stop()

    private fun post(
        path: String,
        body: ByteArray,
        token: String? = session.token,
    ): HttpURLConnection =
        (URL("http://${address.hostAddress}:$port$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setFixedLengthStreamingMode(body.size)
            setRequestProperty(TransferRouter.CLIENT_HEADER, TransferRouter.CLIENT_VALUE)
            setRequestProperty(TransferRouter.NAME_HEADER, "Live%20Track.mp3")
            token?.let { setRequestProperty(TransferRouter.TOKEN_HEADER, it) }
            outputStream.use { it.write(body) }
        }

    @Test
    fun `serves the page, accepts an authorized upload and refuses others`() {
        port = server.start(0)
        val page = URL("http://${address.hostAddress}:$port/").openConnection() as HttpURLConnection
        assertEquals(200, page.responseCode)
        assertTrue(page.getHeaderField("Content-Security-Policy").contains("default-src 'none'"))

        val body = mp3Bytes(300_000)
        assertEquals(200, post("/api/upload", body).responseCode)
        assertTrue(sink.committed.getValue("Live Track.mp3").contentEquals(body))

        assertEquals(401, post("/api/upload", body, token = null).responseCode)
        assertEquals(1, sink.committed.size)
    }

    @Test
    fun `stop closes the listening socket and cuts off an upload in progress`() {
        port = server.start(0)
        // Start an upload that sends its headers and only part of the body, then stop the server.
        val socket = Socket(address, port)
        val head =
            "POST /api/upload HTTP/1.1\r\nHost: ${address.hostAddress}:$port\r\n${TransferRouter.CLIENT_HEADER}: 1\r\n" +
                "${TransferRouter.TOKEN_HEADER}: ${session.token}\r\n${TransferRouter.NAME_HEADER}: partial.mp3\r\nContent-Length: 1000000\r\n\r\n"
        socket.getOutputStream().apply {
            write(head.toByteArray())
            write(mp3Bytes(100_000))
            flush()
        }
        val deadline = System.currentTimeMillis() + 5_000
        while (registry.items.value.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(20)
        server.stop()
        while (registry.items.value.any { !it.status.isTerminal } && System.currentTimeMillis() < deadline) Thread.sleep(20)

        assertEquals(TransferStatus.CANCELLED, registry.items.value.single().status)
        assertTrue(sink.committed.isEmpty())
        assertEquals(1, sink.pending.single().aborts)
        assertFalse(server.isRunning)
        assertTrue(runCatching { Socket(address, port).close() }.exceptionOrNull() is IOException)
        socket.close()
    }

    @Test
    fun `a stalled connection times out and its partial file is removed`() {
        port = server.start(0)
        val socket = Socket(address, port)
        val head =
            "POST /api/upload HTTP/1.1\r\nHost: ${address.hostAddress}:$port\r\n${TransferRouter.CLIENT_HEADER}: 1\r\n" +
                "${TransferRouter.TOKEN_HEADER}: ${session.token}\r\n${TransferRouter.NAME_HEADER}: stall.mp3\r\nContent-Length: 50000\r\n\r\n"
        socket.getOutputStream().apply {
            write(head.toByteArray())
            write(mp3Bytes(1_000))
            flush()
        }
        val deadline = System.currentTimeMillis() + 8_000
        while (registry.items.value.none { it.status.isTerminal } && System.currentTimeMillis() < deadline) Thread.sleep(50)
        assertEquals(TransferStatus.CANCELLED, registry.items.value.single().status)
        assertEquals(1, sink.pending.single().aborts)
        socket.close()
    }
}
