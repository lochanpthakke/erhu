package com.lochanpthakke.empee3.feature.transfer.server

import java.io.BufferedInputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.Collections
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * A small HTTP server for one transfer session, listening only on [bindAddress] (the phone's
 * Wi-Fi address), never on every interface. One request per connection; at most
 * [TransferLimits.maxConnections] connections at once (others get a quick 503); idle or stalled
 * connections time out. [stop] closes the listening socket and every open connection, which ends
 * any upload in progress (the router then deletes its partial file).
 */
class TransferServer(
    private val bindAddress: InetAddress,
    private val limits: TransferLimits,
    private val routerFor: (hostPort: String) -> TransferRouter,
    private val onError: (String, Throwable?) -> Unit = { _, _ -> },
) {
    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null
    private var workers: ExecutorService? = null
    private val openSockets: MutableSet<Socket> = Collections.synchronizedSet(HashSet())

    @Volatile
    private var running = false

    /** Starts listening on [preferredPort] (or a free port if it is taken). Returns the port in use. */
    @Throws(IOException::class)
    fun start(preferredPort: Int): Int {
        check(!running) { "Already running" }
        val socket = bindTo(preferredPort) ?: bindTo(0) ?: throw IOException("No free port on ${bindAddress.hostAddress}")
        val port = socket.localPort
        val router = routerFor(hostPort(bindAddress, port))
        val pool = Executors.newFixedThreadPool(limits.maxConnections) { r -> Thread(r, "wifi-transfer-worker").apply { isDaemon = true } }
        serverSocket = socket
        workers = pool
        running = true
        acceptThread =
            Thread({ acceptLoop(socket, pool, router) }, "wifi-transfer-accept").apply {
                isDaemon = true
                start()
            }
        return port
    }

    fun stop() {
        running = false
        runCatching { serverSocket?.close() }
        synchronized(openSockets) { openSockets.toList() }.forEach { runCatching { it.close() } }
        workers?.shutdownNow()
        runCatching { workers?.awaitTermination(STOP_WAIT_MS, TimeUnit.MILLISECONDS) }
        serverSocket = null
        workers = null
        acceptThread = null
    }

    val isRunning: Boolean get() = running

    @Suppress("SwallowedException") // a taken port just means: try another one
    private fun bindTo(port: Int): ServerSocket? =
        try {
            ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(bindAddress, port), BACKLOG)
            }
        } catch (e: IOException) {
            null
        }

    @Suppress("SwallowedException") // closing the socket in stop() is how this loop is told to end
    private fun acceptLoop(
        socket: ServerSocket,
        pool: ExecutorService,
        router: TransferRouter,
    ) {
        while (running) {
            val client =
                try {
                    socket.accept()
                } catch (e: SocketException) {
                    null
                } catch (e: IOException) {
                    onError("Accept failed", e)
                    null
                }
            when {
                client == null -> if (socket.isClosed) running = false
                openSockets.size >= limits.maxConnections -> refuse(client)
                else -> dispatch(client, pool, router)
            }
        }
    }

    @Suppress("SwallowedException") // the pool is shut down while stopping: refuse politely
    private fun dispatch(
        client: Socket,
        pool: ExecutorService,
        router: TransferRouter,
    ) {
        openSockets += client
        try {
            pool.execute { serve(client, router) }
        } catch (e: java.util.concurrent.RejectedExecutionException) {
            openSockets -= client
            refuse(client)
        }
    }

    @Suppress("SwallowedException") // the client went away: nothing to report
    private fun serve(
        client: Socket,
        router: TransferRouter,
    ) {
        try {
            client.soTimeout = limits.readTimeoutMs
            val input = BufferedInputStream(client.getInputStream())
            val output = client.getOutputStream()
            val response =
                try {
                    router.handle(HttpParser.readHead(input), input)
                } catch (e: HttpException) {
                    HttpResponse.error(e.status, e.message ?: "Bad request")
                }
            response?.writeTo(output)
        } catch (e: IOException) {
            // The client went away; the router has already cleaned up any partial file.
        } finally {
            openSockets -= client
            runCatching { client.close() }
        }
    }

    private fun refuse(client: Socket) {
        runCatching {
            client.soTimeout = REFUSE_TIMEOUT_MS
            HttpResponse.error(HttpStatus.SERVICE_UNAVAILABLE, "Too many connections, try again").writeTo(client.getOutputStream())
        }
        runCatching { client.close() }
    }

    companion object {
        const val DEFAULT_PORT = 8770
        private const val BACKLOG = 16
        private const val STOP_WAIT_MS = 2_000L
        private const val REFUSE_TIMEOUT_MS = 2_000

        fun hostPort(
            address: InetAddress,
            port: Int,
        ): String = "${address.hostAddress}:$port"
    }
}
