@file:Suppress("detekt:All")

package com.erhu.erhu.feature.settings.sound

import com.erhu.erhu.core.sound.model.EqMode
import com.erhu.erhu.core.sound.dsp.BandFilter
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.Switch
import androidx.compose.material3.FilterChip
import androidx.compose.material.icons.filled.Edit
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SaveAs
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.erhu.erhu.core.common.locale.AppLocaleManager
import com.erhu.erhu.core.common.locale.LocaleFormatters
import com.erhu.erhu.core.designsystem.component.GlassAlertDialog
import com.erhu.erhu.core.designsystem.component.TopBarNavigation
import com.erhu.erhu.core.designsystem.foundation.IconSize
import com.erhu.erhu.core.designsystem.foundation.Spacing
import com.erhu.erhu.core.designsystem.theme.ErhuGlass
import com.erhu.erhu.core.designsystem.theme.popupGlass
import com.erhu.erhu.core.sound.dsp.CustomEqAudioProcessor
import com.erhu.erhu.core.sound.dsp.FilterType
import com.erhu.erhu.core.sound.model.BandConfig
import com.erhu.erhu.core.sound.model.EqPreset
import com.erhu.erhu.core.sound.model.SoundSettings
import com.erhu.erhu.feature.settings.component.SettingsGroup
import com.erhu.erhu.feature.settings.component.SettingsPage
import com.erhu.erhu.feature.settings.component.SettingsPanel
import com.erhu.erhu.feature.settings.component.SettingsSection
import com.erhu.erhu.feature.settings.component.SettingsSegmentedControl
import com.erhu.erhu.feature.settings.component.SettingsSwitchRow
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import com.erhu.erhu.core.common.R as CommonR

