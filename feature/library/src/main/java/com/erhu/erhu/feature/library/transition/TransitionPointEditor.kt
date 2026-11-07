package com.erhu.erhu.feature.library.transition

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import com.erhu.erhu.core.designsystem.component.GlassFloatingPanel
import com.erhu.erhu.core.designsystem.component.GlassMenu
import com.erhu.erhu.core.designsystem.component.GlassMenuAnchor
import com.erhu.erhu.core.designsystem.component.GlassMenuItem
import com.erhu.erhu.core.designsystem.component.glassMenuTrigger
import com.erhu.erhu.core.designsystem.foundation.Spacing
import com.erhu.erhu.core.model.TransitionRole
import com.erhu.erhu.core.model.TransitionSpec
import java.util.Locale

/** Local numeric draft. Cancel never changes a curve; Apply is one existing undoable edit. */
@Composable
internal fun TransitionPointEditor(
    spec: TransitionSpec,
    durationMs: Int,
    onApply: (TransitionPointEdit) -> Unit,
    onDismiss: () -> Unit,
) {
    var role by remember { mutableStateOf(TransitionRole.OUTGOING) }
    var index by remember { mutableStateOf(1) }
    var menu by remember { mutableStateOf(false) }
    val points = TransitionEditing.curveOf(spec, role).points
    val handle = TransitionCurveHandle(role, index.coerceIn(points.indices))
    GlassFloatingPanel(onDismissRequest = onDismiss) {
        Text("Exact point values", style = MaterialTheme.typography.titleLarge)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = Spacing.sm)) {
            TransitionRole.entries.forEachIndexed { position, option ->
                SegmentedButton(
                    selected = role == option,
                    onClick = {
                        role = option
                        index = 1
                    },
                    shape = SegmentedButtonDefaults.itemShape(position, TransitionRole.entries.size),
                ) { Text(if (option == TransitionRole.OUTGOING) "Outgoing" else "Incoming") }
            }
        }
        GlassMenuAnchor {
            TextButton(onClick = { menu = true }, modifier = Modifier.glassMenuTrigger()) {
                Text("Point " + (handle.index + 1) + " of " + points.size)
            }
            GlassMenu(label = "Volume point", expanded = menu, onDismissRequest = { menu = false }) {
                points.indices.forEach { point ->
                    GlassMenuItem(text = { Text("Point " + (point + 1)) }, onClick = {
                        index = point
                        menu = false
                    })
                }
            }
        }
        key(handle, spec, durationMs) {
            PointValueFields(spec, handle, durationMs, onApply, onDismiss)
        }
    }
}

@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
private fun PointValueFields(
    spec: TransitionSpec,
    handle: TransitionCurveHandle,
    durationMs: Int,
    onApply: (TransitionPointEdit) -> Unit,
    onDismiss: () -> Unit,
) {
    val initial = TransitionPointPrecision.values(spec, handle, durationMs) ?: return
    val count = TransitionEditing.curveOf(spec, handle.role).points.size
    val fixedTime = handle.index == 0 || handle.index == count - 1
    val fixedGain = TransitionEditing.fixedGain(handle.role, handle.index, count - 1) != null
    val initialTime = pointNumber(initial.timeMs)
    val initialGain = pointNumber(initial.gainDb)
    var time by remember { mutableStateOf(initialTime) }
    var gain by remember { mutableStateOf(initialGain) }
    var silence by remember { mutableStateOf(initial.silence) }
    val timeValue = if (time == initialTime) initial.timeMs else time.toDoubleOrNull()
    val gainValue = if (gain == initialGain) initial.gainDb else gain.toDoubleOrNull()
    val valid = timeValue?.isFinite() == true && (silence || gainValue?.isFinite() == true)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        OutlinedTextField(
            value = time,
            onValueChange = { time = it.take(MAX_POINT_INPUT_CHARS) },
            label = { Text("Time in overlap · ms") },
            enabled = !fixedTime,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = timeValue?.isFinite() != true,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Point time in milliseconds" },
        )
        OutlinedTextField(
            value = gain,
            onValueChange = { gain = it.take(MAX_POINT_INPUT_CHARS) },
            label = { Text("Gain · dB") },
            enabled = !fixedGain && !silence,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            isError = !silence && gainValue?.isFinite() != true,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Point gain in decibels" },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Silence · −∞ dB", Modifier.weight(1f))
            Switch(
                checked = silence,
                onCheckedChange = { silence = it },
                enabled = !fixedGain,
                modifier = Modifier.semantics { contentDescription = "Exact point silence" },
            )
        }
        Text(
            "Time uses the actual overlap (" + durationMs + " ms) and stays between neighbouring points. " +
                "Fixed endpoints are preserved. Gain is limited to −96…0 dB; Silence sets exact zero.",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            TextButton(
                enabled = valid,
                onClick = {
                    onApply(
                        TransitionPointEdit(
                            handle,
                            TransitionPointValues(
                                timeValue!!,
                                gainValue?.takeIf { it.isFinite() } ?: initial.gainDb,
                                silence,
                            ),
                        ),
                    )
                },
            ) { Text("Apply") }
        }
    }
}

private const val MAX_POINT_INPUT_CHARS = 20

private fun pointNumber(value: Double): String = String.format(Locale.ROOT, "%.3f", value).trimEnd('0').trimEnd('.')
