package com.erhu.erhu.core.designsystem.artwork

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity

/** Placeholder icon takes this fraction of the artwork width. */
private const val PLACEHOLDER_ICON_FRACTION = 3

/**
 * Album cover from the album's MediaStore artwork uri, loaded asynchronously
 * through the shared [ArtworkLoader] cache. Shows a neutral album placeholder
 * while loading or when there is no artwork. Size comes from [modifier].
 */
@Composable
fun AlbumArtwork(
    artworkUri: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    placeholderIcon: ImageVector = Icons.Default.Album,
) {
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val context = LocalContext.current
        val iconSize = maxWidth / PLACEHOLDER_ICON_FRACTION
        val sizePx = with(LocalDensity.current) { maxWidth.roundToPx() }.coerceAtLeast(1)
        val bitmap by produceState(initialValue = artworkUri?.let(ArtworkLoader::peek), artworkUri, sizePx) {
            value = artworkUri?.let { ArtworkLoader.load(context, it, it, sizePx) }
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
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = placeholderIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(iconSize),
                )
            }
        }
    }
}
