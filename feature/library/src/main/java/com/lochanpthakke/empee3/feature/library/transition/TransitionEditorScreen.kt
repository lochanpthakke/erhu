package com.lochanpthakke.empee3.feature.library.transition

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lochanpthakke.empee3.core.designsystem.component.GlassMenu
import com.lochanpthakke.empee3.core.designsystem.component.GlassMenuAnchor
import com.lochanpthakke.empee3.core.designsystem.component.GlassMenuItem
import com.lochanpthakke.empee3.core.designsystem.component.glassMenuTrigger
import com.lochanpthakke.empee3.core.designsystem.foundation.Spacing
import com.lochanpthakke.empee3.core.designsystem.theme.EmPee3Glass
import com.lochanpthakke.empee3.core.designsystem.theme.GlassLevel
import com.lochanpthakke.empee3.core.designsystem.theme.glassPanel
import com.lochanpthakke.empee3.core.model.TransitionCurve
import com.lochanpthakke.empee3.core.model.TransitionSpec
import com.lochanpthakke.empee3.core.playback.transition.PreviewState
import com.lochanpthakke.empee3.feature.library.formatTimestamp

/**
 * The transition editor for a pair, a collection's default or the global default, laid out like a
 * DJ transition editor: the two tracks around a shared timeline, presets, and one row per kind of
 * automation that opens its options. Edits stay in the view-model until Save; Cancel discards them.
 */
@Composable
@Suppress("LongMethod")
internal fun TransitionEditorScreen(
    viewModel: TransitionStudioViewModel,
    draft: EditorDraft,
    collectionNoun: String,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = viewModel::cancelEditor)
    val waves by viewModel.pairWaveforms.collectAsState()
    val preview by viewModel.previewState.collectAsState()
    var sheet by remember { mutableStateOf<EditorSection?>(null) }
    var durationSheet by remember { mutableStateOf(false) }
    val pair = (draft.target as? EditorTarget.Pair)?.pair
    val shown = draft.shown
    val base = shown ?: draft.editBase()
    val requestedMs = base.durationMs
    val effectiveMs = pair?.let { base.effectiveDurationMs(it.from.song.durationMs, it.to.song.durationMs) } ?: requestedMs

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        LazyColumn(contentPadding = PaddingValues(bottom = Spacing.xl), modifier = Modifier.fillMaxSize()) {
            item(key = "bar") { EditorTopBar(viewModel, draft, collectionNoun) }
            pair?.let { item(key = "out") { TrackRow(it.from, "Outgoing", cueLabel(base, it, outgoing = true)) } }
            item(key = "timeline") { TimelineBlock(viewModel, draft, waves, preview, effectiveMs) }
            item(key = "duration") { DurationBlock(draft, preview, requestedMs, effectiveMs) { sheet = null; durationSheet = true } }
            pair?.let { item(key = "in") { TrackRow(it.to, "Incoming", cueLabel(base, it, outgoing = false)) } }
            item(key = "presets") { PresetCards(draft, viewModel::choose) }
            item(key = "status") { StatusLine(draft) }
            items(EditorSection.entries, key = { it.name }) { section ->
                SectionRow(section, sectionValue(section, shown)) { durationSheet = false; sheet = section }
            }
            item(key = "more") { SectionRowUnavailable() }
            if (draft.mode == EditorMode.CUSTOM && draft.spec.curve == TransitionCurve.CUSTOM) {
                item(key = "hint") {
                    Text(
                        "Drag the blue points · tap a lane to add one · hold a point to remove it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                    )
                }
            }
        }
    }
    sheet?.let { start -> TransitionOptionsSheet(viewModel, draft, start, onDismiss = { sheet = null }) }
    if (durationSheet) DurationSheet(draft.editBase().durationMs, viewModel::setDuration, onDismiss = { durationSheet = false })
}

/** The timeline with the preview button between its lanes. */
@Composable
private fun TimelineBlock(
    viewModel: TransitionStudioViewModel,
    draft: EditorDraft,
    waves: PairWaveforms,
    preview: PreviewState,
    effectiveMs: Int,
) {
    val pair = (draft.target as? EditorTarget.Pair)?.pair
    val shown = draft.shown
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm)) {
        if (shown != null) TransitionWindowGlass(effectiveMs.coerceAtLeast(1))
        TransitionTimelineCanvas(
            spec = shown,
            durationMs = effectiveMs.coerceAtLeast(1),
            outgoingWave =
                pair?.let {
                    laneWave(
                        waves.outgoing,
                        (shown ?: draft.editBase()).outgoingEndMs(it.from.song.durationMs) - effectiveMs,
                    )
                },
            incomingWave = pair?.let { laneWave(waves.incoming, (shown ?: draft.editBase()).incomingStartMs.toLong()) },
            playheadMs = (preview as? PreviewState.Playing)?.positionMs,
            editablePoints = draft.mode == EditorMode.CUSTOM,
            lanesDraggable = pair != null,
            handlers =
                TimelineHandlers(
                    onDragStart = viewModel::beginDrag,
                    onMove = viewModel::movePoint,
                    onAdd = viewModel::addPoint,
                    onRemove = viewModel::removePoint,
                    onLaneDrag = viewModel::shiftCue,
                    onDragEnd = viewModel::refreshWaveforms,
                ),
            modifier = Modifier.fillMaxWidth().height(TIMELINE_HEIGHT),
        )
        if (pair != null && shown != null) {
            PreviewButton(preview, viewModel::togglePreview, Modifier.align(Alignment.Center))
        }
    }
}

