package com.erhu.erhu.core.designsystem.artwork

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.erhu.erhu.core.designsystem.theme.LocalGlassTokens
import com.erhu.erhu.core.model.Song
import kotlinx.coroutines.delay

/** Tiny source bitmap: upscaling it is itself a soft blur, and it costs almost nothing. */
private const val BACKDROP_SOURCE_PX = 48
private const val DEFAULT_BLUR_RADIUS_DP = 40
private const val DEFAULT_FALLBACK_SCRIM_ALPHA = 0.85f

/**
 * The flattened backdrop is rendered at this share of the screen's size and drawn scaled up: under a
 * 40 dp blur nothing finer survives, and each render (and its GPU readback) is ~16x cheaper.
 */
private const val RASTER_SCALE = 0.25f

/** Rendered backdrops kept: the current track's, its neighbours' and the one just left. */
private const val RASTER_CACHE_SIZE = 4

/** Neighbours are pre-rendered only once a track change (and its card motion) has settled. */
private const val PREWARM_DELAY_MS = 700L

/** A render waits this long, so superseded slider/theme changes never render at all. */
private const val RENDER_DELAY_MS = 100L

/**
 * Full-bleed, blurred album art behind the Now Playing screen.
 *
 * - Android 12+: the blur is flattened once per track into a small bitmap (see [RASTER_SCALE]),
 *   so the glass consumers sample a plain image. The [upcoming] tracks (queue neighbours) are
 *   rendered ahead of time, so a skip swaps in a ready image instead of blurring live while the
 *   player animates. A track that is not ready yet keeps the previous image until its own is.
 * - Android 8–11: Modifier.blur is a no-op there, so the look comes from
 *   upscaling a 48 px thumbnail with bilinear filtering plus the scrim.
 * When artwork is unavailable, draws the theme background to keep the layer intact.
 */
@Composable
fun GlassArtworkBackdrop(
    song: Song?,
    modifier: Modifier = Modifier,
    upcoming: List<Song?> = emptyList(),
) {
    val glass = LocalGlassTokens.current
    val context = LocalContext.current
    val opaqueBackground = MaterialTheme.colorScheme.background.copy(alpha = 1f)
    val blurRadius = (glass?.artworkBlurRadiusDp ?: DEFAULT_BLUR_RADIUS_DP).dp
    val scrimColor =
        glass?.let { Color(it.artworkScrim) }
            ?: MaterialTheme.colorScheme.background.copy(alpha = DEFAULT_FALLBACK_SCRIM_ALPHA)
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val graphics = LocalGraphicsContext.current
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val cache = remember { BackdropCache() }
    val style = BackdropStyle(viewport, blurRadius, opaqueBackground, scrimColor, density.density)
    val rasterSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && viewport.width > 0 && viewport.height > 0
    val key = song?.let { BackdropKey(it.uri, it.artworkUri, style) }

    // The flattened image on screen: the current track's once rendered, until then the last one shown.
    var shown by remember { mutableStateOf<ImageBitmap?>(null) }
    val raster = if (key == null || !rasterSupported) null else cache.get(key) ?: shown
    // Before any image is flattened (first open, older Android): the live blur of the small source.
    val source by produceState<Bitmap?>(initialValue = null, song?.uri, song?.artworkUri) {
        value = song?.let { ArtworkLoader.load(context, it.uri, it.artworkUri, BACKDROP_SOURCE_PX) }
    }

    LaunchedEffect(key, rasterSupported) {
        if (key == null || !rasterSupported) return@LaunchedEffect
        cache.get(key)?.let {
            shown = it
            return@LaunchedEffect
        }
        // Superseded changes (rapid skips, slider drags) are cancelled here; the previous image stays meanwhile.
        delay(RENDER_DELAY_MS)
        val bitmap = ArtworkLoader.load(context, key.uri, key.artworkUri, BACKDROP_SOURCE_PX)
        val raster = bitmap?.let { renderBackdrop(graphics, density, direction, it, style) }
        if (raster != null) cache.put(key, raster)
        shown = raster
    }
    // Pre-render the neighbours after the change has settled, one at a time, away from the card motion.
    val upcomingKeys = upcoming.mapNotNull { next -> next?.let { BackdropKey(it.uri, it.artworkUri, style) } }
    LaunchedEffect(upcomingKeys, rasterSupported) {
        if (!rasterSupported) return@LaunchedEffect
        delay(PREWARM_DELAY_MS)
        for (next in upcomingKeys) {
            if (cache.get(next) != null) continue
            val bitmap = ArtworkLoader.load(context, next.uri, next.artworkUri, BACKDROP_SOURCE_PX) ?: continue
            renderBackdrop(graphics, density, direction, bitmap, style)?.let { cache.put(next, it) }
        }
    }

    val live = source
    Box(modifier = modifier.fillMaxSize().background(opaqueBackground).onSizeChanged { viewport = it }) {
        when {
            raster != null ->
                Image(
                    bitmap = raster,
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    filterQuality = FilterQuality.Low,
                    modifier = Modifier.fillMaxSize(),
                )
            live != null -> {
                Image(
                    bitmap = live.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.Low,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .blur(blurRadius),
                )
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(scrimColor),
                )
            }
        }
    }
}

