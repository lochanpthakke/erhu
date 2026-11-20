package com.erhu.erhu.core.model

import java.io.IOException
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

/** The kinds of remote library erhu can connect to. [key] is stored and appears in track URIs. */
enum class RemoteProvider(
    val key: String,
) {
    GOOGLE_DRIVE("drive"),
    SUBSONIC("subsonic"),
    ;

    companion object {
        fun fromKey(key: String?): RemoteProvider? = entries.firstOrNull { it.key == key }
    }
}

/** One connected account or server. Several sources of the same provider can exist side by side. */
data class RemoteSource(
    val id: Long,
    val provider: RemoteProvider,
    val name: String,
    /** Server address (Subsonic); null for providers with a fixed endpoint. */
    val baseUrl: String? = null,
    /** Account or user name shown to the user. Never a password or token. */
    val account: String? = null,
    /** The user accepted plain HTTP for this server. */
    val allowInsecure: Boolean = false,
)

/** A track as a provider describes it, before erhu has given it a library id. */
data class RemoteTrack(
    val remoteId: String,
    val title: String,
    val artist: String = "",
    val album: String = "",
    val durationMs: Long = 0L,
    val trackNumber: Int = 0,
    val discNumber: Int = 1,
    val mimeType: String? = null,
    /** Provider-side handle of the cover (cover-art id, file id), or null when there is none. */
    val artworkKey: String? = null,
    val isFavorite: Boolean = false,
    /** The server's genre and year, when it reports them (Subsonic does; Drive knows only file names). */
    val genre: String? = null,
    val year: Int = 0,
)

/**
 * Identity of a remote track: the source it belongs to plus the provider's own id. This pair, not
 * the title or the local id, is what makes two remote tracks the same.
 *
 * It travels inside [Song.uri] as `erhu-remote://<provider>/<sourceId>/<remoteId>`. That URI holds
 * no address, user name or token: the playable URL is produced by a [RemoteStreamResolver] only at
 * the moment the player opens the stream.
 */
data class RemoteRef(
    val provider: RemoteProvider,
    val sourceId: Long,
    val remoteId: String,
) {
    fun streamUri(): String = build(STREAM_SCHEME, provider, sourceId, remoteId)

    companion object {
        const val STREAM_SCHEME = "erhu-remote"
        const val ARTWORK_SCHEME = "erhu-remote-art"

        /** URI of a cover held by the provider; resolved by the artwork fetcher, never fetched as-is. */
        fun artworkUri(
            provider: RemoteProvider,
            sourceId: Long,
            artworkKey: String,
        ): String = build(ARTWORK_SCHEME, provider, sourceId, artworkKey)

        fun isStreamUri(uri: String?): Boolean = uri?.startsWith("$STREAM_SCHEME://") == true

        fun parseStream(uri: String?): RemoteRef? = parse(uri, STREAM_SCHEME)

        /** The reference inside an artwork URI; its [remoteId] is the artwork key. */
        fun parseArtwork(uri: String?): RemoteRef? = parse(uri, ARTWORK_SCHEME)

        private fun build(
            scheme: String,
            provider: RemoteProvider,
            sourceId: Long,
            id: String,
        ): String = "$scheme://${provider.key}/$sourceId/${URLEncoder.encode(id, "UTF-8")}"

        @Suppress("ReturnCount", "SwallowedException")
        private fun parse(
            uri: String?,
            scheme: String,
        ): RemoteRef? {
            if (uri == null || !uri.startsWith("$scheme://")) return null
            return try {
                val parsed = URI(uri)
                val provider = RemoteProvider.fromKey(parsed.host) ?: return null
                // Raw path: the id is percent-encoded and may itself contain an encoded slash.
                val parts =
                    parsed.rawPath
                        .orEmpty()
                        .trim('/')
                        .split('/')
                if (parts.size != 2) return null
                val sourceId = parts[0].toLongOrNull() ?: return null
                val id = URLDecoder.decode(parts[1], "UTF-8").takeIf { it.isNotEmpty() } ?: return null
                RemoteRef(provider, sourceId, id)
            } catch (e: IllegalArgumentException) {
                null
            } catch (e: java.net.URISyntaxException) {
                null
            }
        }
    }
}

/**
 * Local ids of remote tracks live far above anything MediaStore hands out, so a remote track can
 * never be mistaken for a device file anywhere an id is stored (queue, history, bookmarks).
 */
object RemoteIds {
    const val FIRST: Long = 1L shl 40

    fun isRemote(id: Long): Boolean = id >= FIRST
}

/** What the player needs to open a remote track right now. Never stored and never logged. */
class ResolvedStream(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
) {
    override fun toString(): String = "ResolvedStream(<redacted>)"
}

/**
 * Turns a [RemoteRef] into a playable request. Called on a loading thread each time the player
 * opens or re-opens the stream (start, seek, retry), so short-lived tokens are always current.
 * May block. Throws [RemoteAccessException] when the track cannot be offered.
 */
interface RemoteStreamResolver {
    val provider: RemoteProvider

    @Throws(IOException::class)
    fun resolve(ref: RemoteRef): ResolvedStream
}

/** Why a remote track cannot be opened, in terms the UI can act on. */
class RemoteAccessException(
    val reason: Reason,
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause) {
    enum class Reason {
        /** Sign-in expired, was revoked, or the credentials are wrong. */
        AUTH_REQUIRED,

        /** The source was removed from erhu, or no resolver exists for it. */
        SOURCE_MISSING,

        /** The track was deleted or is no longer shared. */
        NOT_FOUND,
    }
}

val Song.remote: RemoteRef? get() = RemoteRef.parseStream(uri)

val Song.isRemote: Boolean get() = RemoteRef.isStreamUri(uri)

/**
 * Fetches a cover a provider holds. [ref] comes from an artwork URI, so its remoteId is the
 * artwork key. Blocking. Returns null when there is no cover; throws when it cannot be reached.
 */
interface RemoteArtworkSource {
    val provider: RemoteProvider

    @Throws(IOException::class)
    fun fetch(
        ref: RemoteRef,
        sizePx: Int,
    ): ByteArray?
}

/** The connected sources, for code that cannot suspend (stream and artwork resolution). Blocking. */
interface RemoteSourceLookup {
    fun source(id: Long): RemoteSource?
}
