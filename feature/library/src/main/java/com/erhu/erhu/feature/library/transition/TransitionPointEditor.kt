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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import com.erhu.erhu.core.common.R
import com.erhu.erhu.core.common.locale.AppLocaleManager
import com.erhu.erhu.core.common.locale.LocaleFormatters
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
    val points = TransitionEditing.curveOf(spec, role).points
    val handle = TransitionCurveHandle(role, index.coerceIn(points.indices))
    GlassFloatingPanel(onDismissRequest = onDismiss) {
        Text(stringResource(R.string.transition_exact_points_title), style = MaterialTheme.typography.titleLarge)
        RoleSegmentedButtons(
            selectedRole = role,
            onSelectRole = {
                role = it
                index = 1
            },
        )
        PointSelectorMenu(
            selectedIndex = handle.index,
            pointCount = points.size,
            onSelectPoint = { index = it },
        )
        key(handle, spec, durationMs) {
            PointValueFields(spec, handle, durationMs, onApply, onDismiss)
        }
    }
}

@Composable
private fun RoleSegmentedButtons(
    selectedRole: TransitionRole,
    onSelectRole: (TransitionRole) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = Spacing.sm)) {
        TransitionRole.entries.forEachIndexed { position, option ->
            SegmentedButton(
                selected = selectedRole == option,
                onClick = { onSelectRole(option) },
                shape = SegmentedButtonDefaults.itemShape(position, TransitionRole.entries.size),
            ) {
                Text(
                    stringResource(
                        if (option == TransitionRole.OUTGOING) {
                            R.string.transition_role_outgoing
                        } else {
                            R.string.transition_role_incoming
                        },
                    ),
                )
            }
        }
    }
}

@Composable
private fun PointSelectorMenu(
    selectedIndex: Int,
    pointCount: Int,
    onSelectPoint: (Int) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val locale = AppLocaleManager.activeLocale(LocalContext.current)
    GlassMenuAnchor {
        TextButton(onClick = { menu = true }, modifier = Modifier.glassMenuTrigger()) {
            Text(
                stringResource(
                    R.string.transition_point_label,
                    LocaleFormatters.formatInteger(selectedIndex + 1, locale),
                    LocaleFormatters.formatInteger(pointCount, locale),
                ),
            )
        }
        GlassMenu(
            label = stringResource(R.string.transition_point_menu_label),
            expanded = menu,
            onDismissRequest = { menu = false },
        ) {
            repeat(pointCount) { point ->
                GlassMenuItem(
                    text = {
                        Text(stringResource(R.string.transition_point_item, LocaleFormatters.formatInteger(point + 1, locale)))
                    },
                    onClick = {
                        onSelectPoint(point)
                        menu = false
                    },
                )
            }
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
    val locale = AppLocaleManager.activeLocale(LocalContext.current)
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
    val cdTime = stringResource(R.string.transition_point_cd_time)
    val cdGain = stringResource(R.string.transition_point_cd_gain)
    val cdSilence = stringResource(R.string.transition_point_cd_silence)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        OutlinedTextField(
            value = time,
            onValueChange = { time = it.take(MAX_POINT_INPUT_CHARS) },
            label = { Text(stringResource(R.string.transition_point_time_label)) },
            enabled = !fixedTime,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = timeValue?.isFinite() != true,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = cdTime },
        )
        OutlinedTextField(
            value = gain,
            onValueChange = { gain = it.take(MAX_POINT_INPUT_CHARS) },
            label = { Text(stringResource(R.string.transition_point_gain_label)) },
            enabled = !fixedGain && !silence,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            isError = !silence && gainValue?.isFinite() != true,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = cdGain },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.transition_point_silence_label), Modifier.weight(1f))
            Switch(
                checked = silence,
                onCheckedChange = { silence = it },
                enabled = !fixedGain,
                modifier = Modifier.semantics { contentDescription = cdSilence },
            )
        }
        Text(
            stringResource(R.string.transition_point_help, LocaleFormatters.formatInteger(durationMs, locale)),
            style = MaterialTheme.typography.bodySmall,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
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
            ) { Text(stringResource(R.string.action_apply)) }
        }
    }
}

private const val MAX_POINT_INPUT_CHARS = 20

private fun pointNumber(value: Double): String = String.format(Locale.ROOT, "%.3f", value).trimEnd('0').trimEnd('.')
