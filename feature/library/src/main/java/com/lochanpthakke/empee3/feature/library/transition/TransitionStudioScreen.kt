package com.lochanpthakke.empee3.feature.library.transition

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lochanpthakke.empee3.core.designsystem.artwork.SongArtwork
import com.lochanpthakke.empee3.core.designsystem.component.GlassMenu
import com.lochanpthakke.empee3.core.designsystem.component.GlassMenuItem
import com.lochanpthakke.empee3.core.designsystem.foundation.IconSize
import com.lochanpthakke.empee3.core.designsystem.foundation.Spacing
import com.lochanpthakke.empee3.core.model.CollectionKind
import com.lochanpthakke.empee3.core.model.CollectionRef
import com.lochanpthakke.empee3.core.model.TransitionChoice
import com.lochanpthakke.empee3.core.playback.transition.specOrNull
import com.lochanpthakke.empee3.feature.library.formatTimestamp

/** The compact entry row on an album or playlist page: shows this collection's transition state, opens the Studio. */
@Composable
internal fun TransitionStudioRow(
    collection: CollectionRef,
    viewModel: TransitionStudioViewModel,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val config by viewModel.config.collectAsState()
    val choice = config.collection(collection)
    val summary =
        when {
            !config.global.masterEnabled -> "Automatic crossfades are off"
            choice == TransitionChoice.Off -> "Off for this ${noun(collection)}"
            choice is TransitionChoice.Custom -> specSummary(choice.spec)
            else -> "Default · ${specSummary(config.global.default.specOrNull())}"
        }
    val custom = config.pairs.keys.count { it.collection == collection }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClickLabel = "Open Transition Studio", onClick = onOpen)
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Tune,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(IconSize.small),
        )
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text("Transition Studio", style = MaterialTheme.typography.titleSmall)
            Text(
                summary + if (custom > 0) " · $custom custom" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun noun(collection: CollectionRef) = if (collection.kind == CollectionKind.ALBUM) "album" else "playlist"

/**
 * Transition Studio for one album or playlist, shown over its page: the app-wide switch and default,
 * this collection's setting, and the timeline of its tracks with the transition between each pair.
 */
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
    val noun = noun(state.input.collection)
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
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Close Transition Studio") }
                    Column(Modifier.weight(1f)) {
                        Text("Transition Studio", style = MaterialTheme.typography.titleLarge)
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
                            Text("Automatic crossfades", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Master switch for every transition in the app. Off keeps all settings but plays tracks back to back.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = state.global.masterEnabled, onCheckedChange = viewModel::setMasterEnabled)
                    }
                    SettingLine(
                        title = "Default for all music",
                        value = specSummary(state.global.default.specOrNull()),
                        action = "Edit",
                        onAction = { viewModel.openEditor(EditorTarget.Global) },
                    )
                }
            }
            item(key = "collection") {
                Panel {
                    Text("This $noun", style = MaterialTheme.typography.titleSmall)
                    val options = listOf(TransitionChoice.Inherit to "Default", TransitionChoice.Off to "Off")
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        options.forEachIndexed { index, (choice, label) ->
                            SegmentedButton(
                                selected = state.collectionChoice == choice,
                                onClick = { viewModel.setCollectionChoice(choice) },
                                shape = SegmentedButtonDefaults.itemShape(index, options.size + 1),
                            ) { Text(label) }
                        }
                        SegmentedButton(
                            selected = state.collectionChoice is TransitionChoice.Custom,
                            onClick = { viewModel.openEditor(EditorTarget.Collection(state.input.collection)) },
                            shape = SegmentedButtonDefaults.itemShape(options.size, options.size + 1),
                        ) { Text("Custom") }
                    }
                    Text(
                        when (state.collectionChoice) {
                            TransitionChoice.Inherit -> "Uses the default: ${specSummary(state.global.default.specOrNull())}."
                            TransitionChoice.Off -> "No crossfades in this $noun, whatever the default."
                            is TransitionChoice.Custom -> "Its own transition: ${specSummary(state.collectionChoice.spec)}."
                        } + if (state.isPlaying) " Playing now." else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.collectionChoice is TransitionChoice.Custom) {
                        Row {
                            TextButton(onClick = { viewModel.openEditor(EditorTarget.Collection(state.input.collection)) }) { Text("Edit") }
                            TextButton(onClick = { viewModel.setCollectionChoice(TransitionChoice.Inherit) }) { Text("Reset to default") }
                        }
                    }
                    if (!state.global.masterEnabled) {
                        Text(
                            "Automatic crossfades are off, so nothing here plays until the master switch is on.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            item(key = "timeline-title") {
                Column {
                    Text("Transitions", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Tap a transition to edit that exact pair. With shuffle, a pair's setting applies whenever those two tracks play " +
                            "back to back; Auto follows the $noun's setting.",
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
    draft?.let { TransitionEditorScreen(viewModel, it, noun) }
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
    onReset: (com.lochanpthakke.empee3.core.model.TransitionPairKey) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        SongArtwork(track.song, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)))
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(track.song.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${track.song.artist} · ${formatTimestamp(track.song.durationMs)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (pairAfter != null) {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More for ${track.song.title}") }
                GlassMenu(label = "Transition options", expanded = menu, onDismissRequest = { menu = false }) {
                    GlassMenuItem(icon = Icons.Default.Tune, text = { Text("Edit transition to next") }, onClick = {
                        menu = false
                        onEdit(pairAfter)
                    })
                    if (pairAfter.choice != TransitionChoice.Inherit) {
                        GlassMenuItem(icon = Icons.Default.RestartAlt, text = { Text("Reset transition to Auto") }, onClick = {
                            menu = false
                            onReset(pairAfter.key)
                        })
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
        AssistChip(onClick = onClick, label = { Text(pairLabel(pair.choice)) }, enabled = true)
        Spacer(Modifier.width(Spacing.sm))
        Text(
            if (enabled) specSummary(pair.resolved) else "Off (master switch)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
