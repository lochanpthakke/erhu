package com.erhu.erhu.feature.transfer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import androidx.core.content.ContextCompat
import com.erhu.erhu.core.common.dispatcher.DispatcherProvider
import com.erhu.erhu.core.common.logging.Logger
import com.erhu.erhu.core.database.repository.LibraryRepository
import com.erhu.erhu.feature.transfer.server.TransferEvents
import com.erhu.erhu.feature.transfer.server.TransferItem
import com.erhu.erhu.feature.transfer.server.TransferLimits
import com.erhu.erhu.feature.transfer.server.TransferRegistry
import com.erhu.erhu.feature.transfer.server.TransferRouter
import com.erhu.erhu.feature.transfer.server.TransferServer
import com.erhu.erhu.feature.transfer.server.TransferSession
import com.lochanpthakke.empee3.feature.transfer.WifiTransferService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.Inet4Address
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/** Why a session is not running (null: never started). */
enum class StopReason {
    USER,
    NO_WIFI,
    WIFI_LOST,
    PERMISSION_DENIED,
    STORAGE_PERMISSION_DENIED,
    IDLE_TIMEOUT,
    SESSION_EXPIRED,
    SYSTEM_TIME_LIMIT,
    FAILED_TO_START,
}

sealed interface TransferServerState {
    data class Stopped(
        val reason: StopReason? = null,
    ) : TransferServerState

    data object Starting : TransferServerState

    /** Listening: only reported once the socket is really bound. */
    data class Running(
        val address: String,
        /** What the QR code holds: the address plus the session token in the #fragment. */
        val pairingUrl: String,
        val pairingCode: String,
    ) : TransferServerState
}

/**
 * One Wi-Fi transfer session at a time, from the user's Start to Stop. The server listens only on
 * the Wi-Fi interface's address and lives inside [WifiTransferService] (a foreground service), so
 * it is never a hidden background listener: it stops when the user stops it, when Wi-Fi goes away
 * or changes address, after [IDLE_TIMEOUT_MS] without any request, when the session's lifetime
 * ends, or when Android ends the foreground service. Every stop closes the session (its
 * credentials stop working), every socket, and deletes partial files.
 */
