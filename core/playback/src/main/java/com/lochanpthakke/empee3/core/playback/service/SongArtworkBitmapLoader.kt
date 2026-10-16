package com.lochanpthakke.empee3.core.playback.service

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import com.lochanpthakke.empee3.core.common.artwork.ArtworkLoader
import com.lochanpthakke.empee3.core.playback.songUriFromExtras
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.util.concurrent.Executors

/**
 * Artwork for the media notification / lock screen.
 *
 * Uses [ArtworkLoader]'s size-keyed cache on a worker. Notification artwork must
 * not reuse an arbitrary hero-sized bitmap and parcel it during a swipe callback.
 */
@UnstableApi
internal class SongArtworkBitmapLoader(
    private val context: Context,
) : BitmapLoader {
    private val executor: ListeningExecutorService =
        MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())
    private val fallback = DataSourceBitmapLoader(context)

    @Volatile
    private var activeTask: ListenableFuture<Bitmap>? = null

    override fun supportsMimeType(mimeType: String): Boolean = fallback.supportsMimeType(mimeType)

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> = fallback.decodeBitmap(data)

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> = fallback.loadBitmap(uri)

    override fun loadBitmapFromMetadata(metadata: MediaMetadata): ListenableFuture<Bitmap>? {
        val songUri = metadata.extras?.songUriFromExtras() ?: return super.loadBitmapFromMetadata(metadata)
        val albumArtUri = metadata.artworkUri?.toString()

        // Resolve the notification-sized cache entry and any resizing on the
        // worker rather than returning an arbitrary UI bitmap inline.
        activeTask?.cancel(true)

        val task =
            executor.submit<Bitmap> {
                val bitmap =
                    runBlocking {
                        ArtworkLoader.load(context, songUri, albumArtUri, ARTWORK_SIZE_PX)
                    } ?: throw IOException("No artwork for $songUri")
                // Embedded-image sampling can return more than the requested size.
                val largest = maxOf(bitmap.width, bitmap.height)
                if (largest > ARTWORK_SIZE_PX) {
                    val scale = ARTWORK_SIZE_PX.toFloat() / largest
                    Bitmap.createScaledBitmap(
                        bitmap,
                        (bitmap.width * scale).toInt().coerceAtLeast(1),
                        (bitmap.height * scale).toInt().coerceAtLeast(1),
                        true,
                    )
                } else {
                    bitmap
                }
            }
        activeTask = task
        return task
    }

    companion object {
        private const val ARTWORK_SIZE_PX = 512
    }
}
