@file:Suppress("detekt:All")

package com.erhu.erhu.feature.library

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.erhu.erhu.core.common.locale.AppLocaleManager
import com.erhu.erhu.core.common.locale.LocaleFormatters
import com.erhu.erhu.core.designsystem.component.GlassAlertDialog
import com.erhu.erhu.core.designsystem.foundation.Spacing
import com.erhu.erhu.core.model.Song
import com.erhu.erhu.core.network.tagger.BpmCorrection
import com.erhu.erhu.core.network.tagger.PLAUSIBLE_BPM
import com.erhu.erhu.core.network.tagger.parseBpmTag
import com.erhu.erhu.core.common.R as CommonR

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
        libraryViewModel.clearBpmMessage()
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
    val bpmMessage by libraryViewModel.bpmMessage.collectAsState()

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
                    val localized = AppLocaleManager.localizedContext(context)
                    Toast.makeText(context, localized.getString(CommonR.string.inspector_image_read_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }

    val locale = AppLocaleManager.activeLocale(context)

    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(CommonR.string.inspector_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = stringResource(CommonR.string.inspector_cd_close))
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
                        stringResource(CommonR.string.inspector_section_artwork),
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
                                    contentDescription = stringResource(CommonR.string.inspector_cd_artwork),
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
                                    stringResource(CommonR.string.inspector_no_artwork),
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
                            Text(stringResource(CommonR.string.inspector_replace_cover))
                        }
                        if (artBytes != null && artBytes.isNotEmpty()) {
                            OutlinedButton(
                                onClick = { viewModel.setSelectedArtwork(null) },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            ) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(Spacing.xs))
                                Text(stringResource(CommonR.string.action_remove))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.md))

                    // 2. BASIC METADATA SECTION
                    Text(
                        stringResource(CommonR.string.inspector_section_basic),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs))

                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text(stringResource(CommonR.string.fix_tags_field_title)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs))

                    OutlinedTextField(
                        value = artist,
                        onValueChange = { artist = it },
                        label = { Text(stringResource(CommonR.string.fix_tags_field_artist)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs))

                    OutlinedTextField(
                        value = albumArtist,
                        onValueChange = { albumArtist = it },
                        label = { Text(stringResource(CommonR.string.inspector_field_album_artist)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs))

                    OutlinedTextField(
                        value = album,
                        onValueChange = { album = it },
                        label = { Text(stringResource(CommonR.string.fix_tags_field_album)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(modifier = Modifier.height(Spacing.md))

                    // 3. RELEASE & TRACK METADATA
                    Text(
                        stringResource(CommonR.string.inspector_section_release),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        OutlinedTextField(
                            value = trackNumber,
                            onValueChange = { trackNumber = it },
                            label = { Text(stringResource(CommonR.string.inspector_field_track_number)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = discNumber,
                            onValueChange = { discNumber = it },
                            label = { Text(stringResource(CommonR.string.inspector_field_disc_number)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(modifier = Modifier.height(Spacing.xs))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        OutlinedTextField(
                            value = year,
                            onValueChange = { year = it },
                            label = { Text(stringResource(CommonR.string.fix_tags_field_year)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = genre,
                            onValueChange = { genre = it },
                            label = { Text(stringResource(CommonR.string.fix_tags_field_genre)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = bpm,
                            onValueChange = { bpm = it },
                            label = { Text(stringResource(CommonR.string.fix_tags_field_bpm)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    BpmCorrectionRow(
                        bpm = parseBpmTag(bpm),
                        onPropose = { from, to, kind -> pendingBpmCorrection = Triple(from, to, kind) },
                    )
                    bpmMessage?.let { message ->
                        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }

                    Spacer(modifier = Modifier.height(Spacing.md))

                    // 4. CREDITS & EXTRA TAGS
                    Text(
                        stringResource(CommonR.string.inspector_section_credits),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs))

                    OutlinedTextField(
                        value = composer,
                        onValueChange = { composer = it },
                        label = { Text(stringResource(CommonR.string.inspector_field_composer)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs))

                    OutlinedTextField(
                        value = comment,
                        onValueChange = { comment = it },
                        label = { Text(stringResource(CommonR.string.inspector_field_comment)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs))

                    OutlinedTextField(
                        value = lyrics,
                        onValueChange = { lyrics = it },
                        label = { Text(stringResource(CommonR.string.inspector_field_lyrics)) },
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(modifier = Modifier.height(Spacing.md))

                    // 5. TECHNICAL FILE & AUDIO STREAM PROPERTIES (READ-ONLY)
                    Text(
                        stringResource(CommonR.string.inspector_section_properties),
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
                            val unknown = stringResource(CommonR.string.common_unknown)
                            Text(
                                stringResource(CommonR.string.inspector_prop_file_path, uiState.filePath ?: unknown),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            diag?.let {
                                Text(
                                    stringResource(CommonR.string.inspector_prop_container, it.container?.value ?: unknown),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(
                                    stringResource(CommonR.string.inspector_prop_codec, it.codec?.value ?: unknown),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(
                                    stringResource(CommonR.string.inspector_prop_bitrate, it.bitrate?.value ?: unknown),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(
                                    stringResource(CommonR.string.inspector_prop_sample_rate, it.sampleRate?.value ?: unknown),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(
                                    stringResource(CommonR.string.inspector_prop_channels, it.channels?.value ?: unknown),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(
                                    stringResource(CommonR.string.inspector_prop_file_size, it.fileSize?.value ?: unknown),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(
                                    stringResource(CommonR.string.inspector_prop_duration, it.duration?.value ?: unknown),
                                    style = MaterialTheme.typography.bodySmall,
                                )
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
                        Text(stringResource(CommonR.string.inspector_force_reconstruct_button))
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
                Text(
                    stringResource(
                        if (uiState.isSaving) CommonR.string.action_saving else CommonR.string.inspector_save_verify,
                    ),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(CommonR.string.action_cancel))
            }
        },
    )

    // Enlarged Artwork Viewer Dialog
    uiState.enlargedArtworkBytes?.let { bytes ->
        val bitmap = remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
        GlassAlertDialog(
            onDismissRequest = { viewModel.setEnlargedArtwork(null) },
            title = { Text(stringResource(CommonR.string.inspector_artwork_dialog_title)) },
            text = {
                bitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = stringResource(CommonR.string.inspector_cd_enlarged_artwork),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().height(300.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.setEnlargedArtwork(null) }) {
                    Text(stringResource(CommonR.string.action_close))
                }
            },
        )
    }

    // Half / double time: the proposed value is shown, and only written once confirmed.
    pendingBpmCorrection?.let { (from, to, kind) ->
        val fromText = LocaleFormatters.formatInteger(from, locale)
        val toText = LocaleFormatters.formatInteger(to, locale)
        val kindLabel =
            stringResource(
                when (kind) {
                    BpmCorrection.HALF_TIME -> CommonR.string.inspector_bpm_half
                    BpmCorrection.DOUBLE_TIME -> CommonR.string.inspector_bpm_double
                },
            )
        GlassAlertDialog(
            onDismissRequest = { pendingBpmCorrection = null },
            title = { Text(stringResource(CommonR.string.inspector_bpm_dialog_title, toText)) },
            text = {
                Text(
                    stringResource(CommonR.string.inspector_bpm_dialog_body, kindLabel, fromText, toText),
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
                    Text(stringResource(CommonR.string.inspector_bpm_dialog_confirm, toText))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingBpmCorrection = null }) {
                    Text(stringResource(CommonR.string.action_cancel))
                }
            },
        )
    }

    // Force Repair Confirmation Dialog
    if (uiState.showForceRepairConfirm) {
        GlassAlertDialog(
            onDismissRequest = { viewModel.setShowForceRepairConfirm(false) },
            title = { Text(stringResource(CommonR.string.inspector_force_dialog_title)) },
            text = {
                Text(
                    stringResource(CommonR.string.inspector_force_dialog_body),
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
                    Text(stringResource(CommonR.string.inspector_force_dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setShowForceRepairConfirm(false) }) {
                    Text(stringResource(CommonR.string.action_cancel))
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
    val locale = AppLocaleManager.activeLocale(LocalContext.current)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(CommonR.string.inspector_bpm_octave_prompt), style = MaterialTheme.typography.bodySmall)
        BpmCorrection.entries.forEach { kind ->
            kind.applyTo(bpm)?.let { proposed ->
                val kindLabel =
                    stringResource(
                        when (kind) {
                            BpmCorrection.HALF_TIME -> CommonR.string.inspector_bpm_half
                            BpmCorrection.DOUBLE_TIME -> CommonR.string.inspector_bpm_double
                        },
                    )
                TextButton(onClick = { onPropose(bpm, proposed, kind) }) {
                    Text(
                        stringResource(
                            CommonR.string.inspector_bpm_chip,
                            kindLabel,
                            LocaleFormatters.formatInteger(proposed, locale),
                        ),
                    )
                }
            }
        }
    }
}
