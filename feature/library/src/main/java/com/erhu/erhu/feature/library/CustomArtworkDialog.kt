package com.erhu.erhu.feature.library

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import com.erhu.erhu.core.database.repository.CustomSongArtworkRepository
import com.erhu.erhu.core.designsystem.artwork.SongArtwork
import com.erhu.erhu.core.designsystem.foundation.Radius
import com.erhu.erhu.core.designsystem.foundation.Spacing
import com.erhu.erhu.core.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.erhu.erhu.core.common.R as CommonR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomArtworkDialog(
    song: Song,
    customArtworkRepository: CustomSongArtworkRepository,
    currentCustomArtworkPath: String?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    val photoPickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia(),
        ) { uri: Uri? ->
            if (uri != null) {
                selectedUri = uri
            }
        }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = Radius.large, topEnd = Radius.large),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.screenGutter, vertical = Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(CommonR.string.custom_artwork_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(Spacing.xs))

            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(Spacing.lg))

            // Artwork Preview
            Box(
                modifier =
                    Modifier
                        .size(240.dp)
                        .clip(Radius.largeShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (selectedUri != null) {
                    val imageLoader =
                        remember(context) {
                            coil.ImageLoader
                                .Builder(context)
                                .components {
                                    if (android.os.Build.VERSION.SDK_INT >= 28) {
                                        add(ImageDecoderDecoder.Factory())
                                    } else {
                                        add(GifDecoder.Factory())
                                    }
                                }.build()
                        }
                    AsyncImage(
                        model =
                            ImageRequest
                                .Builder(context)
                                .data(selectedUri)
                                .crossfade(true)
                                .build(),
                        imageLoader = imageLoader,
                        contentDescription = stringResource(CommonR.string.custom_artwork_title),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                    )
                } else if (currentCustomArtworkPath != null) {
                    SongArtwork(
                        song = song,
                        customArtworkPath = currentCustomArtworkPath,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                    )
                } else {
                    SongArtwork(
                        song = song,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                    )
                }

                if (isSaving) {
                    Box(
                        modifier =
                            Modifier
                                .matchParentSize()
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.lg))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                OutlinedButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    modifier = Modifier.weight(1f),
                    shape = Radius.pillShape,
                    enabled = !isSaving,
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(Spacing.xs))
                    Text(stringResource(CommonR.string.custom_artwork_choose))
                }

                if (selectedUri != null) {
                    Button(
                        onClick = {
                            val uri = selectedUri ?: return@Button
                            isSaving = true
                            scope.launch {
                                try {
                                    val stream =
                                        withContext(Dispatchers.IO) {
                                            context.contentResolver.openInputStream(uri)
                                        }
                                    if (stream != null) {
                                        customArtworkRepository.setCustomArtwork(song.id, stream)
                                        Toast
                                            .makeText(
                                                context,
                                                context.getString(CommonR.string.custom_artwork_applied),
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        onDismiss()
                                    }
                                } catch (e: Exception) {
                                    Toast
                                        .makeText(
                                            context,
                                            context.getString(CommonR.string.custom_artwork_error),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                } finally {
                                    isSaving = false
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = Radius.pillShape,
                        enabled = !isSaving,
                    ) {
                        Text(stringResource(CommonR.string.custom_artwork_apply))
                    }
                }
            }

            if (currentCustomArtworkPath != null && selectedUri == null) {
                Spacer(modifier = Modifier.height(Spacing.sm))
                TextButton(
                    onClick = {
                        isSaving = true
                        scope.launch {
                            try {
                                customArtworkRepository.removeCustomArtwork(song.id)
                                Toast
                                    .makeText(
                                        context,
                                        context.getString(CommonR.string.custom_artwork_removed),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                onDismiss()
                            } finally {
                                isSaving = false
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    enabled = !isSaving,
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(Spacing.xs))
                    Text(stringResource(CommonR.string.custom_artwork_remove))
                }
            }

            Spacer(modifier = Modifier.height(Spacing.sm))
        }
    }
}
