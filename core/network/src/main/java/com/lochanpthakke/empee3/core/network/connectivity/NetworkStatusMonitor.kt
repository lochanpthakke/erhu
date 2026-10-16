package com.lochanpthakke.empee3.core.network.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetAddress
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

/** Whether this app can reach the internet right now. */
enum class NetworkStatus {
    AVAILABLE,

    /**
     * A network exists but Android blocks this app from using it: in the background with Battery
     * Saver or Data Saver on. Requests then fail as DNS errors ("No address associated with hostname").
     */
    BLOCKED,
    OFFLINE,
}

/** The app's network state, and a cheap reachability check that sends nothing to providers. */
interface NetworkStatusSource {
    val status: StateFlow<NetworkStatus>

    /** For each host, whether its address resolves now (DNS only, no request to the provider). */
    suspend fun resolves(hosts: Collection<String>): Map<String, Boolean>
}

/** [NetworkStatusSource] from Android's default-network callbacks, including the per-app "blocked" signal. */
@Singleton
class AndroidNetworkStatusMonitor
    @Inject
    constructor(
        @param:ApplicationContext context: Context,
    ) : NetworkStatusSource {
        private val connectivity = context.getSystemService(ConnectivityManager::class.java)
        private val _status = MutableStateFlow(initialStatus())
        override val status: StateFlow<NetworkStatus> = _status.asStateFlow()

        private var hasInternet = _status.value != NetworkStatus.OFFLINE
        private var blocked = _status.value == NetworkStatus.BLOCKED

        init {
            connectivity?.registerDefaultNetworkCallback(
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        hasInternet = true
                        publish()
                    }

                    override fun onLost(network: Network) {
                        hasInternet = false
                        publish()
                    }

                    override fun onCapabilitiesChanged(
                        network: Network,
                        capabilities: NetworkCapabilities,
                    ) {
                        hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        publish()
                    }

                    override fun onBlockedStatusChanged(
                        network: Network,
                        isBlocked: Boolean,
                    ) {
                        blocked = isBlocked
                        publish()
                    }
                },
            )
        }

        private fun publish() {
            _status.value =
                when {
                    !hasInternet -> NetworkStatus.OFFLINE
                    blocked -> NetworkStatus.BLOCKED
                    else -> NetworkStatus.AVAILABLE
                }
        }

        @Suppress("DEPRECATION", "ReturnCount")
        private fun initialStatus(): NetworkStatus {
            val manager = connectivity ?: return NetworkStatus.OFFLINE
            val network = manager.activeNetwork ?: return NetworkStatus.OFFLINE
            val capabilities = manager.getNetworkCapabilities(network)
            return when {
                capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) != true -> NetworkStatus.OFFLINE
                manager.getNetworkInfo(network)?.detailedState == android.net.NetworkInfo.DetailedState.BLOCKED -> NetworkStatus.BLOCKED
                else -> NetworkStatus.AVAILABLE
            }
        }

        override suspend fun resolves(hosts: Collection<String>): Map<String, Boolean> =
            withContext(Dispatchers.IO) {
                coroutineScope {
                    hosts
                        .distinct()
                        .map { host ->
                            async {
                                host to (
                                    withTimeoutOrNull(DNS_TIMEOUT_MS) {
                                        try {
                                            InetAddress.getAllByName(host).isNotEmpty()
                                        } catch (_: UnknownHostException) {
                                            false
                                        } catch (_: SecurityException) {
                                            false
                                        }
                                    } ?: false
                                )
                            }
                        }.awaitAll()
                        .toMap()
                }
            }

        private companion object {
            const val DNS_TIMEOUT_MS = 8_000L
        }
    }
