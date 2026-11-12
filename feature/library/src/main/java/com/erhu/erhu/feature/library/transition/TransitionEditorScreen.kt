package com.erhu.erhu.feature.library.transition

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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.erhu.erhu.core.common.R
import com.erhu.erhu.core.common.locale.AppLocaleManager
import com.erhu.erhu.core.designsystem.component.GlassMenu
import com.erhu.erhu.core.designsystem.component.GlassMenuAnchor
import com.erhu.erhu.core.designsystem.component.GlassMenuItem
import com.erhu.erhu.core.designsystem.component.glassMenuTrigger
import com.erhu.erhu.core.designsystem.foundation.Spacing
import com.erhu.erhu.core.designsystem.theme.ErhuGlass
import com.erhu.erhu.core.designsystem.theme.GlassLevel
import com.erhu.erhu.core.designsystem.theme.glassPanel
import com.erhu.erhu.core.model.CollectionKind
import com.erhu.erhu.core.model.TransitionCurve
import com.erhu.erhu.core.model.TransitionSpec
import com.erhu.erhu.core.playback.transition.PreviewState
import com.erhu.erhu.feature.library.formatTimestamp

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
    collectionKind: CollectionKind,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = viewModel::cancelEditor)
    val waves by viewModel.pairWaveforms.collectAsState()
    val preview by viewModel.previewState.collectAsState()
    var sheet by remember { mutableStateOf<EditorSection?>(null) }
    var durationSheet by remember { mutableStateOf(false) }
    var precisionEditor by remember(draft.target) { mutableStateOf(false) }
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
            item(key = "bar") { EditorTopBar(viewModel, draft, collectionKind) }
            pair?.let {
                item(key = "out") {
                    TrackRow(it.from, stringResource(R.string.transition_role_outgoing), cueLabel(base, it, outgoing = true))
                }
            }
            item(key = "timeline") { TimelineBlock(viewModel, draft, waves, preview, effectiveMs) }
            item(key = "duration") {
                DurationBlock(draft, preview, requestedMs, effectiveMs) {
                    sheet = null
                    durationSheet = true
                }
            }
            pair?.let {
                item(key = "in") {
                    TrackRow(it.to, stringResource(R.string.transition_role_incoming), cueLabel(base, it, outgoing = false))
                }
            }
            item(key = "presets") { PresetCards(draft, viewModel::choose) }
            item(key = "status") { StatusLine(draft) }
            items(EditorSection.entries, key = { it.name }) { section ->
                SectionRow(section, sectionValue(section, shown)) {
                    durationSheet = false
                    sheet = section
                }
            }
            item(key = "more") { SectionRowUnavailable() }
            if (draft.mode == EditorMode.CUSTOM && draft.spec.curve == TransitionCurve.CUSTOM) {
                item(key = "hint") {
                    Column {
                        Text(
                            stringResource(R.string.transition_editor_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                        )
                        TextButton(
                            onClick = { precisionEditor = true },
                            enabled = effectiveMs > 0,
                            modifier = Modifier.padding(horizontal = Spacing.lg),
                        ) {
                            Text(stringResource(R.string.transition_edit_exact_point))
                        }
                    }
                }
            }
        }
    }
    sheet?.let { start -> TransitionOptionsSheet(viewModel, draft, start, onDismiss = { sheet = null }) }
    if (durationSheet) DurationSheet(draft.editBase().durationMs, viewModel::setDuration, onDismiss = { durationSheet = false })
    if (precisionEditor) {
        TransitionPointEditor(draft.spec, effectiveMs, onApply = { edit ->
            viewModel.edit { current -> TransitionPointPrecision.updated(current, edit, effectiveMs) }
            precisionEditor = false
        }, onDismiss = { precisionEditor = false })
    }
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
                stringResource(R.string.transition_shortened_for_tracks, localizedDurationLabel(effectiveMs)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
        (preview as? PreviewState.Unavailable)?.let {
            Text(stringResource(it.reasonRes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
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
    val fallback = if (ErhuGlass.isActive) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = FALLBACK_ALPHA)
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
    collectionKind: CollectionKind,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.sm, vertical = Spacing.sm),
    ) {
        IconButton(onClick = viewModel::cancelEditor) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.transition_cd_cancel_discard))
        }
        Spacer(Modifier.weight(1f))
        EditorTargetBadge(draft.target, collectionKind)
        EditorOverflowMenu(viewModel, draft)
        Button(onClick = viewModel::save, enabled = draft.isDirty, modifier = Modifier.padding(start = Spacing.xs)) {
            Text(stringResource(R.string.action_save))
        }
    }
}

@Composable
private fun EditorTargetBadge(
    target: EditorTarget,
    collectionKind: CollectionKind,
) {
    val labelRes =
        when (target) {
            EditorTarget.Global -> R.string.transition_scope_all_music
            is EditorTarget.Collection ->
                if (collectionKind == CollectionKind.ALBUM) {
                    R.string.transition_scope_whole_album
                } else {
                    R.string.transition_scope_whole_playlist
                }
            is EditorTarget.Pair -> R.string.transition_scope_this_pair
        }
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(
            stringResource(labelRes),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        )
    }
}

@Composable
private fun EditorOverflowMenu(
    viewModel: TransitionStudioViewModel,
    draft: EditorDraft,
) {
    var menu by remember { mutableStateOf(false) }
    GlassMenuAnchor {
        IconButton(modifier = Modifier.glassMenuTrigger(), onClick = {
            menu = true
        }) { Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.player_cd_more_options)) }
        GlassMenu(
            label = stringResource(R.string.transition_menu_label),
            expanded = menu,
            onDismissRequest = { menu = false },
        ) {
            GlassMenuItem(
                icon = Icons.AutoMirrored.Filled.Undo,
                text = { Text(stringResource(R.string.action_undo)) },
                enabled = draft.undo.isNotEmpty(),
                onClick = {
                    menu = false
                    viewModel.undo()
                },
            )
            if (draft.target is EditorTarget.Pair) {
                GlassMenuItem(
                    icon = Icons.Default.RestartAlt,
                    text = { Text(stringResource(R.string.transition_reset_cues)) },
                    onClick = {
                        menu = false
                        viewModel.resetCues()
                    },
                )
            }
            GlassMenuItem(
                icon = Icons.Default.RestartAlt,
                text = { Text(stringResource(R.string.transition_reset_to_preset)) },
                enabled = draft.mode == EditorMode.CUSTOM,
                onClick = {
                    menu = false
                    viewModel.resetToPreset()
                },
            )
        }
    }
}

/** Where the outgoing track hands over or the incoming one starts, when moved from the default. */
@Composable
private fun cueLabel(
    spec: TransitionSpec,
    pair: StudioPair,
    outgoing: Boolean,
): String? {
    val locale = AppLocaleManager.activeLocale(LocalContext.current)
    return if (outgoing) {
        spec.outgoingCutMs.takeIf { it > 0 }?.let {
            stringResource(R.string.transition_cue_hands_over, formatTimestamp(spec.outgoingEndMs(pair.from.song.durationMs), locale))
        }
    } else {
        spec.incomingStartMs.takeIf { it > 0 }?.let {
            stringResource(R.string.transition_cue_starts_at, formatTimestamp(it.toLong(), locale))
        }
    }
}

private val TIMELINE_HEIGHT = 300.dp
private val WINDOW_CORNER = 20.dp
private const val FALLBACK_ALPHA = 0.35f
