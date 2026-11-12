@file:Suppress("detekt:All")

package com.erhu.erhu.feature.settings.sound

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

    val selectedBandIndex = soundSettings.selectedBandIndex.coerceIn(0, soundSettings.activeBandCount - 1)
    val selectedBand = soundSettings.bands.getOrElse(selectedBandIndex) { SoundSettings.defaultBands()[selectedBandIndex] }

    SettingsPage(title = title, navigation = navigation, modifier = modifier) {
        SettingsGroup {
            row {
                SettingsSwitchRow(
                    title = stringResource(CommonR.string.eq_title),
                    summary = stringResource(if (soundSettings.isEqEnabled) CommonR.string.eq_state_on else CommonR.string.eq_state_off),
                    icon = Icons.Default.Equalizer,
                    checked = soundSettings.isEqEnabled,
                    onCheckedChange = { viewModel.setEqEnabled(it) },
                )
            }
        }

        AudioUtilityControls(soundSettings.utilities, viewModel::updateUtilities)

        SettingsSection(title = stringResource(CommonR.string.eq_preset_curve_section)) {
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

        SettingsSection(title = stringResource(CommonR.string.eq_bands_section)) {
            val bandDescriptions =
                List(soundSettings.activeBandCount) {
                    stringResource(CommonR.string.eq_band_numbered, LocaleFormatters.formatInteger(it + 1, locale))
                }
            SettingsPanel {
                BandCountSelectorRow(
                    activeCount = soundSettings.activeBandCount,
                    locale = locale,
                    enabled = soundSettings.isEqEnabled,
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
                selectedBandIndex = selectedBandIndex,
                activeBandCount = soundSettings.activeBandCount,
                band = selectedBand,
                locale = locale,
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

@Composable
@Suppress("MagicNumber", "LongMethod")
private fun SelectedBandPanel(
    selectedBandIndex: Int,
    activeBandCount: Int,
    band: BandConfig,
    locale: Locale,
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
            Text(stringResource(CommonR.string.eq_filter_type_label), style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(Spacing.sm))
            SettingsSegmentedControl(
                options =
                    listOf(
                        stringResource(CommonR.string.eq_filter_type_bell),
                        stringResource(CommonR.string.eq_filter_type_shelf),
                    ),
                selectedIndex = if (band.filterType == shelfType) 1 else 0,
                onSelect = { onFilterTypeChange(if (it == 1) shelfType else FilterType.PEAKING) },
                enabled = enabled,
            )
            Spacer(modifier = Modifier.height(Spacing.lg))
        }

        // 1. Logarithmic Center Frequency Slider
        SliderLabel(
            label = stringResource(CommonR.string.eq_center_frequency_label),
            value = formatFrequency(band.frequencyHz, locale),
        )
        Slider(
            value = frequencyToNormalized(band.frequencyHz),
            onValueChange = { norm -> onFreqChange(normalizedToFrequency(norm)) },
            valueRange = 0f..1f,
            enabled = enabled,
        )

        // 2. Gain Slider
        SliderLabel(
            label = stringResource(CommonR.string.eq_gain_label),
            value = formatGain(band.gainDb, locale),
        )
        Slider(
            value = band.gainDb,
            onValueChange = onGainChange,
            valueRange = -15f..15f,
            enabled = enabled,
        )

        // 3. Q Factor Slider
        SliderLabel(
            label = stringResource(CommonR.string.eq_q_factor_label),
            value = LocaleFormatters.formatDecimal(band.q.toDouble(), 2, locale),
        )
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
                value = localizedPresetName(activePreset),
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
                for (preset in soundSettings.allPresets) {
                    DropdownMenuItem(
                        text = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(localizedPresetName(preset))
                                if (preset.isCustom) {
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
) {
    var presetName by remember { mutableStateOf("") }

    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(CommonR.string.eq_save_preset_dialog_title)) },
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
