package com.erhu.erhu.feature.library.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import com.erhu.erhu.core.model.Song
import kotlin.math.max
import kotlin.math.min

enum class CardAspectRatio(val width: Int, val height: Int, val ratio: Float) {
    STORY(1080, 1920, 9f / 16f),
    SQUARE(1080, 1080, 1f),
    FEED(1080, 1350, 4f / 5f),
}

enum class SongCardStyle {
    CENTERED,
    POSTER,
    MINIMAL,
}

enum class BlurIntensity(val iterations: Int, val downsample: Int) {
    SUBTLE(2, 6),
    MEDIUM(4, 8),
    DEEP(7, 10),
}

data class SongShareConfig(
    val aspectRatio: CardAspectRatio = CardAspectRatio.STORY,
    val style: SongCardStyle = SongCardStyle.CENTERED,
    val blur: BlurIntensity = BlurIntensity.MEDIUM,
    val darkness: Float = 0.45f,
    val showAlbum: Boolean = true,
)

enum class StatsCardType {
    TOP_SONGS,
    TOP_ARTISTS,
    TOP_ALBUMS,
    OVERVIEW,
}

data class StatsShareConfig(
    val aspectRatio: CardAspectRatio = CardAspectRatio.STORY,
    val darkness: Float = 0.5f,
)

object ShareCardRenderer {

    fun renderSongCard(
        context: Context,
        song: Song,
        artwork: Bitmap?,
        config: SongShareConfig,
    ): Bitmap {
        val width = config.aspectRatio.width
        val height = config.aspectRatio.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        // 1. Draw Blurred Background
        drawBackground(canvas, artwork, width, height, config.blur, config.darkness)

        // 2. Draw Artwork & Text based on style
        when (config.style) {
            SongCardStyle.CENTERED -> drawCenteredStyle(canvas, song, artwork, width, height, config.showAlbum)
            SongCardStyle.POSTER -> drawPosterStyle(canvas, song, artwork, width, height, config.showAlbum)
            SongCardStyle.MINIMAL -> drawMinimalStyle(canvas, song, artwork, width, height, config.showAlbum)
        }

        // 3. Subtle Erhu Branding at bottom
        drawBranding(canvas, width, height)

        return output
    }