@Composable
@Suppress("MagicNumber", "LongMethod", "CyclomaticComplexMethod", "NestedBlockDepth")
fun SoundSettingsScreen(
    viewModel: SoundViewModel,
    title: String,
    navigation: TopBarNavigation,
    modifier: Modifier = Modifier,
) {
    val locale = AppLocaleManager.activeLocale(LocalContext.current)
    val soundSettings by viewModel.soundSettings.collectAsState()

    var showSaveDialog by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<EqPreset?>(null) }

    val selectedBandIndex = soundSettings.selectedBandIndex.coerceIn(0, soundSettings.activeBandCount - 1)
    val selectedBand = soundSettings.bands.getOrElse(selectedBandIndex) { SoundSettings.defaultBands()[selectedBandIndex] }
    val graphic = soundSettings.mode == EqMode.GRAPHIC
    val eqOn = soundSettings.isEqEnabled

    SettingsPage(
        title = title,
        navigation = navigation,
        modifier = modifier,
        pinnedContent = {
            BoxWithConstraints(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(ErhuGlass.backdropColor(MaterialTheme.colorScheme.background))
                        .padding(horizontal = Spacing.screenGutter),
            ) {
                // Leave most of the inset-adjusted height for controls in short windows.
                val graphHeight = minOf(220.dp, maxHeight * 0.42f)
                Eq8CurveCanvas(
                    bands = soundSettings.effectiveBands(),
                    selectedBandIndex = if (graphic) -1 else selectedBandIndex,
                    sampleRate = viewModel.customEqAudioProcessor.sampleRate(),
                    modifier = Modifier.height(graphHeight),
                    frequencyEditable = !graphic,
                    enabled = eqOn,
                    onNodeDrag = { index, freqHz, gainDb ->
                        if (graphic) viewModel.setGraphicGain(index, gainDb) else viewModel.setBandFrequencyAndGain(index, freqHz, gainDb)
                    },
                    onNodeSelect = { index -> if (!graphic) viewModel.setSelectedBandIndex(index) },
                )
            }
        },
    ) {
        SettingsGroup {
            row {
                SettingsSwitchRow(
                    title = stringResource(CommonR.string.eq_title),
                    summary = stringResource(if (eqOn) CommonR.string.eq_state_on else CommonR.string.eq_state_off),
                    icon = Icons.Default.Equalizer,
                    checked = eqOn,
                    onCheckedChange = { viewModel.setEqEnabled(it) },
                )
            }
        }

        SettingsSection(title = stringResource(CommonR.string.eq_preset_curve_section)) {
            SettingsPanel {
                SettingsSegmentedControl(
                    options = listOf("Graphic", "Parametric"),
                    selectedIndex = if (graphic) 0 else 1,
                    onSelect = { viewModel.setMode(if (it == 0) EqMode.GRAPHIC else EqMode.PARAMETRIC) },
                    enabled = eqOn,
                )
                Spacer(modifier = Modifier.height(Spacing.md))
                PresetAndResetHeader(
                    soundSettings = soundSettings,
                    onPresetSelected = { viewModel.applyPreset(it) },
                    onSaveCustomClick = { showSaveDialog = true },
                    onRenameCustomClick = { renaming = it },
                    onDeleteCustomClick = { viewModel.deleteCustomPreset(it) },
                    onResetClick = { viewModel.resetToFlat() },
                )
            }
        }

        if (graphic) {
            SettingsSection(title = stringResource(CommonR.string.eq_bands_section)) {
                GraphicBandsPanel(
                    gains = soundSettings.graphicGains,
                    locale = locale,
                    enabled = eqOn,
                    onGainChange = { index, gain -> viewModel.setGraphicGain(index, gain) },
                )
            }
        } else {
            SettingsSection(title = stringResource(CommonR.string.eq_bands_section)) {
                val bandDescriptions =
                    List(soundSettings.activeBandCount) {
                        stringResource(CommonR.string.eq_band_numbered, LocaleFormatters.formatInteger(it + 1, locale))
                    }
                SettingsPanel {
                    BandCountSelectorRow(
                        activeCount = soundSettings.activeBandCount,
                        locale = locale,
                        enabled = eqOn,
                        onCountSelected = { viewModel.setActiveBandCount(it) },
                    )
                    Spacer(modifier = Modifier.height(Spacing.md))
                    SettingsSegmentedControl(
                        options = List(soundSettings.activeBandCount) { LocaleFormatters.formatInteger(it + 1, locale) },
                        selectedIndex = selectedBandIndex,
                        onSelect = { viewModel.setSelectedBandIndex(it) },
                        describe = { bandDescriptions[it] },
                    )
                }
            }

            SettingsSection(
                title = stringResource(CommonR.string.eq_band_numbered, LocaleFormatters.formatInteger(selectedBandIndex + 1, locale)),
            ) {
                SelectedBandPanel(
                    band = selectedBand,
                    locale = locale,
                    enabled = eqOn,
                    onEnabledChange = { viewModel.setBandEnabled(selectedBandIndex, it) },
                    onFreqChange = { viewModel.setBandFrequency(selectedBandIndex, it) },
                    onGainChange = { viewModel.setBandGain(selectedBandIndex, it) },
                    onQChange = { viewModel.setBandQ(selectedBandIndex, it) },
                    onSlopeChange = { viewModel.setBandSlope(selectedBandIndex, it) },
                    onFilterTypeChange = { viewModel.setFilterType(selectedBandIndex, it) },
                    onReset = { viewModel.resetBand(selectedBandIndex) },
                )
            }
        }

        // Mono, balance, preamp and the other audio settings, below the Equalizer.
        AudioUtilityControls(soundSettings.utilities, viewModel::updateUtilities)
    }

    if (showSaveDialog) {
        SavePresetDialog(
            onDismiss = { showSaveDialog = false },
            onSave = { name ->
                viewModel.saveCustomPreset(name)
                showSaveDialog = false
            },
        )
    }
    renaming?.let { preset ->
        SavePresetDialog(
            initialName = preset.name,
            title = "Rename preset",
            onDismiss = { renaming = null },
            onSave = { name ->
                viewModel.renameCustomPreset(preset.id, name)
                renaming = null
            },
        )
    }
}

/** Graphic mode: the 8 fixed bands' gains. */
@Composable
private fun GraphicBandsPanel(
    gains: List<Float>,
    locale: Locale,
    enabled: Boolean,
    onGainChange: (Int, Float) -> Unit,
) {
    SettingsPanel {
        CustomEqAudioProcessor.BAND_FREQUENCIES.forEachIndexed { index, frequency ->
            val gain = gains.getOrElse(index) { 0f }
            SliderLabel(label = formatFrequency(frequency, locale), value = formatGain(gain, locale))
            Slider(
                value = gain.coerceIn(CustomEqAudioProcessor.MIN_GAIN_DB, CustomEqAudioProcessor.MAX_GAIN_DB),
                onValueChange = { onGainChange(index, (it * 2f).roundToInt() / 2f) },
                valueRange = CustomEqAudioProcessor.MIN_GAIN_DB..CustomEqAudioProcessor.MAX_GAIN_DB,
                enabled = enabled,
            )
        }
    }
}

