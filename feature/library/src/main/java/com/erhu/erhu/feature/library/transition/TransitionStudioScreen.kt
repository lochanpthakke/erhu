package com.erhu.erhu.feature.library.transition

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.erhu.erhu.core.common.R
import com.erhu.erhu.core.common.locale.AppLocaleManager
import com.erhu.erhu.core.designsystem.artwork.SongArtwork
import com.erhu.erhu.core.designsystem.component.GlassMenu
import com.erhu.erhu.core.designsystem.component.GlassMenuAnchor
import com.erhu.erhu.core.designsystem.component.GlassMenuItem
import com.erhu.erhu.core.designsystem.component.glassMenuTrigger
import com.erhu.erhu.core.designsystem.foundation.Spacing
import com.erhu.erhu.core.model.CollectionKind
import com.erhu.erhu.core.model.CollectionRef
import com.erhu.erhu.core.model.TransitionChoice
import com.erhu.erhu.core.playback.transition.specOrNull
import com.erhu.erhu.feature.library.formatTimestamp

/** The compact entry row on an album or playlist page: shows this collection's transition state, opens the Studio. */
@Composable
internal fun TransitionStudioRow(
    collection: CollectionRef,
    viewModel: TransitionStudioViewModel,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val config by viewModel.config.collectAsState()
    CollectionTransitionRow(collection, config, { viewModel.setCollectionEnabled(collection, it) }, onOpen, modifier)
}

