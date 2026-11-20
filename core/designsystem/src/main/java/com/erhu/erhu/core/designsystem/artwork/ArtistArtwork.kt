package com.erhu.erhu.core.designsystem.artwork

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.erhu.erhu.core.designsystem.theme.ErhuGlass
import kotlinx.coroutines.delay

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
        // The initial shows while the photo loads and if it cannot; a failed photo is asked for again
        // a few times (slow network at launch) instead of leaving an empty circle.
        ArtistMonogramCover(name = artistName, clipToCircle = clipToCircle)
        if (!artworkUri.isNullOrBlank()) {
            var attempt by remember(artworkUri) { mutableIntStateOf(0) }
            val context = LocalContext.current
            val request =
                remember(artworkUri, attempt) {
                    ImageRequest
                        .Builder(context)
                        .data(artworkUri)
                        .setParameter("attempt", attempt, memoryCacheKey = null)
                        .crossfade(true)
                        .build()
                }
            var failed by remember(artworkUri, attempt) { mutableStateOf(false) }
            AsyncImage(
                model = request,
                contentDescription = artistName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onError = { failed = true },
            )
            if (failed && attempt < ARTIST_PHOTO_RETRIES) {
                LaunchedEffect(artworkUri, attempt) {
                    delay(ARTIST_PHOTO_RETRY_MS * (attempt + 1))
                    attempt++
                }
            }
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
                .background(ErhuGlass.chromeColor(MaterialTheme.colorScheme.surfaceVariant)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val ARTIST_PHOTO_RETRIES = 3
private const val ARTIST_PHOTO_RETRY_MS = 2_000L