/** The duration pill, and why the duration may be shorter than chosen, or why a preview cannot play. */
@Composable
private fun DurationBlock(
    draft: EditorDraft,
    preview: PreviewState,
    requestedMs: Int,
    effectiveMs: Int,
    onOpen: () -> Unit,
) {
    val shown = draft.shown
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        DurationPill(requestedMs, enabled = shown != null, onClick = onOpen)
        if (draft.target is EditorTarget.Pair && shown != null && effectiveMs < requestedMs) {
            Text(
                "Shortened to ${TransitionSpec.durationLabel(effectiveMs)} for these tracks",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
        (preview as? PreviewState.Unavailable)?.let {
            Text(it.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

/**
 * The transition window as a Liquid Glass panel behind the waveforms (rim, edge light and tint,
 * without blurring the waveforms drawn over it); a soft tint when the Glass Effect is off.
 */
@Composable
private fun TransitionWindowGlass(durationMs: Int) {
    val context = contextFor(durationMs)
    val span = (durationMs + 2f * context).coerceAtLeast(1f)
    val shape = RoundedCornerShape(WINDOW_CORNER)
    val fallback = if (EmPee3Glass.isActive) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = FALLBACK_ALPHA)
    BoxWithConstraints(Modifier.fillMaxWidth().height(TIMELINE_HEIGHT)) {
        Box(
            Modifier
                .offset(x = maxWidth * (context / span))
                .width(maxWidth * (durationMs / span))
                .fillMaxHeight()
                .glassPanel(shape, level = GlassLevel.Medium, elevated = false, backdrop = null)
                .background(fallback, shape),
        )
    }
}

/** Places a loaded waveform window on the transition axis: [transitionStartMs] is the track time where the transition starts. */
private fun laneWave(
    window: LaneWindow,
    transitionStartMs: Long,
): LaneWave {
    val binMs = if (window.lengthMs > 0) window.lengthMs.toFloat() / TransitionStudioViewModel.WAVEFORM_BINS else 0f
    return LaneWave(window.load, (window.startMs - transitionStartMs).toFloat(), binMs)
}

@Composable
private fun EditorTopBar(
    viewModel: TransitionStudioViewModel,
    draft: EditorDraft,
    noun: String,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.sm, vertical = Spacing.sm),
    ) {
        IconButton(onClick = viewModel::cancelEditor) { Icon(Icons.Default.Close, contentDescription = "Cancel and discard changes") }
        Spacer(Modifier.weight(1f))
        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
            Text(
                when (draft.target) {
                    EditorTarget.Global -> "All music"
                    is EditorTarget.Collection -> "Whole $noun"
                    is EditorTarget.Pair -> "This transition"
                },
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            )
        }
        GlassMenuAnchor {
            IconButton(modifier = Modifier.glassMenuTrigger(), onClick = {
                menu = true
            }) { Icon(Icons.Default.MoreVert, contentDescription = "More options") }
            GlassMenu(label = "Transition editor options", expanded = menu, onDismissRequest = { menu = false }) {
                GlassMenuItem(icon = Icons.AutoMirrored.Filled.Undo, text = { Text("Undo") }, enabled = draft.undo.isNotEmpty(), onClick = {
                    menu = false
                    viewModel.undo()
                })
                if (draft.target is EditorTarget.Pair) {
                    GlassMenuItem(icon = Icons.Default.RestartAlt, text = { Text("Reset cue points") }, onClick = {
                        menu = false
                        viewModel.resetCues()
                    })
                }
                GlassMenuItem(icon = Icons.Default.RestartAlt, text = {
                    Text(
                        "Reset to preset",
                    )
                }, enabled = draft.mode == EditorMode.CUSTOM, onClick = {
                    menu = false
                    viewModel.resetToPreset()
                })
            }
        }
        Button(onClick = viewModel::save, enabled = draft.isDirty, modifier = Modifier.padding(start = Spacing.xs)) { Text("Save") }
    }
}

/** Where the outgoing track hands over or the incoming one starts, when moved from the default. */
private fun cueLabel(
    spec: TransitionSpec,
    pair: StudioPair,
    outgoing: Boolean,
): String? =
    if (outgoing) {
        spec.outgoingCutMs.takeIf { it > 0 }?.let { "Hands over at ${formatTimestamp(spec.outgoingEndMs(pair.from.song.durationMs))}" }
    } else {
        spec.incomingStartMs.takeIf { it > 0 }?.let { "Starts at ${formatTimestamp(it.toLong())}" }
    }

private val TIMELINE_HEIGHT = 300.dp
private val WINDOW_CORNER = 20.dp
private const val FALLBACK_ALPHA = 0.35f