private data class BackdropStyle(
    val viewport: IntSize,
    val radius: Dp,
    val background: Color,
    val scrim: Color,
    val density: Float,
)

private data class BackdropKey(
    val uri: String,
    val artworkUri: String?,
    val style: BackdropStyle,
)

/** A few flattened backdrops; the least recently used is dropped first. */
private class BackdropCache {
    private val entries = LinkedHashMap<BackdropKey, ImageBitmap>(RASTER_CACHE_SIZE, 0.75f, true)

    fun get(key: BackdropKey): ImageBitmap? = entries[key]

    fun put(
        key: BackdropKey,
        raster: ImageBitmap,
    ) {
        entries[key] = raster
        while (entries.size > RASTER_CACHE_SIZE) entries.remove(entries.keys.first())
    }
}

/** Flatten the static blur once (at [RASTER_SCALE]), instead of running it again through every glass consumer. */
private suspend fun renderBackdrop(
    graphics: GraphicsContext,
    density: Density,
    direction: LayoutDirection,
    source: Bitmap,
    style: BackdropStyle,
): ImageBitmap? {
    val size =
        IntSize(
            (style.viewport.width * RASTER_SCALE).toInt().coerceAtLeast(1),
            (style.viewport.height * RASTER_SCALE).toInt().coerceAtLeast(1),
        )
    val image = source.asImageBitmap()
    val blurred = graphics.createGraphicsLayer()
    val frame = graphics.createGraphicsLayer()
    return try {
        val px = with(density) { style.radius.toPx() } * RASTER_SCALE
        blurred.renderEffect = BlurEffect(px, px, TileMode.Decal)
        blurred.clip = true
        blurred.record(density, direction, size) {
            val scale = maxOf(size.width.toFloat() / image.width, size.height.toFloat() / image.height)
            val scaled = IntSize(kotlin.math.round(image.width * scale).toInt(), kotlin.math.round(image.height * scale).toInt())
            clipRect {
                drawImage(
                    image,
                    dstSize = scaled,
                    dstOffset = IntOffset((size.width - scaled.width) / 2, (size.height - scaled.height) / 2),
                    filterQuality = FilterQuality.Low,
                )
            }
        }
        frame.record(density, direction, size) {
            drawRect(style.background)
            drawLayer(blurred)
            drawRect(style.scrim)
        }
        frame.toImageBitmap()
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
    } catch (error: RuntimeException) {
        // Renderer readback is optional. Keep the original GPU blur on unsupported drivers.
        android.util.Log.w("ArtworkBackdrop", "Backdrop caching unavailable", error)
        null
    } finally {
        graphics.releaseGraphicsLayer(blurred)
        graphics.releaseGraphicsLayer(frame)
    }
}
