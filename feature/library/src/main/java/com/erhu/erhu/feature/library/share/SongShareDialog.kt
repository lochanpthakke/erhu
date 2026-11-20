package com.erhu.erhu.feature.library.share

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.erhu.erhu.core.common.artwork.ArtworkLoader
import com.erhu.erhu.core.designsystem.foundation.Radius
import com.erhu.erhu.core.designsystem.foundation.Spacing
import com.erhu.erhu.core.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.erhu.erhu.core.common.R as CommonR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongShareDialog(
    song: Song,
    onDismiss: () -> Unit,
    customArtworkPath: String? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var aspectRatio by remember { mutableStateOf(CardAspectRatio.STORY) }
    var style by remember { mutableStateOf(SongCardStyle.CENTERED) }
    var blur by remember { mutableStateOf(BlurIntensity.MEDIUM) }
    var darkness by remember { mutableFloatStateOf(0.45f) }

    var isExporting by remember { mutableStateOf(false) }

    // Load artwork bitmap for rendering
    val artworkBitmap by produceState<Bitmap?>(initialValue = null, song.id, customArtworkPath) {
        value =
            withContext(Dispatchers.IO) {
                ArtworkLoader.load(context, song.uri, song.artworkUri, 1024, customArtworkPath)
            }
    }

    // Generate preview
    val previewBitmap by produceState<Bitmap?>(
        initialValue = null,
        artworkBitmap,
        aspectRatio,
        style,
        blur,
        darkness,
    ) {
        val config =
            SongShareConfig(
                aspectRatio = aspectRatio,
                style = style,
                blur = blur,
                darkness = darkness,
            )
        value =
            withContext(Dispatchers.Default) {
                ShareCardRenderer.renderSongCard(context, song, artworkBitmap, config)
            }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.screenGutter, vertical = Spacing.md)
                    .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(CommonR.string.share_card_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = null)
                }
            }

            Spacer(modifier = Modifier.height(Spacing.sm))

            // Card Preview
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 280.dp, max = 380.dp)
                        .clip(Radius.largeShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
            ) {
                val preview = previewBitmap
                if (preview != null) {
                    Image(
                        bitmap = preview.asImageBitmap(),
                        contentDescription = null,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(8.dp)
                                .aspectRatio(aspectRatio.ratio, matchHeightConstraintsFirst = true)
                                .clip(RoundedCornerShape(16.dp)),
                    )
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(36.dp))
                }
            }

            Spacer(modifier = Modifier.height(Spacing.md))

            // Aspect Ratio Selector
            Text(
                text = "Format",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Start),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                FilterChip(
                    selected = aspectRatio == CardAspectRatio.STORY,
                    onClick = { aspectRatio = CardAspectRatio.STORY },
                    label = { Text(stringResource(CommonR.string.share_card_aspect_story)) },
                )
                FilterChip(
                    selected = aspectRatio == CardAspectRatio.SQUARE,
                    onClick = { aspectRatio = CardAspectRatio.SQUARE },
                    label = { Text(stringResource(CommonR.string.share_card_aspect_square)) },
                )
                FilterChip(
                    selected = aspectRatio == CardAspectRatio.FEED,
                    onClick = { aspectRatio = CardAspectRatio.FEED },
                    label = { Text(stringResource(CommonR.string.share_card_aspect_feed)) },
                )
            }

            Spacer(modifier = Modifier.height(Spacing.sm))

            // Style Selector
            Text(
                text = "Style",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Start),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                FilterChip(
                    selected = style == SongCardStyle.CENTERED,
                    onClick = { style = SongCardStyle.CENTERED },
                    label = { Text(stringResource(CommonR.string.share_card_style_centered)) },
                )
                FilterChip(
                    selected = style == SongCardStyle.POSTER,
                    onClick = { style = SongCardStyle.POSTER },
                    label = { Text(stringResource(CommonR.string.share_card_style_poster)) },
                )
                FilterChip(
                    selected = style == SongCardStyle.MINIMAL,
                    onClick = { style = SongCardStyle.MINIMAL },
                    label = { Text(stringResource(CommonR.string.share_card_style_minimal)) },
                )
            }

            Spacer(modifier = Modifier.height(Spacing.sm))

            // Blur & Darken Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(CommonR.string.share_card_brightness),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${(darkness * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Slider(
                value = darkness,
                onValueChange = { darkness = it },
                valueRange = 0.15f..0.85f,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(Spacing.md))

            // Action Buttons: Save & Share
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                OutlinedButton(
                    onClick = {
                        val current = previewBitmap ?: return@OutlinedButton
                        scope.launch {
                            isExporting = true
                            val success = ShareCardExporter.saveToGallery(context, current, song.title)
                            isExporting = false
                            val msg =
                                if (success) {
                                    context.getString(CommonR.string.share_card_saved)
                                } else {
                                    context.getString(CommonR.string.share_card_save_failed)
                                }
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = previewBitmap != null && !isExporting,
                ) {
                    Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(CommonR.string.share_card_save))
                }

                Button(
                    onClick = {
                        val current = previewBitmap ?: return@Button
                        scope.launch {
                            isExporting = true
                            val uri = ShareCardExporter.saveToCache(context, current, "song_card")
                            isExporting = false
                            ShareCardExporter.shareImage(context, uri, "Share ${song.title}")
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = previewBitmap != null && !isExporting,
                ) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(CommonR.string.share_card_share))
                }
            }

            Spacer(modifier = Modifier.height(Spacing.lg))
        }
    }
}
