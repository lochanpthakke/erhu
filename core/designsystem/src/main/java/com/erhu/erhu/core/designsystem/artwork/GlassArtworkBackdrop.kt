package com.erhu.erhu.core.designsystem.artwork

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.erhu.erhu.core.designsystem.theme.LocalGlassTokens
import com.erhu.erhu.core.model.Song

/** Tiny source bitmap: upscaling it is itself a soft blur, and it costs almost nothing. */
private const val BACKDROP_SOURCE_PX = 48
private const val DEFAULT_BLUR_RADIUS_DP = 40
private const val DEFAULT_FALLBACK_SCRIM_ALPHA = 0.85f

/**
 * Full-bleed, blurred album art behind the Now Playing screen.
 *
 * - Android 12+: a GPU blur ([Modifier.blur], RenderEffect) on one static image.
 * - Android 8–11: Modifier.blur is a no-op there, so the look comes from
 *   upscaling a 48 px thumbnail with bilinear filtering plus the scrim.
 * When artwork is unavailable, draws the theme background to keep the layer intact.
 */
@Composable
fun GlassArtworkBackdrop(
    song: Song?,
    modifier: Modifier = Modifier,
) {
    val glass = LocalGlassTokens.current
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, song?.uri) {
        value = song?.let { ArtworkLoader.load(context, it.uri, it.artworkUri, BACKDROP_SOURCE_PX) }
    }
    val source = bitmap
    val blurRadius = (glass?.artworkBlurRadiusDp ?: DEFAULT_BLUR_RADIUS_DP).dp
    val scrimColor =
        glass?.let { Color(it.artworkScrim) }
            ?: MaterialTheme.colorScheme.background.copy(alpha = DEFAULT_FALLBACK_SCRIM_ALPHA)
    if (source != null) {
        Box(modifier = modifier.fillMaxSize()) {
            Image(
                bitmap = source.asImageBitmap(),
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
    } else {
        Box(
            modifier =
                modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
        )
    }
}