    /**
     * Blurred [artwork] (centre-cropped, never stretched to the card's shape) or a dark gradient,
     * darkened by [darkness]. With [keepTextReadable], a bright cover is darkened at least far
     * enough for white text to stay readable (contrast of about 4.5:1 against the average colour);
     * the darkness setting still applies above that.
     */
    internal fun drawBackground(
        canvas: Canvas,
        artwork: Bitmap?,
        width: Int,
        height: Int,
        blur: BlurIntensity,
        darkness: Float,
        keepTextReadable: Boolean = false,
    ) {
        var dark = darkness.coerceIn(0.1f, 0.9f)
        if (artwork != null) {
            val blurred = fastBlur(artwork, blur.iterations, blur.downsample)
            canvas.drawBitmap(blurred, centreCrop(blurred, width.toFloat() / height), Rect(0, 0, width, height), Paint(Paint.FILTER_BITMAP_FLAG))
            if (keepTextReadable) dark = max(dark, readableDarkness(blurred))
        } else {
            // Dark gradient backdrop
            val bgPaint = Paint().apply {
                shader = LinearGradient(0f, 0f, 0f, height.toFloat(), Color.rgb(24, 26, 32), Color.rgb(8, 9, 12), Shader.TileMode.CLAMP)
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        }

        // Overlay darkening and subtle vignette
        val overlayPaint = Paint().apply {
            val darkAlpha = (dark * 255).toInt()
            shader = LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                Color.argb(darkAlpha, 0, 0, 0),
                Color.argb(min(255, darkAlpha + 60), 0, 0, 0),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)
    }

    /**
     * The black overlay share needed so white text has about 4.5:1 contrast against the
     * [image]'s average relative luminance.
     */
    private fun readableDarkness(image: Bitmap): Float {
        val pixels = IntArray(image.width * image.height)
        image.getPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
        if (pixels.isEmpty()) return 0f
        fun linear(c: Int): Double = (c / 255.0).let { if (it <= 0.03928) it / 12.92 else Math.pow((it + 0.055) / 1.055, 2.4) }
        val luminance =
            pixels.sumOf { 0.2126 * linear(Color.red(it)) + 0.7152 * linear(Color.green(it)) + 0.0722 * linear(Color.blue(it)) } / pixels.size
        // White on L: (1.05) / (L' + 0.05) >= 4.5  ->  L' <= 0.183, and L' = L * (1 - d).
        val target = 1.05 / READABLE_CONTRAST - 0.05
        return if (luminance <= target) 0f else (1.0 - target / luminance).toFloat().coerceIn(0f, 0.9f)
    }

    /** The part of [bitmap] with [aspect] (width / height), centred. */
    private fun centreCrop(
        bitmap: Bitmap,
        aspect: Float,
    ): Rect {
        val w = bitmap.width
        val h = bitmap.height
        return if (w.toFloat() / h > aspect) {
            val cw = (h * aspect).toInt().coerceAtLeast(1)
            Rect((w - cw) / 2, 0, (w - cw) / 2 + cw, h)
        } else {
            val ch = (w / aspect).toInt().coerceAtLeast(1)
            Rect(0, (h - ch) / 2, w, (h - ch) / 2 + ch)
        }
    }

    private const val READABLE_CONTRAST = 4.5


    private fun drawCenteredStyle(
        canvas: Canvas,
        song: Song,
        artwork: Bitmap?,
        width: Int,
        height: Int,
        showAlbum: Boolean,
    ) {
        val side = min(width * 0.72f, height * 0.42f)
        val left = (width - side) / 2f
        val top = height * 0.18f
        val artRect = RectF(left, top, left + side, top + side)

        // Drop shadow for artwork
        val shadowPaint = Paint().apply {
            color = Color.argb(120, 0, 0, 0)
            setShadowLayer(40f, 0f, 25f, Color.argb(140, 0, 0, 0))
        }
        canvas.drawRoundRect(artRect, 44f, 44f, shadowPaint)

        // Draw artwork with rounded corners
        if (artwork != null) {
            drawRoundedBitmap(canvas, artwork, artRect, 44f)
        } else {
            drawPlaceholderArtwork(canvas, artRect, 44f)
        }

        // Text below artwork
        var textY = top + side + 70f
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 54f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        // Title
        val title = ellipsize(song.title, textPaint, width * 0.85f)
        canvas.drawText(title, width / 2f, textY, textPaint)
        textY += 65f

        // Artist
        textPaint.apply {
            color = Color.argb(230, 255, 255, 255)
            textSize = 38f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val artist = ellipsize(song.artist, textPaint, width * 0.85f)
        canvas.drawText(artist, width / 2f, textY, textPaint)

        // Album
        if (showAlbum && song.album.isNotBlank()) {
            textY += 55f
            textPaint.apply {
                color = Color.argb(170, 255, 255, 255)
                textSize = 30f
            }
            val album = ellipsize(song.album, textPaint, width * 0.85f)
            canvas.drawText(album, width / 2f, textY, textPaint)
        }
    }

    private fun drawPosterStyle(
        canvas: Canvas,
        song: Song,
        artwork: Bitmap?,
        width: Int,
        height: Int,
        showAlbum: Boolean,
    ) {
        val margin = 80f
        val side = width - (margin * 2)
        val top = height * 0.12f
        val artRect = RectF(margin, top, margin + side, top + side)

        // Rounded image
        if (artwork != null) {
            drawRoundedBitmap(canvas, artwork, artRect, 32f)
        } else {
            drawPlaceholderArtwork(canvas, artRect, 32f)
        }

        var textY = top + side + 80f
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 62f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }

        val title = ellipsize(song.title, textPaint, side)
        canvas.drawText(title, margin, textY, textPaint)
        textY += 75f

        textPaint.apply {
            color = Color.argb(230, 255, 255, 255)
            textSize = 42f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val artist = ellipsize(song.artist, textPaint, side)
        canvas.drawText(artist, margin, textY, textPaint)

        if (showAlbum && song.album.isNotBlank()) {
            textY += 60f
            textPaint.apply {
                color = Color.argb(170, 255, 255, 255)
                textSize = 32f
            }
            val album = ellipsize(song.album, textPaint, side)
            canvas.drawText(album, margin, textY, textPaint)
        }
    }

    private fun drawMinimalStyle(
        canvas: Canvas,
        song: Song,
        artwork: Bitmap?,
        width: Int,
        height: Int,
        showAlbum: Boolean,
    ) {
        val side = min(width * 0.65f, height * 0.38f)
        val centerX = width / 2f
        val centerY = height * 0.36f
        val artRect = RectF(centerX - side / 2, centerY - side / 2, centerX + side / 2, centerY + side / 2)

        // Vinyl circle style
        val clipPath = Path().apply {
            addCircle(centerX, centerY, side / 2, Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(clipPath)
        if (artwork != null) {
            val src = Rect(0, 0, artwork.width, artwork.height)
            canvas.drawBitmap(artwork, src, artRect, Paint(Paint.FILTER_BITMAP_FLAG))
        } else {
            drawPlaceholderArtwork(canvas, artRect, side / 2)
        }
        canvas.restore()

        // Center vinyl hole
        val centerHolePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(240, 25, 25, 25)
        }
        canvas.drawCircle(centerX, centerY, side * 0.12f, centerHolePaint)

        var textY = centerY + side / 2 + 80f
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 50f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(ellipsize(song.title, textPaint, width * 0.85f), centerX, textY, textPaint)

        textY += 60f
        textPaint.apply {
            color = Color.argb(230, 255, 255, 255)
            textSize = 36f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        canvas.drawText(ellipsize(song.artist, textPaint, width * 0.85f), centerX, textY, textPaint)
    }

    internal fun drawMonogramAvatar(canvas: Canvas, name: String, dst: RectF) {
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(160, 65, 75, 90)
        }
        canvas.drawCircle(dst.centerX(), dst.centerY(), dst.width() / 2f, bgPaint)
        val initial = name.trim().firstOrNull()?.uppercase() ?: "?"
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = dst.width() * 0.45f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val textY = dst.centerY() - ((textPaint.descent() + textPaint.ascent()) / 2f)
        canvas.drawText(initial, dst.centerX(), textY, textPaint)
    }

    internal fun drawCircularBitmap(canvas: Canvas, bitmap: Bitmap, dst: RectF) {
        val path = Path().apply {
            addCircle(dst.centerX(), dst.centerY(), dst.width() / 2f, Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(path)
        canvas.drawBitmap(bitmap, centreCrop(bitmap, dst.width() / dst.height()), dst, Paint(Paint.FILTER_BITMAP_FLAG))
        canvas.restore()
    }

    /** "E R H U" centred at [baseline] (by default 60 px above the bottom edge). */
    internal fun drawBranding(canvas: Canvas, width: Int, height: Int, baseline: Float = height - 60f) {
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(160, 255, 255, 255)
            textSize = 22f
            letterSpacing = 0.28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("E R H U", width / 2f, baseline, textPaint)
    }

    internal fun drawRoundedBitmap(canvas: Canvas, bitmap: Bitmap, dst: RectF, radius: Float) {
        val path = Path().apply {
            addRoundRect(dst, radius, radius, Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(path)
        canvas.drawBitmap(bitmap, centreCrop(bitmap, dst.width() / dst.height()), dst, Paint(Paint.FILTER_BITMAP_FLAG))
        canvas.restore()
    }

    internal fun drawPlaceholderArtwork(canvas: Canvas, dst: RectF, radius: Float) {
        val bgPaint = Paint().apply {
            color = Color.argb(180, 45, 55, 72)
        }
        canvas.drawRoundRect(dst, radius, radius, bgPaint)
    }

    internal fun ellipsize(text: String, paint: TextPaint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 0 && paint.measureText(text.substring(0, end) + "…") > maxWidth) {
            end--
        }
        return if (end > 0) text.substring(0, end) + "…" else text
    }


    private fun fastBlur(sentBitmap: Bitmap, iterations: Int, scaleDown: Int): Bitmap {
        val width = max(1, sentBitmap.width / scaleDown)
        val height = max(1, sentBitmap.height / scaleDown)
        val small = Bitmap.createScaledBitmap(sentBitmap, width, height, true)
        val blurred = small.copy(Bitmap.Config.ARGB_8888, true)

        val w = blurred.width
        val h = blurred.height
        val pix = IntArray(w * h)
        blurred.getPixels(pix, 0, w, 0, 0, w, h)

        val radius = 12
        for (i in 0 until iterations) {
            boxBlur(pix, w, h, radius)
        }
        blurred.setPixels(pix, 0, w, 0, 0, w, h)
        return blurred
    }

    private fun boxBlur(pix: IntArray, w: Int, h: Int, r: Int) {
        val wm = w - 1
        val hm = h - 1
        val wh = w * h
        val div = r + r + 1

        val rAcc = IntArray(wh)
        val gAcc = IntArray(wh)
        val bAcc = IntArray(wh)

        var rsum: Int
        var gsum: Int
        var bsum: Int
        var p: Int
        var yi = 0
        var yw = 0

        for (y in 0 until h) {
            rsum = 0
            gsum = 0
            bsum = 0
            for (i in -r..r) {
                p = pix[yi + min(wm, max(i, 0))]
                rsum += (p and 0xff0000) shr 16
                gsum += (p and 0x00ff00) shr 8
                bsum += (p and 0x0000ff)
            }
            for (x in 0 until w) {
                rAcc[yi] = rsum / div
                gAcc[yi] = gsum / div
                bAcc[yi] = bsum / div

                val p1 = pix[yw + min(x + r + 1, wm)]
                val p2 = pix[yw + max(x - r, 0)]

                rsum += ((p1 and 0xff0000) - (p2 and 0xff0000)) shr 16
                gsum += ((p1 and 0x00ff00) - (p2 and 0x00ff00)) shr 8
                bsum += ((p1 and 0x0000ff) - (p2 and 0x0000ff))
                yi++
            }
            yw += w
        }

        for (x in 0 until w) {
            rsum = 0
            gsum = 0
            bsum = 0
            for (i in -r..r) {
                // Clamped to the image like the horizontal pass: small covers are shorter than the radius.
                yi = min(hm, max(i, 0)) * w + x
                rsum += rAcc[yi]
                gsum += gAcc[yi]
                bsum += bAcc[yi]
            }
            yi = x
            for (y in 0 until h) {
                pix[yi] = (0xff000000.toInt()) or (min(255, rsum / div) shl 16) or (min(255, gsum / div) shl 8) or min(255, bsum / div)
                val p1 = x + min(y + r + 1, hm) * w
                val p2 = x + max(y - r, 0) * w

                rsum += rAcc[p1] - rAcc[p2]
                gsum += gAcc[p1] - gAcc[p2]
                bsum += bAcc[p1] - bAcc[p2]
                yi += w
            }
        }
    }
}