@Composable
@Suppress("LongMethod")
internal fun TransitionStudioScreen(
    viewModel: TransitionStudioViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ui by viewModel.ui.collectAsState()
    val draft by viewModel.draft.collectAsState()
    val state = ui ?: return
    val isAlbum = state.input.collection.kind == CollectionKind.ALBUM
    BackHandler(enabled = draft == null, onBack = onClose)

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        LazyColumn(
            contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "header") {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm)) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.transition_cd_close_studio))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.menu_transition_studio), style = MaterialTheme.typography.titleLarge)
                        Text(
                            state.input.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
            item(key = "global") {
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.transition_automatic_crossfades), style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(R.string.transition_automatic_crossfades_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = state.global.masterEnabled, onCheckedChange = viewModel::setMasterEnabled)
                    }
                    SettingLine(
                        title = stringResource(R.string.transition_default_all_music),
                        value = localizedSpecSummary(state.global.default.specOrNull()),
                        action = stringResource(R.string.action_edit),
                        onAction = { viewModel.openEditor(EditorTarget.Global) },
                    )
                }
            }
            item(key = "collection") {
                CollectionTransitionPanel(viewModel = viewModel, state = state, isAlbum = isAlbum)
            }
            item(key = "timeline-title") {
                Column {
                    Text(stringResource(R.string.transition_section_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(if (isAlbum) R.string.transition_section_album_help else R.string.transition_section_playlist_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val tracks = state.input.tracks
            itemsIndexed(tracks, key = { _, track -> track.occurrence.key }) { index, track ->
                Column {
                    TimelineTrack(
                        track = track,
                        pairAfter = state.pairs.getOrNull(index),
                        onEdit = { pair -> viewModel.openEditor(EditorTarget.Pair(pair)) },
                        onReset = viewModel::resetPair,
                    )
                    state.pairs.getOrNull(index)?.let { pair ->
                        PairConnector(pair, enabled = state.global.masterEnabled) { viewModel.openEditor(EditorTarget.Pair(pair)) }
                    }
                }
            }
        }
    }
    draft?.let { TransitionEditorScreen(viewModel, it, state.input.collection.kind) }
}

@Composable
private fun Panel(content: @Composable () -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) { content() }
}

@Composable
private fun SettingLine(
    title: String,
    value: String,
    action: String,
    onAction: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun TimelineTrack(
    track: StudioTrack,
    pairAfter: StudioPair?,
    onEdit: (StudioPair) -> Unit,
    onReset: (com.erhu.erhu.core.model.TransitionPairKey) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val locale = AppLocaleManager.activeLocale(LocalContext.current)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        SongArtwork(track.song, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)))
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(track.song.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${track.song.artist} · ${formatTimestamp(track.song.durationMs, locale)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (pairAfter != null) {
            GlassMenuAnchor {
                IconButton(modifier = Modifier.glassMenuTrigger(), onClick = {
                    menu = true
                }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.transition_cd_more_for_track, track.song.title),
                    )
                }
                GlassMenu(
                    label = stringResource(R.string.transition_options_label),
                    expanded = menu,
                    onDismissRequest = { menu = false },
                ) {
                    GlassMenuItem(icon = Icons.Default.Tune, text = { Text(stringResource(R.string.transition_edit_to_next)) }, onClick = {
                        menu = false
                        onEdit(pairAfter)
                    })
                    if (pairAfter.choice != TransitionChoice.Inherit) {
                        GlassMenuItem(
                            icon = Icons.Default.RestartAlt,
                            text = { Text(stringResource(R.string.transition_reset_to_auto)) },
                            onClick = {
                                menu = false
                                onReset(pairAfter.key)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PairConnector(
    pair: StudioPair,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 20.dp)) {
        Box(
            Modifier
                .width(2.dp)
                .height(40.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
        Spacer(Modifier.width(Spacing.lg))
        AssistChip(onClick = onClick, label = { Text(localizedPairLabel(pair.choice)) }, enabled = true)
        Spacer(Modifier.width(Spacing.sm))
        Text(
            if (enabled) localizedSpecSummary(pair.resolved) else stringResource(R.string.transition_off_master_switch),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CollectionTransitionPanel(
    viewModel: TransitionStudioViewModel,
    state: StudioUi,
    isAlbum: Boolean,
) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(if (isAlbum) R.string.transition_this_album else R.string.transition_this_playlist),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = state.collectionEnabled,
                onCheckedChange = { viewModel.setCollectionEnabled(state.input.collection, it) },
            )
        }
        if (!state.collectionEnabled) {
            Text(
                stringResource(if (isAlbum) R.string.transition_disabled_album else R.string.transition_disabled_playlist),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        CollectionTransitionModeRow(
            choice = state.collectionChoice,
            onSelectChoice = viewModel::setCollectionChoice,
            onOpenEditor = { viewModel.openEditor(EditorTarget.Collection(state.input.collection)) },
        )
        Text(
            collectionTransitionSummary(state, isAlbum),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.collectionChoice is TransitionChoice.Custom) {
            Row {
                TextButton(onClick = { viewModel.openEditor(EditorTarget.Collection(state.input.collection)) }) {
                    Text(stringResource(R.string.action_edit))
                }
                TextButton(onClick = { viewModel.setCollectionChoice(TransitionChoice.Inherit) }) {
                    Text(stringResource(R.string.transition_reset_to_default))
                }
            }
        }
        if (!state.global.masterEnabled) {
            Text(
                stringResource(R.string.transition_master_off_collection_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun CollectionTransitionModeRow(
    choice: TransitionChoice,
    onSelectChoice: (TransitionChoice) -> Unit,
    onOpenEditor: () -> Unit,
) {
    val options =
        listOf(
            TransitionChoice.Inherit to stringResource(R.string.transition_default),
            TransitionChoice.Off to stringResource(R.string.transition_off),
        )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (option, label) ->
            SegmentedButton(
                selected = choice == option,
                onClick = { onSelectChoice(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size + 1),
            ) { Text(label) }
        }
        SegmentedButton(
            selected = choice is TransitionChoice.Custom,
            onClick = onOpenEditor,
            shape = SegmentedButtonDefaults.itemShape(options.size, options.size + 1),
        ) { Text(stringResource(R.string.transition_custom)) }
    }
}

@Composable
private fun collectionTransitionSummary(
    state: StudioUi,
    isAlbum: Boolean,
): String {
    val summaryLine =
        when (state.collectionChoice) {
            TransitionChoice.Inherit ->
                stringResource(
                    R.string.transition_collection_uses_default,
                    localizedSpecSummary(state.global.default.specOrNull()),
                )
            TransitionChoice.Off ->
                stringResource(if (isAlbum) R.string.transition_collection_off_album else R.string.transition_collection_off_playlist)
            is TransitionChoice.Custom ->
                stringResource(R.string.transition_collection_custom, localizedSpecSummary(state.collectionChoice.spec))
        }
    return if (state.isPlaying) stringResource(R.string.transition_playing_now, summaryLine) else summaryLine
}
