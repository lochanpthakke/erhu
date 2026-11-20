package com.erhu.erhu.core.common.artwork

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import com.erhu.erhu.core.common.perf.Interactions
import com.erhu.erhu.core.common.perf.traced
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/**
 * Loads album artwork for a song off the main thread, with an in-memory cache.
 *
 * Missing or unreadable artwork is never an error: it simply yields null and
 * the caller shows a placeholder.
 */
object ArtworkLoader {
    private const val CACHE_SIZE_KB = 16 * 1024
    private const val MISS_CACHE_SIZE = 512
    private const val BYTES_PER_KB = 1024
    private const val LATEST_CACHE_ENTRIES = 64
    private const val DISPLAY_CACHE_SIZE_KB = 24 * 1024

    private val bitmapCache =
        object : LruCache<String, Bitmap>(CACHE_SIZE_KB) {
            override fun sizeOf(
                key: String,
                value: Bitmap,
            ): Int = (value.byteCount / BYTES_PER_KB).coerceAtLeast(1)
        }

    /**
     * The most recently loaded bitmap per song or album art URI, any size.
     */
    private val latestBySong = LruCache<String, Bitmap>(LATEST_CACHE_ENTRIES)

    /** Any already-loaded bitmap for [songUri] or [albumArtUri], without loading anything. */
    fun peek(
        songUri: String,
        albumArtUri: String? = null,
    ): Bitmap? =
        latestBySong.get(songUri)
            ?: albumArtUri?.let { latestBySong.get(it) }

    /**
     * GPU (hardware) copies of [bitmapCache] entries, for drawing only. A software bitmap is
     * uploaded to the GPU on the RenderThread in the first frame that draws it, and an artwork
     * card's bitmap can be 1500 px square: 6-11 ms of a 8 ms frame, exactly when the Now Playing
     * carousel brings a new card in. The copy does that upload on a worker instead.
     */
    private val displayCache =
        object : LruCache<String, Bitmap>(DISPLAY_CACHE_SIZE_KB) {
            override fun sizeOf(
                key: String,
                value: Bitmap,
            ): Int = (value.byteCount / BYTES_PER_KB).coerceAtLeast(1)
        }

    /** The most recently loaded display copy per song, any size (see [peek]). */
    private val latestDisplayBySong = LruCache<String, Bitmap>(LATEST_CACHE_ENTRIES)

    /** Like [peek], preferring a display copy. */
    fun peekForDisplay(songUri: String): Bitmap? = latestDisplayBySong.get(songUri) ?: peek(songUri)

    /**
     * [load], as a hardware bitmap for on-screen artwork. Its pixels cannot be read or drawn on a
     * software Canvas: callers that process the image (colours, widgets, notification) use [load].
     */
    @Suppress("ReturnCount")
    suspend fun loadForDisplay(
        context: Context,
        songUri: String,
        albumArtUri: String?,
        sizePx: Int,
        customArtworkPath: String? = null,
    ): Bitmap? {
        val cacheKeySource = customArtworkPath ?: songUri
        val key = "$cacheKeySource@$sizePx"
        displayCache.get(key)?.let {
            latestDisplayBySong.put(cacheKeySource, it)
            return it
        }
        val bitmap =
            load(context, songUri, albumArtUri, sizePx, customArtworkPath)
                ?: awaitRemoteCover(context, songUri, albumArtUri, sizePx, customArtworkPath)
                ?: return null
        val shown =
            withContext(Dispatchers.Default) {
                traced("Artwork upload") { bitmap.copy(Bitmap.Config.HARDWARE, false) ?: bitmap }
            }
        displayCache.put(key, shown)
        latestDisplayBySong.put(cacheKeySource, shown)
        return shown
    }

    /** Songs known to have no artwork, so lists don't retry them on every bind. */
    private val missCache = LruCache<String, Boolean>(MISS_CACHE_SIZE)

    /** Drops cached artwork (and "no artwork" results) for a song whose file changed. */
    fun invalidate(songUri: String) {
        latestBySong.remove(songUri)
        latestDisplayBySong.remove(songUri)
        val prefix = "$songUri@"
        displayCache
            .snapshot()
            .keys
            .filter { it.startsWith(prefix) }
            .forEach { displayCache.remove(it) }
        bitmapCache
            .snapshot()
            .keys
            .filter { it.startsWith(prefix) }
            .forEach { bitmapCache.remove(it) }
        missCache
            .snapshot()
            .keys
            .filter { it.startsWith(prefix) }
            .forEach { missCache.remove(it) }
    }

