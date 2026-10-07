package com.lochanpthakke.empee3.core.playback.service

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import com.google.common.util.concurrent.Futures
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
 * Uses [ArtworkLoader]'s in-memory cache for instant 0ms hits when skipping tracks.
 * Stale background requests from rapid multi-skips are automatically canceled.
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

        // Fast path: instant 0ms memory cache hit for single atomic notification update
        ArtworkLoader.peek(songUri, albumArtUri)?.let { cachedBitmap ->
            return Futures.immediateFuture(cachedBitmap)
        }

        // Slow path: cancel stale in-flight request from previous rapid skips
        activeTask?.cancel(true)

        val task =
            executor.submit<Bitmap> {
                runBlocking {
                    ArtworkLoader.load(context, songUri, albumArtUri, ARTWORK_SIZE_PX)
                } ?: throw IOException("No artwork for $songUri")
            }
        activeTask = task
        return task
    }

    companion object {
        private const val ARTWORK_SIZE_PX = 512
    }
}
