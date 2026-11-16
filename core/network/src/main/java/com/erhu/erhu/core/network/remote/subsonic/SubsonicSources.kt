package com.erhu.erhu.core.network.remote.subsonic

import com.erhu.erhu.core.model.RemoteAccessException
import com.erhu.erhu.core.model.RemoteAccessException.Reason
import com.erhu.erhu.core.model.RemoteArtworkSource
import com.erhu.erhu.core.model.RemoteProvider
import com.erhu.erhu.core.model.RemoteRef
import com.erhu.erhu.core.model.RemoteSource
import com.erhu.erhu.core.model.RemoteSourceLookup
import com.erhu.erhu.core.model.RemoteStreamResolver
import com.erhu.erhu.core.model.ResolvedStream
import com.erhu.erhu.core.network.remote.RemoteCredentialStore
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns a stored Subsonic source into a usable [SubsonicConnection] (address + user + password
 * from the credential store). Blocking: call from IO or loading threads.
 */
@Singleton
class SubsonicConnections
    @Inject
    constructor(
        private val sources: RemoteSourceLookup,
        private val credentials: RemoteCredentialStore,
    ) {
        private val cache = ConcurrentHashMap<Long, SubsonicConnection>()

        @Throws(IOException::class)
        fun connection(sourceId: Long): SubsonicConnection =
            cache[sourceId] ?: open(sourceId).also { cache[sourceId] = it }

        /** After a source is removed or its details change. */
        fun forget(sourceId: Long) {
            cache.remove(sourceId)
        }

        private fun open(sourceId: Long): SubsonicConnection {
            val source =
                sources.source(sourceId)?.takeIf { it.provider == RemoteProvider.SUBSONIC }
                    ?: throw RemoteAccessException(Reason.SOURCE_MISSING, "This server is no longer connected")
            val password =
                credentials.load(sourceId)
                    ?: throw RemoteAccessException(Reason.AUTH_REQUIRED, "Sign in to ${source.name} again")
            return connectionFor(source, password)
        }
    }

/** Plain HTTP is used only for a server the user explicitly allowed it for. */
@Throws(IOException::class)
fun connectionFor(
    source: RemoteSource,
    password: String,
): SubsonicConnection {
    val url = source.baseUrl?.let(::parseServerUrl) ?: throw IOException("The server address is not valid")
    if (!url.isHttps && !source.allowInsecure) throw IOException("Unencrypted connections are not allowed for this server")
    return SubsonicConnection(url, source.account.orEmpty(), password)
}

@Singleton
class SubsonicStreamResolver
    @Inject
    constructor(
        private val connections: SubsonicConnections,
        private val client: SubsonicClient,
    ) : RemoteStreamResolver {
        override val provider = RemoteProvider.SUBSONIC

        override fun resolve(ref: RemoteRef): ResolvedStream =
            ResolvedStream(client.streamUrl(connections.connection(ref.sourceId), ref.remoteId))
    }

@Singleton
class SubsonicArtworkSource
    @Inject
    constructor(
        private val connections: SubsonicConnections,
        private val client: SubsonicClient,
    ) : RemoteArtworkSource {
        override val provider = RemoteProvider.SUBSONIC

        override fun fetch(
            ref: RemoteRef,
            sizePx: Int,
        ): ByteArray? = client.coverArt(connections.connection(ref.sourceId), ref.remoteId, sizePx.coerceIn(MIN_SIZE_PX, MAX_SIZE_PX))

        private companion object {
            const val MIN_SIZE_PX = 64
            const val MAX_SIZE_PX = 1024
        }
    }