    @Suppress("ReturnCount")
    suspend fun load(
        context: Context,
        songUri: String,
        albumArtUri: String?,
        sizePx: Int,
        customArtworkPath: String? = null,
    ): Bitmap? {
        if ((songUri.isBlank() && customArtworkPath.isNullOrBlank()) || sizePx <= 0) return null
        val cacheKeySource = customArtworkPath ?: albumArtUri?.takeIf { it.startsWith(REMOTE_ARTWORK_PREFIX) } ?: songUri
        val key = "$cacheKeySource@$sizePx"
        bitmapCache.get(key)?.let {
            latestBySong.put(cacheKeySource, it)
            if (!albumArtUri.isNullOrBlank()) {
                latestBySong.put(albumArtUri, it)
            }
            return it
        }
        if (missCache.get(key) != null) return null

        // A cover that could not be fetched (offline, server down) is retried later, not remembered as missing.
        var failedForNow = false
        val bitmap =
            withContext(decodeDispatcher) {
                decoding(+1)
                try {
                    traced("Artwork decode") {
                        customArtworkPath?.takeIf { java.io.File(it).exists() }?.let { loadFromFile(it, sizePx) }
                            ?: if (songUri.startsWith(REMOTE_STREAM_PREFIX) || songUri.startsWith(REMOTE_ARTWORK_PREFIX)) {
                                // A remote track has no local file to read a picture from: only its provider's cover.
                                // A remote album or artist has no track at all and passes its cover as both arguments.
                                loadRemote(albumArtUri ?: songUri, sizePx) { failedForNow = true }
                            } else if (songUri == albumArtUri || isImageUri(songUri)) {
                                // An album or artist cover (no song): read the image itself. Opening it as a song
                                // (metadata reader, then thumbnail) only wasted seconds before this direct read.
                                // MediaStore album art opens reliably through the thumbnail API on Android 10+;
                                // reading its stream only works while the system still has the cover cached.
                                (if (songUri.startsWith("content://")) loadThumbnail(context, songUri, sizePx) else null)
                                    ?: loadFromStream(context, songUri, sizePx) { failedForNow = true }
                            } else {
                                loadEmbeddedPicture(context, songUri, sizePx)
                                    ?: loadThumbnail(context, songUri, sizePx)
                                    ?: albumArtUri?.let { loadFromStream(context, it, sizePx) { failedForNow = true } }
                            }
                    }
                } finally {
                    decoding(-1)
                }
            }
        if (bitmap != null) {
            bitmapCache.put(key, bitmap)
            latestBySong.put(cacheKeySource, bitmap)
            if (!albumArtUri.isNullOrBlank()) {
                latestBySong.put(albumArtUri, bitmap)
            }
        } else if (!failedForNow) {
            missCache.put(key, true)
        }
        return bitmap
    }

    private fun loadFromFile(
        path: String,
        sizePx: Int,
    ): Bitmap? =
        try {
            val bytes = java.io.File(path).readBytes()
            decodeSampled(bytes, sizePx)
        } catch (e: Exception) {
            null
        }

    /**
     * Fetches the bytes of a cover held by a remote library, given its `erhu-remote-art://` URI.
     * Returns null when the library has no cover for it and throws when it could not be reached.
     * Blocking; called on an IO thread.
     */
    fun interface RemoteArtworkFetcher {
        @Throws(java.io.IOException::class)
        fun fetch(
            artworkUri: String,
            sizePx: Int,
        ): ByteArray?
    }

    /** Set once at start-up by the remote libraries; null means remote covers show the placeholder. */
    @Volatile
    var remoteArtworkFetcher: RemoteArtworkFetcher? = null

    // Same schemes as core:model's RemoteRef, which this module cannot see.
    private const val REMOTE_STREAM_PREFIX = "erhu-remote://"
    private const val REMOTE_ARTWORK_PREFIX = "erhu-remote-art://"

    @Suppress("TooGenericExceptionCaught", "SwallowedException", "ReturnCount")
    private fun loadRemote(
        artworkUri: String?,
        sizePx: Int,
        onUnreachable: () -> Unit,
    ): Bitmap? {
        if (artworkUri == null || !artworkUri.startsWith(REMOTE_ARTWORK_PREFIX)) return null
        val fetcher = remoteArtworkFetcher
        if (fetcher == null) {
            onUnreachable()
            return null
        }
        return try {
            fetcher.fetch(artworkUri, sizePx)?.let { decodeSampled(it, sizePx) }
        } catch (e: Exception) {
            onUnreachable()
            null
        }
    }

    /** Decodes running right now, reported to frame diagnostics while there are any. */
    private var decodesInFlight = 0