@Singleton
class WifiTransferCoordinator
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val libraryRepository: LibraryRepository,
        private val logger: Logger,
        dispatcherProvider: DispatcherProvider,
    ) {
        private val io = dispatcherProvider.io
        private val scope = CoroutineScope(SupervisorJob() + dispatcherProvider.main)
        private val connectivity = context.getSystemService(ConnectivityManager::class.java)
        private val registry = TransferRegistry()
        private val limits = TransferLimits()

        private val _state = MutableStateFlow<TransferServerState>(TransferServerState.Stopped())
        val state: StateFlow<TransferServerState> = _state.asStateFlow()
        val items: StateFlow<List<TransferItem>> = registry.items

        private var server: TransferServer? = null
        private var session: TransferSession? = null
        private var sink: MediaStoreUploadSink? = null
        private var networkCallback: ConnectivityManager.NetworkCallback? = null
        private var watchdog: Job? = null
        private val activeUploads = AtomicInteger()
        private val lastActivity = AtomicLong()

        private val refresher =
            LibraryRefreshScheduler(scope, isBusy = { activeUploads.get() > 0 }) {
                libraryRepository.requestRescanAfterChange()
            }

        private val events =
            object : TransferEvents {
                override fun onRequest() = lastActivity.set(System.currentTimeMillis())

                override fun onUploadStarted() {
                    activeUploads.incrementAndGet()
                }

                override fun onUploadFinished() {
                    activeUploads.decrementAndGet()
                }

                override fun onFileSaved() = refresher.onFileSaved()
            }

        /** What is missing before a session can start, checked again by the service itself. */
        fun missingPermissions(): List<String> = requiredPermissions().filter { !isGranted(it) }

        fun requiredPermissions(): List<String> =
            buildList {
                if (Build.VERSION.SDK_INT >= ANDROID_17) add(ACCESS_LOCAL_NETWORK)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }

        fun hasWifi(): Boolean = currentWifi() != null

        /** Called by [WifiTransferService] once it is in the foreground. Each check that fails ends the attempt with its reason. */
        @Suppress("ReturnCount")
        suspend fun startServer() {
            if (_state.value is TransferServerState.Running || _state.value == TransferServerState.Starting) return
            _state.value = TransferServerState.Starting
            val missing = missingPermissions()
            if (missing.isNotEmpty()) {
                finish(if (ACCESS_LOCAL_NETWORK in missing) StopReason.PERMISSION_DENIED else StopReason.STORAGE_PERMISSION_DENIED)
                return
            }
            val wifi = currentWifi() ?: return finish(StopReason.NO_WIFI)
            val newSession = TransferSession.create(System.currentTimeMillis())
            val newSink = MediaStoreUploadSink(context)
            registry.clear()
            val newServer =
                TransferServer(wifi.address, limits, { hostPort ->
                    TransferRouter(newSession, hostPort, newSink, registry, limits, events)
                }) { message, error -> logger.w(TAG, message, error) }
            val port =
                try {
                    withContext(io) {
                        newSink.cleanUpLeftovers()
                        newServer.start(TransferServer.DEFAULT_PORT)
                    }
                } catch (e: IOException) {
                    logger.w(TAG, "Could not start the transfer server: ${e.message}")
                    return finish(StopReason.FAILED_TO_START)
                }
            session = newSession
            server = newServer
            sink = newSink
            lastActivity.set(System.currentTimeMillis())
            watchNetwork(wifi.network, wifi.address)
            startWatchdog(newSession)
            val address = "http://${TransferServer.hostPort(wifi.address, port)}"
            _state.value = TransferServerState.Running(address, "$address/#t=${newSession.token}", newSession.pairingCode)
            // Never log the token or the pairing URL.
            logger.d(TAG, "Wi-Fi transfer listening on port $port")
        }

        /** A start the screen could not even attempt (no Wi-Fi, permission refused): shown as the stopped reason. */
        fun reportNotStarted(reason: StopReason) {
            if (_state.value is TransferServerState.Stopped) _state.value = TransferServerState.Stopped(reason)
        }

        fun stop(reason: StopReason = StopReason.USER) {
            if (_state.value is TransferServerState.Stopped) return
            finish(reason)
        }

        private fun finish(reason: StopReason) {
            watchdog?.cancel()
            watchdog = null
            networkCallback?.let { runCatching { connectivity?.unregisterNetworkCallback(it) } }
            networkCallback = null
            session?.close()
            session = null
            val closing = server
            val closingSink = sink
            server = null
            sink = null
            registry.cancelAllActive()
            refresher.flush()
            _state.value = TransferServerState.Stopped(reason)
            scope.launch(io) {
                closing?.stop()
                closingSink?.cleanUpLeftovers()
            }
        }

        private fun startWatchdog(current: TransferSession) {
            watchdog =
                scope.launch {
                    while (isActive) {
                        delay(WATCHDOG_INTERVAL_MS)
                        val now = System.currentTimeMillis()
                        when {
                            !current.isOpen(now) -> stop(StopReason.SESSION_EXPIRED)
                            activeUploads.get() == 0 && now - lastActivity.get() > IDLE_TIMEOUT_MS -> stop(StopReason.IDLE_TIMEOUT)
                            missingPermissions().isNotEmpty() -> stop(StopReason.PERMISSION_DENIED)
                        }
                    }
                }
        }

        /** Stops the session if its Wi-Fi network goes away or the phone's address on it changes. */
        private fun watchNetwork(
            network: Network,
            address: Inet4Address,
        ) {
            val callback =
                object : ConnectivityManager.NetworkCallback() {
                    override fun onLost(lost: Network) {
                        if (lost == network) scope.launch { stop(StopReason.WIFI_LOST) }
                    }

                    override fun onLinkPropertiesChanged(
                        changed: Network,
                        properties: LinkProperties,
                    ) {
                        if (changed == network && properties.linkAddresses.none { it.address == address }) {
                            scope.launch { stop(StopReason.WIFI_LOST) }
                        }
                    }
                }
            val request = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
            runCatching { connectivity?.registerNetworkCallback(request, callback) }
                .onSuccess { networkCallback = callback }
        }

        private class WifiNetwork(
            val network: Network,
            val address: Inet4Address,
        )

        /** The connected Wi-Fi network and the phone's IPv4 address on it, or null without Wi-Fi. */
        @Suppress("DEPRECATION")
        private fun currentWifi(): WifiNetwork? {
            val cm = connectivity ?: return null
            return cm.allNetworks.firstNotNullOfOrNull { network ->
                val caps = cm.getNetworkCapabilities(network)
                val ipv4 =
                    cm
                        .getLinkProperties(network)
                        ?.linkAddresses
                        ?.map { it.address }
                        ?.filterIsInstance<Inet4Address>()
                        ?.firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress }
                if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true && ipv4 != null) WifiNetwork(network, ipv4) else null
            }
        }

        private fun isGranted(permission: String) =
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

        companion object {
            private const val TAG = "WifiTransfer"

            /** Android 17 (API 37): local network access needs this runtime permission. */
            const val ANDROID_17 = 37
            const val ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"

            /** Stop after this long without any request from the computer (no upload running). */
            const val IDLE_TIMEOUT_MS = 10L * 60 * 1000
            private const val WATCHDOG_INTERVAL_MS = 15_000L
        }
    }
