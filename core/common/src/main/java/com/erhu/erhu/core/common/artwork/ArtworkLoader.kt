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
        val bitmap = load(context, songUri, albumArtUri, sizePx, customArtworkPath) ?: return null
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
            withContext(Dispatchers.IO) {
                decoding(+1)
                try {
                    traced("Artwork decode") {
                        customArtworkPath?.takeIf { java.io.File(it).exists() }?.let { loadFromFile(it, sizePx) }
                            ?: if (songUri.startsWith(REMOTE_STREAM_PREFIX) || songUri.startsWith(REMOTE_ARTWORK_PREFIX)) {
                                // A remote track has no local file to read a picture from: only its provider's cover.
                                // A remote album or artist has no track at all and passes its cover as both arguments.
                                loadRemote(albumArtUri ?: songUri, sizePx) { failedForNow = true }
                            } else {
                                loadEmbeddedPicture(context, songUri, sizePx)
                                    ?: loadThumbnail(context, songUri, sizePx)
                                    ?: albumArtUri?.let { loadFromStream(context, it, sizePx) }
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

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun loadFromStream(
        context: Context,
        uri: String,
        sizePx: Int,
    ): Bitmap? =
        try {
            val parsedUri = Uri.parse(uri)
            val bytes =
                if (parsedUri.scheme == "http" || parsedUri.scheme == "https") {
                    java.net
                        .URL(uri)
                        .openConnection()
                        .apply {
                            connectTimeout = 3000
                            readTimeout = 3000
                        }.getInputStream()
                        .use { it.readBytes() }
                } else {
                    context.contentResolver.openInputStream(parsedUri)?.use { it.readBytes() }
                }
            bytes?.let { decodeSampled(it, sizePx) }
        } catch (e: Exception) {
            null
        }

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
