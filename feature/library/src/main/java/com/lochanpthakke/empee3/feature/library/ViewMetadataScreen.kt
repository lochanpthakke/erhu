@file:Suppress("detekt:All")

package com.lochanpthakke.empee3.feature.library

import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lochanpthakke.empee3.core.designsystem.component.GlassAlertDialog
import com.lochanpthakke.empee3.core.designsystem.foundation.Spacing
import com.lochanpthakke.empee3.core.model.Song
import com.lochanpthakke.empee3.core.network.tagger.BpmCorrection
import com.lochanpthakke.empee3.core.network.tagger.PLAUSIBLE_BPM
import com.lochanpthakke.empee3.core.network.tagger.parseBpmTag

@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod", "MagicNumber")
fun ViewMetadataScreen(
    song: Song,
    viewModel: ViewMetadataViewModel,
    libraryViewModel: LibraryViewModel =
        androidx.hilt.navigation.compose
            .hiltViewModel(),
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(song.id) {
        viewModel.inspect(song)
    }

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.consumeMessage()
        }
    }

    var title by remember(uiState.tagData) { mutableStateOf(uiState.tagData?.title ?: song.title) }
    var artist by remember(uiState.tagData) { mutableStateOf(uiState.tagData?.artist ?: song.artist) }
    var albumArtist by remember(uiState.tagData) { mutableStateOf(uiState.tagData?.albumArtist ?: "") }
    var album by remember(uiState.tagData) { mutableStateOf(uiState.tagData?.album ?: song.album) }
    var year by remember(uiState.tagData) { mutableStateOf(uiState.tagData?.year ?: "") }
    var genre by remember(uiState.tagData) { mutableStateOf(uiState.tagData?.genre ?: "") }
    var trackNumber by remember(uiState.tagData) { mutableStateOf(uiState.tagData?.trackNumber ?: "") }
    var discNumber by remember(uiState.tagData) { mutableStateOf(uiState.tagData?.discNumber ?: "") }
    var composer by remember(uiState.tagData) { mutableStateOf(uiState.tagData?.composer ?: "") }
    var comment by remember(uiState.tagData) { mutableStateOf(uiState.tagData?.comment ?: "") }
    var lyrics by remember(uiState.tagData) { mutableStateOf(uiState.tagData?.lyrics ?: "") }
    var bpm by remember(uiState.tagData) { mutableStateOf(uiState.tagData?.bpm ?: "") }
    // A half / double time correction waiting for the user's confirmation: (from, to, kind).
    var pendingBpmCorrection by remember { mutableStateOf<Triple<Int, Int, BpmCorrection>?>(null) }
    val libraryMessage by libraryViewModel.userMessage.collectAsState()

    val imagePickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent(),
        ) { uri: Uri? ->
            uri?.let {
                try {
                    val inputStream = context.contentResolver.openInputStream(it)
                    val bytes = inputStream?.readBytes()
                    viewModel.setSelectedArtwork(bytes)
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to read image", Toast.LENGTH_SHORT).show()
                }
            }
        }

    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Embedded Metadata Inspector", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close Inspector")
                }
            }
        },
        text = {
            if (uiState.isLoading) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                ) {
                    // 1. EMBEDDED ARTWORK SECTION
                    Text(
                        "Embedded Album Cover",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs))

                    val artBytes = uiState.selectedArtworkBytes
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (artBytes != null && artBytes.isNotEmpty()) {
                            val bitmap =
                                remember(artBytes) {
                                    BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size)
                                }
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Embedded Cover Art",
                                    contentScale = ContentScale.Fit,
                                    modifier =
                                        Modifier
                                            .fillMaxSize()
                                            .clickable { viewModel.setEnlargedArtwork(artBytes) },
                                )
                            } else {
                                Icon(imageVector = Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(48.dp))
                            }
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(modifier = Modifier.height(Spacing.xs))
                                Text(
                                    "No embedded artwork in file",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        OutlinedButton(
                            onClick = { imagePickerLauncher.launch("image/*") },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Replace Cover")
                        }
                        if (artBytes != null && artBytes.isNotEmpty()) {
                            OutlinedButton(
                                onClick = { viewModel.setSelectedArtwork(null) },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            ) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(Spacing.xs))
                                Text("Remove")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.md))

                    // 2. BASIC METADATA SECTION
                    Text(
                        "Basic Information",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs))

                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs))

                    OutlinedTextField(
                        value = artist,
                        onValueChange = { artist = it },
                        label = { Text("Artist") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs))

                    OutlinedTextField(
                        value = albumArtist,
                        onValueChange = { albumArtist = it },
                        label = { Text("Album Artist") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs))

                    OutlinedTextField(
                        value = album,
                        onValueChange = { album = it },
                        label = { Text("Album") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(modifier = Modifier.height(Spacing.md))

                    // 3. RELEASE & TRACK METADATA
                    Text(
                        "Release & Track Info",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        OutlinedTextField(
                            value = trackNumber,
                            onValueChange = { trackNumber = it },
                            label = { Text("Track #") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = discNumber,
                            onValueChange = { discNumber = it },
                            label = { Text("Disc #") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(modifier = Modifier.height(Spacing.xs))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        OutlinedTextField(
                            value = year,
                            onValueChange = { year = it },
                            label = { Text("Year") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = genre,
                            onValueChange = { genre = it },
                            label = { Text("Genre") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = bpm,
                            onValueChange = { bpm = it },
                            label = { Text("BPM") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    BpmCorrectionRow(
                        bpm = parseBpmTag(bpm),
                        onPropose = { from, to, kind -> pendingBpmCorrection = Triple(from, to, kind) },
                    )
                    libraryMessage?.takeIf { it.startsWith("BPM") }?.let { message ->
                        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }

                    Spacer(modifier = Modifier.height(Spacing.md))

                    // 4. CREDITS & EXTRA TAGS
                    Text(
                        "Credits & Lyrics",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs))

                    OutlinedTextField(
                        value = composer,
                        onValueChange = { composer = it },
                        label = { Text("Composer") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs))

                    OutlinedTextField(
                        value = comment,
                        onValueChange = { comment = it },
                        label = { Text("Comment") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs))

                    OutlinedTextField(
                        value = lyrics,
                        onValueChange = { lyrics = it },
                        label = { Text("Lyrics") },
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(modifier = Modifier.height(Spacing.md))

                    // 5. TECHNICAL FILE & AUDIO STREAM PROPERTIES (READ-ONLY)
                    Text(
                        "File & Audio Stream Properties (Read-Only)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Column(modifier = Modifier.padding(Spacing.sm)) {
                            val diag = uiState.sourceDiagnostics
                            Text("File Path: ${uiState.filePath ?: "Unknown"}", style = MaterialTheme.typography.bodySmall)
                            diag?.let {
                                Text("Container: ${it.container?.value ?: "Unknown"}", style = MaterialTheme.typography.bodySmall)
                                Text("Codec: ${it.codec?.value ?: "Unknown"}", style = MaterialTheme.typography.bodySmall)
                                Text("Bitrate: ${it.bitrate?.value ?: "Unknown"}", style = MaterialTheme.typography.bodySmall)
                                Text("Sample Rate: ${it.sampleRate?.value ?: "Unknown"}", style = MaterialTheme.typography.bodySmall)
                                Text("Channels: ${it.channels?.value ?: "Unknown"}", style = MaterialTheme.typography.bodySmall)
                                Text("File Size: ${it.fileSize?.value ?: "Unknown"}", style = MaterialTheme.typography.bodySmall)
                                Text("Duration: ${it.duration?.value ?: "Unknown"}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.md))

                    // 6. FORCE REPAIR SECTION
                    OutlinedButton(
                        onClick = { viewModel.setShowForceRepairConfirm(true) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Icon(imageVector = Icons.Default.Build, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text("Force Reconstruct Tag Header")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    libraryViewModel.writeTags(
                        song = song,
                        title = title,
                        artist = artist,
                        album = album,
                        year = year.toIntOrNull() ?: 0,
                        genre = genre,
                        bpm = bpm.toIntOrNull() ?: 0,
                    )
                    onDismiss()
                },
                enabled = !uiState.isSaving && !uiState.isLoading,
            ) {
                Text(if (uiState.isSaving) "Saving..." else "Save & Verify Tags")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )

    // Enlarged Artwork Viewer Dialog
    uiState.enlargedArtworkBytes?.let { bytes ->
        val bitmap = remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
        GlassAlertDialog(
            onDismissRequest = { viewModel.setEnlargedArtwork(null) },
            title = { Text("Embedded Cover Art") },
            text = {
                bitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "Enlarged Cover Art",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().height(300.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.setEnlargedArtwork(null) }) {
                    Text("Close")
                }
            },
        )
    }

    // Half / double time: the proposed value is shown, and only written once confirmed.
    pendingBpmCorrection?.let { (from, to, kind) ->
        GlassAlertDialog(
            onDismissRequest = { pendingBpmCorrection = null },
            title = { Text("Change BPM to $to?") },
            text = {
                Text(
                    "${kind.label}: $from → $to BPM. Only the BPM tag of this file is written; the file is read back " +
                        "to check it, then the library is updated. Other tags are not touched.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        libraryViewModel.correctBpm(song, oldBpm = from, newBpm = to)
                        bpm = to.toString()
                        pendingBpmCorrection = null
                    },
                ) {
                    Text("Save $to BPM")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingBpmCorrection = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    // Force Repair Confirmation Dialog
    if (uiState.showForceRepairConfirm) {
        GlassAlertDialog(
            onDismissRequest = { viewModel.setShowForceRepairConfirm(false) },
            title = { Text("Force Tag Reconstruction?") },
            text = {
                Text(
                    "This will clear malformed ID3 header wrappers and rebuild clean tag frames from scratch using your edited fields. Audio content will NOT be re-encoded.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val fields =
                            EditableInspectorFields(
                                title = title,
                                artist = artist,
                                albumArtist = albumArtist,
                                album = album,
                                year = year,
                                genre = genre,
                                trackNumber = trackNumber,
                                discNumber = discNumber,
                                composer = composer,
                                comment = comment,
                                lyrics = lyrics,
                                bpm = bpm,
                            )
                        viewModel.executeForceRepair(fields, onSuccess = onDismiss)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Force Reconstruct Tags")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setShowForceRepairConfirm(false) }) {
                    Text("Cancel")
                }
            },
        )
    }
}

/** Half / double time buttons for a BPM read at the wrong octave; each shows the value it proposes. */
@Composable
private fun BpmCorrectionRow(
    bpm: Int?,
    onPropose: (from: Int, to: Int, kind: BpmCorrection) -> Unit,
) {
    if (bpm == null || bpm !in PLAUSIBLE_BPM) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Tempo off by an octave?", style = MaterialTheme.typography.bodySmall)
        BpmCorrection.entries.forEach { kind ->
            kind.applyTo(bpm)?.let { proposed ->
                TextButton(onClick = { onPropose(bpm, proposed, kind) }) {
                    Text("${kind.label}: $proposed")
                }
            }
        }
    }
}