    @Synchronized
    private fun decoding(change: Int) {
        val before = decodesInFlight
        decodesInFlight += change
        if ((before == 0) != (decodesInFlight == 0)) {
            Interactions.put(Interactions.ARTWORK, if (decodesInFlight > 0) "decoding" else null)
        }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun loadThumbnail(
        context: Context,
        songUri: String,
        sizePx: Int,
    ): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return try {
            context.contentResolver.loadThumbnail(Uri.parse(songUri), Size(sizePx, sizePx), null)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Reads an image from a content, file or web address. Only "this cover does not exist" (a missing
     * file, HTTP 404) counts as no artwork; anything else (slow network, storage not ready right after
     * launch) calls [transient], so the cover is retried instead of staying blank until a restart.
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun loadFromStream(
        context: Context,
        uri: String,
        sizePx: Int,
        transient: () -> Unit,
    ): Bitmap? =
        try {
            val parsedUri = Uri.parse(uri)
            val bytes =
                when (parsedUri.scheme) {
                    "http", "https" -> {
                        val connection =
                            java.net.URL(uri).openConnection().apply {
                                connectTimeout = NETWORK_TIMEOUT_MS
                                readTimeout = NETWORK_TIMEOUT_MS
                            }
                        connection.getInputStream().use { it.readBytes() }
                    }
                    null, "file" ->
                        java.io
                            .File(parsedUri.path ?: uri)
                            .takeIf { it.isFile }
                            ?.readBytes()
                    else -> context.contentResolver.openInputStream(parsedUri)?.use { it.readBytes() }
                }
            val decoded = bytes?.let { decodeSampled(it, sizePx) }
            // Bytes that do not decode were most likely cut short.
            if (bytes != null && decoded == null) transient()
            decoded
        } catch (e: java.io.FileNotFoundException) {
            null
        } catch (e: Exception) {
            transient()
            null
        }

    private fun isImageUri(uri: String): Boolean =
        uri.startsWith("http://") ||
            uri.startsWith("https://") ||
            uri.startsWith("/") ||
            uri.startsWith("file://") ||
            uri.startsWith("content://media/external/audio/albumart")

    /**
     * A failed cover that may load later is retried a few times, waiting longer each time: a blank
     * cover right after launch fills in by itself instead of waiting for the next app start.
     */
    @Suppress("ReturnCount")
    suspend fun loadWithRetry(
        context: Context,
        songUri: String,
        albumArtUri: String?,
        sizePx: Int,
        customArtworkPath: String? = null,
    ): Bitmap? {
        val key = missKey(songUri, albumArtUri, sizePx, customArtworkPath)
        RETRY_DELAYS_MS.forEach { wait ->
            load(context, songUri, albumArtUri, sizePx, customArtworkPath)?.let { return it }
            if (missCache.get(key) != null) return null
            kotlinx.coroutines.delay(wait)
        }
        return load(context, songUri, albumArtUri, sizePx, customArtworkPath)
            ?: awaitRemoteCover(context, songUri, albumArtUri, sizePx, customArtworkPath)
    }

    private fun missKey(
        songUri: String,
        albumArtUri: String?,
        sizePx: Int,
        customArtworkPath: String?,
    ) = "${customArtworkPath ?: albumArtUri?.takeIf { it.startsWith(REMOTE_ARTWORK_PREFIX) } ?: songUri}@$sizePx"

    /**
     * A remote cover not read yet (a Drive folder's tag pass is still going): waits for new covers
     * instead of staying blank until the row is scrolled away and back. Null at once for anything
     * else (local, or known to have no cover); otherwise ends with the caller's coroutine.
     */
    @Suppress("ReturnCount")
    private suspend fun awaitRemoteCover(
        context: Context,
        songUri: String,
        albumArtUri: String?,
        sizePx: Int,
        customArtworkPath: String?,
    ): Bitmap? {
        val key = missKey(songUri, albumArtUri, sizePx, customArtworkPath)
        if (missCache.get(key) != null || !isRemote(songUri, albumArtUri)) return null
        var seen = remoteCoversVersion.value
        while (true) {
            remoteCoversVersion.first { it != seen }
            // Covers arrive in bursts: one retry per short window, not one per new cover.
            kotlinx.coroutines.delay(REMOTE_COVER_WAKE_MS)
            seen = remoteCoversVersion.value
            load(context, songUri, albumArtUri, sizePx, customArtworkPath)?.let { return it }
            if (missCache.get(key) != null) return null
        }
    }

    private fun isRemote(
        songUri: String,
        albumArtUri: String?,
    ) = songUri.startsWith(REMOTE_STREAM_PREFIX) ||
        songUri.startsWith(REMOTE_ARTWORK_PREFIX) ||
        albumArtUri?.startsWith(REMOTE_ARTWORK_PREFIX) == true

    private val remoteCoversVersion = MutableStateFlow(0)

    /** A remote library cached new covers: placeholders still waiting on one try again. */
    fun notifyRemoteCoversChanged() {
        remoteCoversVersion.update { it + 1 }
    }

    /** At most a few covers decode at once, so the first screen after launch is not starved by dozens. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val decodeDispatcher = Dispatchers.IO.limitedParallelism(DECODE_PARALLELISM)

    private const val NETWORK_TIMEOUT_MS = 10_000
    private const val DECODE_PARALLELISM = 6
    private val RETRY_DELAYS_MS = listOf(1_500L, 4_000L, 10_000L)
    private const val REMOTE_COVER_WAKE_MS = 750L

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun loadEmbeddedPicture(
        context: Context,
        songUri: String,
        sizePx: Int,
    ): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, Uri.parse(songUri))
            retriever.embeddedPicture?.let { decodeSampled(it, sizePx) }
        } catch (e: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // Nothing useful to do if release fails.
            }
        }
    }

    private fun decodeSampled(
        bytes: ByteArray,
        sizePx: Int,
    ): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= sizePx && bounds.outHeight / (sample * 2) >= sizePx) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }
}
