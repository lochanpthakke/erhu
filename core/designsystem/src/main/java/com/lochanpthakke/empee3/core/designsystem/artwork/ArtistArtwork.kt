package com.lochanpthakke.empee3.core.designsystem.artwork

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.lochanpthakke.empee3.core.designsystem.theme.EmPee3Glass

/**
 * Official artist profile artwork loaded asynchronously from a URL (e.g. Deezer/Genius).
 * Rendered inside a circular container by default, or full-bleed when [clipToCircle] is false.
 */
@Composable
fun ArtistArtwork(
    artworkUri: String?,
    artistName: String,
    modifier: Modifier = Modifier,
    clipToCircle: Boolean = true,
) {
    Box(
        modifier =
            if (clipToCircle) modifier.clip(CircleShape) else modifier,
        contentAlignment = Alignment.Center,
    ) {
        if (!artworkUri.isNullOrBlank()) {
            AsyncImage(
                model = artworkUri,
                contentDescription = artistName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            ArtistMonogramCover(name = artistName, clipToCircle = clipToCircle)
        }
    }
}

/** Fallback cover for an artist without an official picture: initial letter. */
@Composable
fun ArtistMonogramCover(
    name: String,
    modifier: Modifier = Modifier,
    clipToCircle: Boolean = true,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .then(if (clipToCircle) Modifier.clip(CircleShape) else Modifier)
                .background(EmPee3Glass.chromeColor(MaterialTheme.colorScheme.surfaceVariant)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
