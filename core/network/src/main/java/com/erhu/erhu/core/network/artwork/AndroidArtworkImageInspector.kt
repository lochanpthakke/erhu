package com.erhu.erhu.core.network.artwork

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import javax.inject.Inject

class AndroidArtworkImageInspector
    @Inject
    constructor() : ArtworkImageInspector {
        override fun inspect(bytes: ByteArray): ArtworkImageFacts? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val width = bounds.outWidth
            val height = bounds.outHeight
            if (width < 64 || height < 64 || width > 12000 || height > 12000 || width.toLong() * height > 60000000) return null
            if (width.toDouble() / height !in 0.60..1.65) return null
            var sample = 1
            while (maxOf(width, height) / sample > 64) sample *= 2
            val bitmap =
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
            val small = Bitmap.createScaledBitmap(bitmap, 9, 8, true)
            val pixels = IntArray(72)
            small.getPixels(pixels, 0, 9, 0, 0, 9, 8)
            val fingerprint = artworkVisualHash(pixels)
            if (small !== bitmap) small.recycle()
            bitmap.recycle()
            return ArtworkImageFacts(width, height, fingerprint)
        }
    }
