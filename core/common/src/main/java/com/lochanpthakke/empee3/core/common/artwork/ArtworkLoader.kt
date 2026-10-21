package com.lochanpthakke.empee3.core.common.artwork

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import com.lochanpthakke.empee3.core.common.perf.Interactions
import com.lochanpthakke.empee3.core.common.perf.traced
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

    /** Songs known to have no artwork, so lists don't retry them on every bind. */
    private val missCache = LruCache<String, Boolean>(MISS_CACHE_SIZE)

    /** Drops cached artwork (and "no artwork" results) for a song whose file changed. */
    fun invalidate(songUri: String) {
        latestBySong.remove(songUri)
        val prefix = "$songUri@"
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
    ): Bitmap? {
        if (songUri.isBlank() || sizePx <= 0) return null
        val key = "$songUri@$sizePx"
        bitmapCache.get(key)?.let {
            latestBySong.put(songUri, it)
            if (!albumArtUri.isNullOrBlank()) {
                latestBySong.put(albumArtUri, it)
            }
            return it
        }
        if (missCache.get(key) != null) return null

        val bitmap =
            withContext(Dispatchers.IO) {
                decoding(+1)
                try {
                    traced("Artwork decode") {
                        loadThumbnail(context, songUri, sizePx)
                            ?: albumArtUri?.let { loadFromStream(context, it, sizePx) }
                            ?: loadEmbeddedPicture(context, songUri, sizePx)
                    }
                } finally {
                    decoding(-1)
                }
            }
        if (bitmap != null) {
            bitmapCache.put(key, bitmap)
            latestBySong.put(songUri, bitmap)
            if (!albumArtUri.isNullOrBlank()) {
                latestBySong.put(albumArtUri, bitmap)
            }
        } else {
            missCache.put(key, true)
        }
        return bitmap
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
            val bytes = if (parsedUri.scheme == "http" || parsedUri.scheme == "https") {
                java.net.URL(uri).openConnection().apply {
                    connectTimeout = 3000
                    readTimeout = 3000
                }.getInputStream().use { it.readBytes() }
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
