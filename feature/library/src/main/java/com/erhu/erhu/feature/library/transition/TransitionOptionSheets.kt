package com.erhu.erhu.feature.library.transition

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.erhu.erhu.core.designsystem.component.GlassFloatingPanel
import com.erhu.erhu.core.designsystem.foundation.Spacing
import com.erhu.erhu.core.model.EqMove
import com.erhu.erhu.core.model.TransitionCurve
import com.erhu.erhu.core.model.TransitionFilter
import com.erhu.erhu.core.model.TransitionSpec
import com.erhu.erhu.core.model.VolumeShape
import kotlin.math.roundToInt

/** Each section owns one floating panel, without a pager competing with horizontal sliders. */
@Composable
internal fun TransitionOptionsSheet(
    viewModel: TransitionStudioViewModel,
    draft: EditorDraft,
    start: EditorSection,
    onDismiss: () -> Unit,
) {
    GlassFloatingPanel(onDismissRequest = onDismiss) {
        when (start) {
            EditorSection.VOLUME -> VolumePage(draft.shown, viewModel, onDismiss)
            EditorSection.EQ -> EqPage(draft.shown, viewModel)
            EditorSection.FILTER -> FilterPage(draft.shown, viewModel)
        }
        TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Close") }
    }
}

@Composable
private fun VolumePage(
    spec: TransitionSpec?,
    viewModel: TransitionStudioViewModel,
    onDismiss: () -> Unit,
) {
    val current = spec?.let { VolumeShape.of(it) }
    SheetTitle(EditorSection.VOLUME) {
        TextButton(onClick = {
            viewModel.customizeVolume()
            onDismiss()
        }) { Text("Customize") }
    }
    HorizontalDivider()
    VolumeShape.entries.forEach { shape ->
        OptionRow(shape.label, selected = shape == current) { viewModel.setVolumeShape(shape) }
    }
    if (spec != null && current == null && spec.curve == TransitionCurve.CUSTOM) OptionRow("Custom curve", selected = true) {}
}

@Composable
private fun EqPage(
    spec: TransitionSpec?,
    viewModel: TransitionStudioViewModel,
) {
    val current = spec?.let { EqMove.of(it) }
    var customizing by remember { mutableStateOf(spec?.bassSwap == true && current == null) }
    SheetTitle(EditorSection.EQ) { TextButton(onClick = { customizing = !customizing }) { Text(if (customizing) "Done" else "Customize") } }
    HorizontalDivider()
    EqMove.entries.forEach { move ->
        OptionRow(move.label, selected = move == current) { viewModel.setEqMove(move) }
    }
    if (customizing) {
        var value by remember(spec?.bassSwapAt) { mutableFloatStateOf(spec?.bassSwapAt ?: TransitionSpec.DEFAULT_BASS_SWAP_AT) }
        Text(
            "Bass swap at ${percent(value)} % of the transition",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = Spacing.md),
        )
        Slider(
            value = value,
            onValueChange = { value = it },
            onValueChangeFinished = {
                viewModel.beginDrag()
                viewModel.setBassSwapAt(value)
            },
            valueRange = TransitionSpec.MIN_BASS_SWAP_AT..TransitionSpec.MAX_BASS_SWAP_AT,
        )
    }
    Text(
        "The incoming track's bass (below ${TransitionSpec.BASS_SHELF_HZ.roundToInt()} Hz) is held down until the swap point, " +
            "then moves over from the outgoing track. Your equalizer settings are not changed.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = Spacing.sm),
    )
}

@Composable
private fun FilterPage(
    spec: TransitionSpec?,
    viewModel: TransitionStudioViewModel,
) {
    SheetTitle(EditorSection.FILTER)
    HorizontalDivider()
    TransitionFilter.entries.forEach { filter ->
        OptionRow(filter.label, selected = spec?.filter == filter) { viewModel.setFilter(filter) }
    }
    Text(
        "High-pass thins a track down to its top end; low-pass darkens it. Sweeps run over half of the transition.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = Spacing.sm),
    )
}

@Composable
private fun OptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .padding(vertical = Spacing.md),
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (selected) Icon(Icons.Default.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
    }
}

/** Duration choices, as large cards, plus a custom length. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DurationSheet(
    durationMs: Int,
    onDuration: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    GlassFloatingPanel(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                TransitionSpec.DURATION_PRESETS_MS.forEach { ms ->
                    val selected = ms == durationMs
                    Surface(
                        onClick = { onDuration(ms) },
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) MaterialTheme.colorScheme.inverseSurface else MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = if (selected) MaterialTheme.colorScheme.inverseOnSurface else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(width = 104.dp, height = 56.dp),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                        ) { Text(TransitionSpec.durationLabel(ms), style = MaterialTheme.typography.titleSmall) }
                    }
                }
            }
            var custom by remember(durationMs) { mutableFloatStateOf(durationMs.toFloat()) }
            Text(
                "Custom · ${TransitionSpec.durationLabel(TransitionSpec.clampDuration(custom.roundToInt()))}",
                style = MaterialTheme.typography.titleSmall,
            )
            Slider(
                value = custom,
                onValueChange = { custom = it },
                onValueChangeFinished = { onDuration(custom.roundToInt()) },
                valueRange = TransitionSpec.MIN_DURATION_MS.toFloat()..TransitionSpec.MAX_DURATION_MS.toFloat(),
                steps = (TransitionSpec.MAX_DURATION_MS - TransitionSpec.MIN_DURATION_MS) / TransitionSpec.DURATION_STEP_MS - 1,
            )
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Close") }
            Text(
                "Choose how long the tracks overlap. A transition never takes more than half of either track.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
