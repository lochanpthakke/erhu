@file:Suppress("detekt:All")

package com.lochanpthakke.empee3.feature.settings.sound

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lochanpthakke.empee3.core.designsystem.component.GlassAlertDialog
import com.lochanpthakke.empee3.core.designsystem.component.TopBarNavigation
import com.lochanpthakke.empee3.core.designsystem.foundation.IconSize
import com.lochanpthakke.empee3.core.designsystem.foundation.Spacing
import com.lochanpthakke.empee3.core.designsystem.theme.popupGlass
import com.lochanpthakke.empee3.core.sound.dsp.CustomEqAudioProcessor
import com.lochanpthakke.empee3.core.sound.dsp.FilterType
import com.lochanpthakke.empee3.core.sound.model.BandConfig
import com.lochanpthakke.empee3.core.sound.model.EqPreset
import com.lochanpthakke.empee3.core.sound.model.SoundSettings
import com.lochanpthakke.empee3.feature.settings.component.SettingsGroup
import com.lochanpthakke.empee3.feature.settings.component.SettingsPage
import com.lochanpthakke.empee3.feature.settings.component.SettingsPanel
import com.lochanpthakke.empee3.feature.settings.component.SettingsSection
import com.lochanpthakke.empee3.feature.settings.component.SettingsSegmentedControl
import com.lochanpthakke.empee3.feature.settings.component.SettingsSwitchRow
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

@Composable
@Suppress("MagicNumber", "LongMethod", "CyclomaticComplexMethod", "NestedBlockDepth")
fun SoundSettingsScreen(
    viewModel: SoundViewModel,
    title: String,
    navigation: TopBarNavigation,
    modifier: Modifier = Modifier,
) {
    val soundSettings by viewModel.soundSettings.collectAsState()

    var showSaveDialog by remember { mutableStateOf(false) }

    val selectedBandIndex = soundSettings.selectedBandIndex.coerceIn(0, soundSettings.activeBandCount - 1)
    val selectedBand = soundSettings.bands.getOrElse(selectedBandIndex) { SoundSettings.defaultBands()[selectedBandIndex] }

    SettingsPage(title = title, navigation = navigation, modifier = modifier) {
        SettingsGroup {
            row {
                SettingsSwitchRow(
                    title = "Equalizer",
                    summary = if (soundSettings.isEqEnabled) "On" else "Off",
                    icon = Icons.Default.Equalizer,
                    checked = soundSettings.isEqEnabled,
                    onCheckedChange = { viewModel.setEqEnabled(it) },
                )
            }
        }

        SettingsSection(title = "Preset & curve") {
            SettingsPanel {
                PresetAndResetHeader(
                    soundSettings = soundSettings,
                    onPresetSelected = { viewModel.applyPreset(it) },
                    onSaveCustomClick = { showSaveDialog = true },
                    onDeleteCustomClick = { viewModel.deleteCustomPreset(it) },
                    onResetClick = { viewModel.resetToFlat() },
                )

                Spacer(modifier = Modifier.height(Spacing.md))

                Eq8CurveCanvas(
                    bands = soundSettings.bands,
                    activeBandCount = soundSettings.activeBandCount,
                    selectedBandIndex = selectedBandIndex,
                    customEqAudioProcessor = viewModel.customEqAudioProcessor,
                    onNodeDrag = { index, freqHz, gainDb ->
                        viewModel.setBandFrequencyAndGain(index, freqHz, gainDb)
                    },
                    onNodeSelect = { index ->
                        viewModel.setSelectedBandIndex(index)
                    },
                )
            }
        }

        SettingsSection(title = "Bands") {
            SettingsPanel {
                BandCountSelectorRow(
                    activeCount = soundSettings.activeBandCount,
                    enabled = soundSettings.isEqEnabled,
                    onCountSelected = { viewModel.setActiveBandCount(it) },
                )
                Spacer(modifier = Modifier.height(Spacing.md))
                SettingsSegmentedControl(
                    options = List(soundSettings.activeBandCount) { "${it + 1}" },
                    selectedIndex = selectedBandIndex,
                    onSelect = { viewModel.setSelectedBandIndex(it) },
                    describe = { "Band ${it + 1}" },
                )
            }
        }

        SettingsSection(title = "Band ${selectedBandIndex + 1}") {
            SelectedBandPanel(
                selectedBandIndex = selectedBandIndex,
                activeBandCount = soundSettings.activeBandCount,
                band = selectedBand,
                enabled = soundSettings.isEqEnabled,
                onFreqChange = { viewModel.setBandFrequency(selectedBandIndex, it) },
                onGainChange = { viewModel.setBandGain(selectedBandIndex, it) },
                onQChange = { viewModel.setBandQ(selectedBandIndex, it) },
                onFilterTypeChange = { viewModel.setFilterType(selectedBandIndex, it) },
            )
        }
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
}

@Composable
private fun BandCountSelectorRow(
    activeCount: Int,
    enabled: Boolean,
    onCountSelected: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Active bands",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        FilledTonalIconButton(
            onClick = { if (activeCount > CustomEqAudioProcessor.MIN_BAND_COUNT) onCountSelected(activeCount - 1) },
            enabled = enabled && activeCount > CustomEqAudioProcessor.MIN_BAND_COUNT,
        ) {
            Icon(imageVector = Icons.Default.Remove, contentDescription = "Fewer bands")
        }
        Text(
            text = "$activeCount",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(36.dp),
        )
        FilledTonalIconButton(
            onClick = { if (activeCount < CustomEqAudioProcessor.BAND_COUNT) onCountSelected(activeCount + 1) },
            enabled = enabled && activeCount < CustomEqAudioProcessor.BAND_COUNT,
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = "More bands")
        }
    }
}