@Composable
private fun BandCountSelectorRow(
    activeCount: Int,
    locale: Locale,
    enabled: Boolean,
    onCountSelected: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(CommonR.string.eq_active_bands_label),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        FilledTonalIconButton(
            onClick = { if (activeCount > CustomEqAudioProcessor.MIN_BAND_COUNT) onCountSelected(activeCount - 1) },
            enabled = enabled && activeCount > CustomEqAudioProcessor.MIN_BAND_COUNT,
        ) {
            Icon(imageVector = Icons.Default.Remove, contentDescription = stringResource(CommonR.string.eq_fewer_bands_cd))
        }
        Text(
            text = LocaleFormatters.formatInteger(activeCount, locale),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(36.dp),
        )
        FilledTonalIconButton(
            onClick = { if (activeCount < CustomEqAudioProcessor.BAND_COUNT) onCountSelected(activeCount + 1) },
            enabled = enabled && activeCount < CustomEqAudioProcessor.BAND_COUNT,
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = stringResource(CommonR.string.eq_more_bands_cd))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
@Suppress("MagicNumber", "LongMethod", "LongParameterList", "CyclomaticComplexMethod")
private fun SelectedBandPanel(
    band: BandConfig,
    locale: Locale,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onFreqChange: (Float) -> Unit,
    onGainChange: (Float) -> Unit,
    onQChange: (Float) -> Unit,
    onSlopeChange: (Int) -> Unit,
    onFilterTypeChange: (FilterType) -> Unit,
    onReset: () -> Unit,
) {
    var editingFrequency by remember { mutableStateOf(false) }
    val editable = enabled && band.enabled
    SettingsPanel {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Band on", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(checked = band.enabled, onCheckedChange = onEnabledChange, enabled = enabled)
        }
        Spacer(modifier = Modifier.height(Spacing.md))

        Text(stringResource(CommonR.string.eq_filter_type_label), style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(Spacing.sm))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            FilterType.entries.forEach { type ->
                FilterChip(
                    selected = band.filterType == type,
                    onClick = { onFilterTypeChange(type) },
                    label = { Text(filterTypeName(type)) },
                    enabled = editable,
                )
            }
        }
        Spacer(modifier = Modifier.height(Spacing.md))

        // Frequency: a log slider, and the value itself opens precise entry.
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(CommonR.string.eq_center_frequency_label), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = { editingFrequency = true }, enabled = editable) { Text(formatPreciseFrequency(band.frequencyHz)) }
        }
        Slider(
            value = frequencyToNormalized(band.frequencyHz),
            onValueChange = { norm -> onFreqChange(normalizedToFrequency(norm)) },
            valueRange = 0f..1f,
            enabled = editable,
        )

        if (band.filterType.hasGain) {
            SliderLabel(label = stringResource(CommonR.string.eq_gain_label), value = formatGain(band.gainDb, locale))
            Slider(
                value = band.gainDb.coerceIn(CustomEqAudioProcessor.MIN_GAIN_DB, CustomEqAudioProcessor.MAX_GAIN_DB),
                onValueChange = { onGainChange((it * 10f).roundToInt() / 10f) },
                valueRange = CustomEqAudioProcessor.MIN_GAIN_DB..CustomEqAudioProcessor.MAX_GAIN_DB,
                enabled = editable,
            )
        }

        if (band.filterType.isPass) {
            Text("Slope", style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(Spacing.sm))
            SettingsSegmentedControl(
                options = BandFilter.SLOPES.map { "$it dB/oct" },
                selectedIndex = BandFilter.SLOPES.indexOf(band.slope).coerceAtLeast(0),
                onSelect = { onSlopeChange(BandFilter.SLOPES[it]) },
                enabled = editable,
            )
            Spacer(modifier = Modifier.height(Spacing.md))
        }

        // Steeper pass filters are fixed Butterworth cascades: Q only shapes the 12 dB/octave slope.
        if (!band.filterType.isPass || band.slope == 12) {
            SliderLabel(
                label = stringResource(CommonR.string.eq_q_factor_label),
                value = LocaleFormatters.formatDecimal(band.q.toDouble(), 2, locale),
            )
            Slider(
                value = qToNormalized(band.q),
                onValueChange = { onQChange(normalizedToQ(it)) },
                valueRange = 0f..1f,
                enabled = editable,
            )
            Text(
                text = if (band.filterType.isPass) "Higher Q adds a resonant peak at the cutoff" else "Lower Q = wider band, higher Q = narrower band",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(Spacing.md))
        TextButton(onClick = onReset, enabled = enabled) {
            Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(IconSize.standard))
            Spacer(modifier = Modifier.width(Spacing.sm))
            Text("Reset band")
        }
    }

    if (editingFrequency) {
        FrequencyEntryDialog(
            initialHz = band.frequencyHz,
            onDismiss = { editingFrequency = false },
            onSet = { hz ->
                onFreqChange(hz)
                editingFrequency = false
            },
        )
    }
}

