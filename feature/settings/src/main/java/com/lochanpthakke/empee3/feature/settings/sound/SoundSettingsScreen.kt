@file:Suppress("detekt:All")

package com.lochanpthakke.empee3.feature.settings.sound

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lochanpthakke.empee3.core.designsystem.component.AppScaffold
import com.lochanpthakke.empee3.core.designsystem.component.AppTopBar
import com.lochanpthakke.empee3.core.designsystem.component.GlassAlertDialog
import com.lochanpthakke.empee3.core.designsystem.component.SectionHeader
import com.lochanpthakke.empee3.core.designsystem.component.TopBarNavigation
import com.lochanpthakke.empee3.core.designsystem.component.forScrollingContent
import com.lochanpthakke.empee3.core.designsystem.foundation.Spacing
import com.lochanpthakke.empee3.core.sound.dsp.CustomEqAudioProcessor
import com.lochanpthakke.empee3.core.sound.dsp.FilterType
import com.lochanpthakke.empee3.core.sound.model.BandConfig
import com.lochanpthakke.empee3.core.sound.model.EqPreset
import com.lochanpthakke.empee3.core.sound.model.SoundSettings
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("MagicNumber", "LongMethod", "CyclomaticComplexMethod", "NestedBlockDepth")
fun SoundSettingsScreen(
    viewModel: SoundViewModel,
    title: String,
    navigation: TopBarNavigation,
    modifier: Modifier = Modifier,
) {
    val soundSettings by viewModel.soundSettings.collectAsState()
    val capabilities by viewModel.capabilities.collectAsState()

    var showSaveDialog by remember { mutableStateOf(false) }

    val selectedBandIndex = soundSettings.selectedBandIndex.coerceIn(0, soundSettings.activeBandCount - 1)
    val selectedBand = soundSettings.bands.getOrElse(selectedBandIndex) { SoundSettings.defaultBands()[selectedBandIndex] }

    AppScaffold(
        topBar = {
            AppTopBar(
                navigation = navigation,
                title = title,
                actions = {
                    Switch(
                        checked = soundSettings.isEqEnabled,
                        onCheckedChange = { viewModel.setEqEnabled(it) },
                    )
                },
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        val scroll = innerPadding.forScrollingContent()
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(scroll.container)
                    .verticalScroll(rememberScrollState())
                    .padding(top = scroll.contentTop)
                    .padding(Spacing.screenGutter)
                    .padding(bottom = scroll.contentBottom),
        ) {
            // Preset, Reset, and Interactive EQ Graph Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(modifier = Modifier.padding(Spacing.md)) {
                    PresetAndResetHeader(
                        soundSettings = soundSettings,
                        onPresetSelected = { viewModel.applyPreset(it) },
                        onSaveCustomClick = { showSaveDialog = true },
                        onDeleteCustomClick = { viewModel.deleteCustomPreset(it) },
                        onResetClick = { viewModel.resetToFlat() },
                    )

                    Spacer(modifier = Modifier.height(Spacing.sm))

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

            Spacer(modifier = Modifier.height(Spacing.md))

            // Active Band Count Stepper (- N +)
            BandCountSelectorRow(
                activeCount = soundSettings.activeBandCount,
                enabled = soundSettings.isEqEnabled,
                onCountSelected = { viewModel.setActiveBandCount(it) },
            )

            Spacer(modifier = Modifier.height(Spacing.md))

            // Band Indicator Row (Active bands only, outline pill for selected)
            BandSelectorRow(
                activeCount = soundSettings.activeBandCount,
                selectedIndex = selectedBandIndex,
                onSelectBand = { viewModel.setSelectedBandIndex(it) },
            )

            Spacer(modifier = Modifier.height(Spacing.md))

            // Selected Band Primary Controls Card
            SelectedBandCard(
                selectedBandIndex = selectedBandIndex,
                activeBandCount = soundSettings.activeBandCount,
                band = selectedBand,
                enabled = soundSettings.isEqEnabled,
                onFreqChange = { viewModel.setBandFrequency(selectedBandIndex, it) },
                onGainChange = { viewModel.setBandGain(selectedBandIndex, it) },
                onQChange = { viewModel.setBandQ(selectedBandIndex, it) },
                onFilterTypeChange = { viewModel.setFilterType(selectedBandIndex, it) },
            )

            Spacer(modifier = Modifier.height(Spacing.xl))

            // Audio Effects Section
            if (capabilities.isBassBoostSupported || capabilities.isVirtualizerSupported || capabilities.isLoudnessEnhancerSupported) {
                SectionHeader(text = "Audio Effects")
                Spacer(modifier = Modifier.height(Spacing.sm))

                if (capabilities.isBassBoostSupported) {
                    EffectSliderRow(
                        title = "Bass Boost",
                        enabled = soundSettings.isBassBoostEnabled,
                        value = soundSettings.bassBoostStrength.toFloat(),
                        valueRange = 0f..1000f,
                        onToggle = { viewModel.setBassBoost(it, soundSettings.bassBoostStrength) },
                        onValueChange = { viewModel.setBassBoost(soundSettings.isBassBoostEnabled, it.roundToInt().toShort()) },
                    )
                }

                if (capabilities.isVirtualizerSupported) {
                    EffectSliderRow(
                        title = "Virtualizer (3D Audio)",
                        enabled = soundSettings.isVirtualizerEnabled,
                        value = soundSettings.virtualizerStrength.toFloat(),
                        valueRange = 0f..1000f,
                        onToggle = { viewModel.setVirtualizer(it, soundSettings.virtualizerStrength) },
                        onValueChange = { viewModel.setVirtualizer(soundSettings.isVirtualizerEnabled, it.roundToInt().toShort()) },
                    )
                }

                if (capabilities.isLoudnessEnhancerSupported) {
                    EffectSliderRow(
                        title = "Loudness Enhancer",
                        enabled = soundSettings.isLoudnessEnabled,
                        value = soundSettings.loudnessGainMb.toFloat(),
                        valueRange = 0f..1000f,
                        onToggle = { viewModel.setLoudnessEnhancer(it, soundSettings.loudnessGainMb) },
                        onValueChange = { viewModel.setLoudnessEnhancer(soundSettings.isLoudnessEnabled, it.roundToInt()) },
                    )
                }
            }
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
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "Active Bands",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            IconButton(
                onClick = { if (activeCount > CustomEqAudioProcessor.MIN_BAND_COUNT) onCountSelected(activeCount - 1) },
                enabled = enabled && activeCount > CustomEqAudioProcessor.MIN_BAND_COUNT,
            ) {
                Text("−", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Text(
                text = "$activeCount",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            IconButton(
                onClick = { if (activeCount < CustomEqAudioProcessor.BAND_COUNT) onCountSelected(activeCount + 1) },
                enabled = enabled && activeCount < CustomEqAudioProcessor.BAND_COUNT,
            ) {
                Text("+", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun BandSelectorRow(
    activeCount: Int,
    selectedIndex: Int,
    onSelectBand: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 0 until activeCount) {
            val isSelected = i == selectedIndex
            val label = "${i + 1}"

            if (isSelected) {
                Surface(
                    onClick = { onSelectBand(i) },
                    shape = CircleShape,
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            } else {
                Box(
                    modifier =
                        Modifier
                            .clickable { onSelectBand(i) }
                            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
@Suppress("MagicNumber", "LongMethod")
private fun SelectedBandCard(
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

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Text(
                text = "Band ${selectedBandIndex + 1}  —  ${formatFrequency(band.frequencyHz)}, ${formatGain(band.gainDb)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(Spacing.sm))

            // Filter Type Selector for first and last active band
            if (isFirstBand || isLastBand) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Filter Type", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        val shelfType = if (isFirstBand) FilterType.LOW_SHELF else FilterType.HIGH_SHELF

                        FilterChip(
                            selected = band.filterType == FilterType.PEAKING,
                            onClick = { onFilterTypeChange(FilterType.PEAKING) },
                            enabled = enabled,
                            label = { Text("Bell") },
                        )
                        FilterChip(
                            selected = band.filterType == shelfType,
                            onClick = { onFilterTypeChange(shelfType) },
                            enabled = enabled,
                            label = { Text("Shelf") },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(Spacing.sm))
            }

            // 1. Logarithmic Center Frequency Slider
            Text(
                text = "Center Frequency: ${formatFrequency(band.frequencyHz)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            val normalizedFreq = frequencyToNormalized(band.frequencyHz)
            Slider(
                value = normalizedFreq,
                onValueChange = { norm ->
                    val newFreq = normalizedToFrequency(norm)
                    onFreqChange(newFreq)
                },
                valueRange = 0f..1f,
                enabled = enabled,
            )

            Spacer(modifier = Modifier.height(Spacing.xs))

            // 2. Gain Slider
            Text(
                text = "Gain: ${formatGain(band.gainDb)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = band.gainDb,
                onValueChange = onGainChange,
                valueRange = -15f..15f,
                enabled = enabled,
            )

            Spacer(modifier = Modifier.height(Spacing.xs))

            // 3. Q Factor Slider
            Text(
                text = "Q-Factor (Sharpness): ${String.format("%.2f", band.q)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = band.q,
                onValueChange = onQChange,
                valueRange = CustomEqAudioProcessor.MIN_Q..CustomEqAudioProcessor.MAX_Q,
                enabled = enabled,
            )
        }
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
        horizontalArrangement = Arrangement.SpaceBetween,
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
                label = { Text("Preset") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
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

        Spacer(modifier = Modifier.width(Spacing.xs))

        IconButton(onClick = onSaveCustomClick) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Save Custom Preset",
            )
        }

        TextButton(onClick = onResetClick) {
            Icon(
                imageVector = Icons.Default.RestartAlt,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Reset")
        }
    }
}

@Composable
@Suppress("MagicNumber", "LongParameterList")
private fun EffectSliderRow(
    title: String,
    enabled: Boolean,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onToggle: (Boolean) -> Unit,
    onValueChange: (Float) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Switch(checked = enabled, onCheckedChange = onToggle)
            }
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                enabled = enabled,
            )
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

private fun formatGain(gainDb: Float): String =
    "${if (gainDb > 0) "+" else ""}${String.format("%.1f", gainDb)} dB"