@Composable
@Suppress("MagicNumber", "LongMethod")
private fun SelectedBandPanel(
    selectedBandIndex: Int,
    activeBandCount: Int,
    band: BandConfig,
    enabled: Boolean,
    onFreqChange: (Float) -> Unit,
    onGainChange: (Float) -> Unit,
    onQChange: (Float) -> Unit,
    onFilterTypeChange: (FilterType) -> Unit,
) {
    val isFirstBand = selectedBandIndex == 0
    val isLastBand = selectedBandIndex == activeBandCount - 1

    SettingsPanel {
        // Filter type: only the first and last active band can be a shelf.
        if (isFirstBand || isLastBand) {
            val shelfType = if (isFirstBand) FilterType.LOW_SHELF else FilterType.HIGH_SHELF
            Text("Filter type", style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(Spacing.sm))
            SettingsSegmentedControl(
                options = listOf("Bell", "Shelf"),
                selectedIndex = if (band.filterType == shelfType) 1 else 0,
                onSelect = { onFilterTypeChange(if (it == 1) shelfType else FilterType.PEAKING) },
                enabled = enabled,
            )
            Spacer(modifier = Modifier.height(Spacing.lg))
        }

        // 1. Logarithmic Center Frequency Slider
        SliderLabel(label = "Center frequency", value = formatFrequency(band.frequencyHz))
        Slider(
            value = frequencyToNormalized(band.frequencyHz),
            onValueChange = { norm -> onFreqChange(normalizedToFrequency(norm)) },
            valueRange = 0f..1f,
            enabled = enabled,
        )

        // 2. Gain Slider
        SliderLabel(label = "Gain", value = formatGain(band.gainDb))
        Slider(
            value = band.gainDb,
            onValueChange = onGainChange,
            valueRange = -15f..15f,
            enabled = enabled,
        )

        // 3. Q Factor Slider
        SliderLabel(label = "Q-factor (sharpness)", value = String.format("%.2f", band.q))
        Slider(
            value = band.q,
            onValueChange = onQChange,
            valueRange = CustomEqAudioProcessor.MIN_Q..CustomEqAudioProcessor.MAX_Q,
            enabled = enabled,
        )
    }
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
    onDeleteCustomClick: (String) -> Unit,
    onResetClick: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val activePreset =
        soundSettings.allPresets.find { it.id == soundSettings.activePresetId }
            ?: EqPreset.FLAT

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
                value = activePreset.name,
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                label = { Text("Preset") },
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
                for (preset in soundSettings.allPresets) {
                    DropdownMenuItem(
                        text = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(preset.name)
                                if (preset.isCustom) {
                                    IconButton(onClick = { onDeleteCustomClick(preset.id) }) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete Preset",
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
            Icon(imageVector = Icons.Default.SaveAs, contentDescription = "Save Custom Preset")
        }
        FilledTonalIconButton(onClick = onResetClick) {
            Icon(imageVector = Icons.Default.RestartAlt, contentDescription = "Reset to flat", modifier = Modifier.size(IconSize.standard))
        }
    }
}

@Composable
private fun SavePresetDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var presetName by remember { mutableStateOf("") }

    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save Custom Preset") },
        text = {
            OutlinedTextField(
                value = presetName,
                onValueChange = { presetName = it },
                label = { Text("Preset Name") },
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
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
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

@Suppress("MagicNumber")
private fun formatFrequency(freqHz: Float): String =
    if (freqHz >= 1000f) {
        "${(freqHz / 1000f).roundToInt()} kHz"
    } else {
        "${freqHz.roundToInt()} Hz"
    }

private fun formatGain(gainDb: Float): String = "${if (gainDb > 0) "+" else ""}${String.format("%.1f", gainDb)} dB"