private fun filterTypeName(type: FilterType): String =
    when (type) {
        FilterType.PEAKING -> "Peak"
        FilterType.LOW_SHELF -> "Low shelf"
        FilterType.HIGH_SHELF -> "High shelf"
        FilterType.LOW_PASS -> "Low pass"
        FilterType.HIGH_PASS -> "High pass"
        FilterType.NOTCH -> "Notch"
    }

/** 1 Hz below 1 kHz, 10 Hz above: "250 Hz", "2.45 kHz". */
@Suppress("MagicNumber")
private fun formatPreciseFrequency(hz: Float): String =
    if (hz >= 1000f) {
        "${"%.2f".format(java.util.Locale.ROOT, hz / 1000f).trimEnd('0').trimEnd('.')} kHz"
    } else {
        "${hz.roundToInt()} Hz"
    }

/** Precise frequency entry, 20 Hz - 20 kHz. */
@Composable
private fun FrequencyEntryDialog(
    initialHz: Float,
    onDismiss: () -> Unit,
    onSet: (Float) -> Unit,
) {
    var text by remember { mutableStateOf(initialHz.roundToInt().toString()) }
    val value = text.replace(',', '.').toFloatOrNull()
    val valid = value != null && value >= CustomEqAudioProcessor.MIN_FREQ_HZ && value <= CustomEqAudioProcessor.MAX_FREQ_HZ
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(CommonR.string.eq_center_frequency_label)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(8) },
                label = { Text("Hz (20 - 20000)") },
                singleLine = true,
                isError = !valid,
                shape = MaterialTheme.shapes.small,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        },
        confirmButton = {
            Button(onClick = { value?.let(onSet) }, enabled = valid) { Text(stringResource(CommonR.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(CommonR.string.action_cancel)) } },
    )
}

/** Q on a log scale, 0.1 - 10, so narrow and wide settings get equal room. */
private fun qToNormalized(q: Float): Float {
    val min = log10(CustomEqAudioProcessor.MIN_Q)
    val max = log10(CustomEqAudioProcessor.MAX_Q)
    return (log10(q.coerceIn(CustomEqAudioProcessor.MIN_Q, CustomEqAudioProcessor.MAX_Q)) - min) / (max - min)
}

@Suppress("MagicNumber")
private fun normalizedToQ(norm: Float): Float {
    val min = log10(CustomEqAudioProcessor.MIN_Q)
    val max = log10(CustomEqAudioProcessor.MAX_Q)
    val q = 10f.pow(min + norm.coerceIn(0f, 1f) * (max - min))
    return (q * 100f).roundToInt() / 100f
}

