package com.lochanpthakke.empee3.feature.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lochanpthakke.empee3.core.database.library.ArtistArtworkCache
import com.lochanpthakke.empee3.core.designsystem.artwork.ArtistArtwork
import com.lochanpthakke.empee3.core.designsystem.component.GlassAlertDialog
import com.lochanpthakke.empee3.core.designsystem.foundation.Spacing
import com.lochanpthakke.empee3.core.designsystem.foundation.TextStyles
import com.lochanpthakke.empee3.core.model.Artist

private val ARTIST_PREVIEW_SIZE = 160.dp

/**
 * The artist's own picture. "Choose image" opens the system photo picker, then the same
 * square crop as playlist covers; the result replaces the automatic Deezer photo and is never
 * changed by scans or lookups. "Use automatic image" deletes it. Applied only on Save.
 */
@Composable
@Suppress("LongMethod")
internal fun ArtistImageDialog(
    artist: Artist,
    hasCustomImage: Boolean,
    onDismiss: () -> Unit,
    onSave: (CoverChange) -> Unit,
) {
    var change by remember(artist.id) { mutableStateOf<CoverChange>(CoverChange.None) }
    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) pickedUri = uri
        }

    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Artist image") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                val previewModifier = Modifier.size(ARTIST_PREVIEW_SIZE)
                when (val pending = change) {
                    is CoverChange.Replace ->
                        Image(
                            bitmap = pending.preview,
                            contentDescription = "New artist image",
                            contentScale = ContentScale.Crop,
                            modifier = previewModifier.clip(CircleShape),
                        )
                    // Until the automatic lookup runs again, the artist shows the placeholder.
                    CoverChange.Remove -> ArtistArtwork(artworkUri = null, artistName = artist.name, modifier = previewModifier)
                    CoverChange.None ->
                        ArtistArtwork(
                            artworkUri = artist.artworkUri?.takeIf(ArtistArtworkCache::hasArtwork),
                            artistName = artist.name,
                            modifier = previewModifier,
                        )
                }
                Spacer(modifier = Modifier.height(Spacing.md))
                Text(
                    text =
                        if (hasCustomImage && change == CoverChange.None) {
                            "You chose this image. It is never replaced automatically."
                        } else {
                            "Your own image replaces the automatic one and is never changed by scans."
                        },
                    style = TextStyles.metadata,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(Spacing.md))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Text("Choose image")
                    }
                    if (hasCustomImage && change == CoverChange.None) {
                        TextButton(onClick = { change = CoverChange.Remove }) { Text("Use automatic image") }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(change) }, enabled = change != CoverChange.None) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    pickedUri?.let { uri ->
        CircularCropDialog(
            imageUri = uri,
            onDismiss = { pickedUri = null },
            onCropped = { bytes, preview ->
                change = CoverChange.Replace(bytes, preview)
                pickedUri = null
            },
        )
    }
}
