package com.lochanpthakke.empee3.core.playback.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import com.lochanpthakke.empee3.core.playback.songUriFromExtras
import java.io.IOException
import java.util.concurrent.Executors

/**
 * Artwork for the media notification / lock screen.
 *
 * The MediaStore album-art URI is not reliable (on Samsung One UI it returns
 * data that fails to decode), so for our own songs the picture is read from the
 * audio file itself: MediaStore's generated thumbnail on API 29+, the embedded
 * picture otherwise. Anything else falls back to Media3's default loader.
 */
@UnstableApi
internal class SongArtworkBitmapLoader(
    private val context: Context,
) : BitmapLoader {
    private val executor: ListeningExecutorService = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())
    private val fallback = DataSourceBitmapLoader(context)

    override fun supportsMimeType(mimeType: String): Boolean = fallback.supportsMimeType(mimeType)

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> = fallback.decodeBitmap(data)

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> = fallback.loadBitmap(uri)

    override fun loadBitmapFromMetadata(metadata: MediaMetadata): ListenableFuture<Bitmap>? {
        val songUri = metadata.extras?.songUriFromExtras() ?: return super.loadBitmapFromMetadata(metadata)
        return executor.submit<Bitmap> {
            loadFromSong(Uri.parse(songUri)) ?: throw IOException("No artwork for $songUri")
        }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun loadFromSong(uri: Uri): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                return context.contentResolver.loadThumbnail(uri, Size(ARTWORK_SIZE_PX, ARTWORK_SIZE_PX), null)
            } catch (e: Exception) {
                // No thumbnail — try the embedded picture below.
            }
        }
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.embeddedPicture?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
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

    companion object {
        private const val ARTWORK_SIZE_PX = 512
    }
}