/** A slider's name with its current value on the right, in the accent. */
@Composable
private fun SliderLabel(
    label: String,
    value: String,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("MagicNumber", "LongParameterList", "LongMethod")
private fun PresetAndResetHeader(
    soundSettings: SoundSettings,
    onPresetSelected: (String) -> Unit,
    onSaveCustomClick: () -> Unit,
    onRenameCustomClick: (EqPreset) -> Unit,
    onDeleteCustomClick: (String) -> Unit,
    onResetClick: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val presets = soundSettings.presetsForMode
    val activePreset = presets.find { it.id == soundSettings.presetIdForMode }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.weight(1f),
        ) {
            OutlinedTextField(
                value = activePreset?.let { localizedPresetName(it) } ?: "Custom",
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                label = { Text(stringResource(CommonR.string.eq_preset_label)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.popupGlass(RoundedCornerShape(28.dp)),
                containerColor = Color.Transparent,
                shape = RoundedCornerShape(28.dp),
            ) {
                for (preset in presets) {
                    DropdownMenuItem(
                        text = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(localizedPresetName(preset), modifier = Modifier.weight(1f))
                                if (preset.isCustom) {
                                    IconButton(onClick = {
                                        expanded = false
                                        onRenameCustomClick(preset)
                                    }) {
                                        Icon(imageVector = Icons.Default.Edit, contentDescription = "Rename preset")
                                    }
                                    IconButton(onClick = { onDeleteCustomClick(preset.id) }) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = stringResource(CommonR.string.eq_delete_preset_cd),
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                        },
                        onClick = {
                            onPresetSelected(preset.id)
                            expanded = false
                        },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(Spacing.sm))

        FilledTonalIconButton(onClick = onSaveCustomClick) {
            Icon(imageVector = Icons.Default.SaveAs, contentDescription = stringResource(CommonR.string.eq_save_custom_preset_cd))
        }
        FilledTonalIconButton(onClick = onResetClick) {
            Icon(
                imageVector = Icons.Default.RestartAlt,
                contentDescription = stringResource(CommonR.string.eq_reset_flat_cd),
                modifier = Modifier.size(IconSize.standard),
            )
        }
    }
}

@Composable
private fun localizedPresetName(preset: EqPreset): String =
    when (preset.id) {
        EqPreset.FLAT.id -> stringResource(CommonR.string.eq_preset_flat)
        EqPreset.BASS_BOOSTER.id -> stringResource(CommonR.string.eq_preset_bass_booster)
        EqPreset.TREBLE_BOOSTER.id -> stringResource(CommonR.string.eq_preset_treble_booster)
        EqPreset.ROCK.id -> stringResource(CommonR.string.eq_preset_rock)
        EqPreset.POP.id -> stringResource(CommonR.string.eq_preset_pop)
        EqPreset.JAZZ.id -> stringResource(CommonR.string.eq_preset_jazz)
        EqPreset.VOCAL.id -> stringResource(CommonR.string.eq_preset_vocal)
        EqPreset.ACOUSTIC.id -> stringResource(CommonR.string.eq_preset_acoustic)
        else -> preset.name
    }

@Composable
private fun SavePresetDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    initialName: String = "",
    title: String? = null,
) {
    var presetName by remember { mutableStateOf(initialName) }

    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title ?: stringResource(CommonR.string.eq_save_preset_dialog_title)) },
        text = {
            OutlinedTextField(
                value = presetName,
                onValueChange = { presetName = it },
                label = { Text(stringResource(CommonR.string.eq_save_preset_name_label)) },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            )
        },
        confirmButton = {
            Button(
                onClick = { onSave(presetName) },
                enabled = presetName.isNotBlank(),
            ) {
                Text(stringResource(CommonR.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(CommonR.string.action_cancel))
            }
        },
    )
}

private fun frequencyToNormalized(freqHz: Float): Float {
    val minF = CustomEqAudioProcessor.MIN_FREQ_HZ
    val maxF = CustomEqAudioProcessor.MAX_FREQ_HZ
    val logMin = log10(minF)
    val logMax = log10(maxF)
    val logF = log10(freqHz.coerceIn(minF, maxF))
    return (logF - logMin) / (logMax - logMin)
}

private fun normalizedToFrequency(norm: Float): Float {
    val minF = CustomEqAudioProcessor.MIN_FREQ_HZ
    val maxF = CustomEqAudioProcessor.MAX_FREQ_HZ
    val logMin = log10(minF)
    val logMax = log10(maxF)
    val logF = logMin + norm.coerceIn(0f, 1f) * (logMax - logMin)
    return 10f.pow(logF).coerceIn(minF, maxF)
}

@Composable
@Suppress("MagicNumber")
private fun formatFrequency(
    freqHz: Float,
    locale: Locale,
): String =
    if (freqHz >= 1000f) {
        stringResource(CommonR.string.eq_freq_khz, LocaleFormatters.formatInteger((freqHz / 1000f).roundToInt(), locale))
    } else {
        stringResource(CommonR.string.eq_freq_hz, LocaleFormatters.formatInteger(freqHz.roundToInt(), locale))
    }

@Composable
private fun formatGain(
    gainDb: Float,
    locale: Locale,
): String {
    val sign = if (gainDb > 0f) "+" else ""
    return stringResource(CommonR.string.eq_gain_db, "$sign${LocaleFormatters.formatDecimal(gainDb.toDouble(), 1, locale)}")
}
