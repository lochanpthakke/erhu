package com.erhu.erhu.core.designsystem.artwork

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import com.erhu.erhu.core.model.Song
import java.io.File

/**
 * Album art for [song], loaded asynchronously; shows a music-note placeholder
 * while loading or when the file has no artwork. Size comes from [modifier].
 * Supports custom artwork overrides, including animated GIF and WebP.
 */
@Composable
fun SongArtwork(
    song: Song?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    customArtworkPath: String? = null,
    isAnimated: Boolean = false,
) {
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val context = LocalContext.current
        val iconSize = maxWidth / 2
        val sizePx = with(LocalDensity.current) { maxWidth.roundToPx() }.coerceAtLeast(1)

        if (isAnimated && !customArtworkPath.isNullOrBlank()) {
            AsyncImage(
                model =
                    ImageRequest.Builder(context)
                        .data(File(customArtworkPath))
                        .crossfade(true)
                        .decoderFactory(
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                ImageDecoderDecoder.Factory()
                            } else {
                                GifDecoder.Factory()
                            },
                        )
                        .build(),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            val cacheKey = customArtworkPath ?: song?.uri
            val bitmap by produceState(
                initialValue = cacheKey?.let { ArtworkLoader.peekForDisplay(it) },
                key1 = cacheKey,
                key2 = sizePx,
            ) {
                value = song?.let { ArtworkLoader.loadForDisplay(context, it.uri, it.artworkUri, sizePx, customArtworkPath) }
            }
            val loaded = bitmap
            if (loaded != null) {
                Image(
                    bitmap = loaded.asImageBitmap(),
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(iconSize),
                    )
                }
            }
        }
    }
}
