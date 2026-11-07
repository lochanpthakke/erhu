package com.erhu.erhu.core.network.artwork

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import java.io.ByteArrayOutputStream

data class UpgradeImageFacts(
    val width: Int,
    val height: Int,
    val signature: IntArray,
    val hash: String,
)

/** Bounded software decoding, independent of Coil's display/crop/cache size. Call on IO. */
object ArtworkUpgradeImages {
    const val MAX_BYTES = 10 * 1024 * 1024

    @Suppress("ReturnCount")
    fun inspect(bytes: ByteArray): UpgradeImageFacts? {
        val bounds = bounds(bytes) ?: return null
        val bitmap = decode(bytes, bounds, 64) ?: return null
        val small = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(small)
            canvas.drawColor(Color.WHITE)
            canvas.drawBitmap(bitmap, null, Rect(0, 0, 16, 16), Paint(Paint.FILTER_BITMAP_FLAG))
            val pixels = IntArray(256)
            small.getPixels(pixels, 0, 16, 0, 0, 16, 16)
            return UpgradeImageFacts(bounds.outWidth, bounds.outHeight, pixels, AlbumArtworkCache.hash(bytes))
        } finally {
            small.recycle()
            bitmap.recycle()
        }
    }

    /** No upscaling. Saved bytes themselves are inspected and compared before offering a match. */
    @Suppress("ReturnCount")
    fun prepare(
        bytes: ByteArray,
        preserveTransparency: Boolean = true,
    ): ByteArray? {
        val bounds = bounds(bytes) ?: return null
        val bitmap = decode(bytes, bounds, ArtworkUpgradeMatch.MAX_SAVED_PIXELS * 2) ?: return null
        val scale = minOf(1.0, ArtworkUpgradeMatch.MAX_SAVED_PIXELS.toDouble() / maxOf(bitmap.width, bitmap.height))
        val result =
            Bitmap.createBitmap(
                maxOf(1, (bitmap.width * scale).toInt()),
                maxOf(1, (bitmap.height * scale).toInt()),
                Bitmap.Config.ARGB_8888,
            )
        return try {
            val canvas = Canvas(result)
            val keepAlpha = bitmap.hasAlpha() && preserveTransparency
            if (!keepAlpha) canvas.drawColor(Color.BLACK)
            canvas.drawBitmap(bitmap, null, Rect(0, 0, result.width, result.height), Paint(Paint.FILTER_BITMAP_FLAG))
            ByteArrayOutputStream().use { output ->
                // Album tag writing uses JPEG; review that representation rather than a transparent PNG.
                val format = if (keepAlpha) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                if (result.compress(format, 94, output) && output.size() <= MAX_BYTES) output.toByteArray() else null
            }
        } finally {
            result.recycle()
            bitmap.recycle()
        }
    }

    private fun bounds(bytes: ByteArray): BitmapFactory.Options? {
        if (bytes.isEmpty() || bytes.size > MAX_BYTES) return null
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        return options.takeIf { it.outWidth in 16..12000 && it.outHeight in 16..12000 && it.outWidth.toLong() * it.outHeight <= 60000000 }
    }

    private fun decode(
        bytes: ByteArray,
        bounds: BitmapFactory.Options,
        limit: Int,
    ): Bitmap? {
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > limit) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}
