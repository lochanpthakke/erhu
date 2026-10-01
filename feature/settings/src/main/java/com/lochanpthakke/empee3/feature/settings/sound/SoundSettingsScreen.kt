@file:Suppress("detekt:All")

package com.lochanpthakke.empee3.feature.settings.sound

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import com.lochanpthakke.empee3.core.designsystem.component.SectionHeader
import com.lochanpthakke.empee3.core.designsystem.component.TopBarNavigation
import com.lochanpthakke.empee3.core.designsystem.component.forScrollingContent
import com.lochanpthakke.empee3.core.designsystem.foundation.Spacing
import com.lochanpthakke.empee3.core.designsystem.foundation.TextStyles
import com.lochanpthakke.empee3.core.sound.dsp.CustomEqAudioProcessor
import com.lochanpthakke.empee3.core.sound.model.EqPreset
import com.lochanpthakke.empee3.core.sound.model.SoundSettings
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

    AppScaffold(
        topBar = {
            AppTopBar(
                navigation = navigation,
                title = title,
                actions = {
                    IconButton(onClick = { viewModel.resetToFlat() }) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = "Reset equalizer to flat",
                        )
                    }
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
                    .padding(Spacing.screenGutter)
                    .padding(bottom = scroll.contentBottom),
        ) {
            // EQ Toggle Switch
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(Spacing.lg),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Enable Equalizer",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Switch(
                        checked = soundSettings.isEqEnabled,
                        onCheckedChange = { viewModel.setEqEnabled(it) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.lg))

            // Ableton EQ8-style Canvas curve visualizer
            Eq8CurveCanvas(
                bandGains = soundSettings.currentBandGains,
                customEqAudioProcessor = viewModel.customEqAudioProcessor,
            )

            Spacer(modifier = Modifier.height(Spacing.lg))

            // Preset Selector Dropdown
            PresetSelectorRow(
                soundSettings = soundSettings,
                onPresetSelected = { viewModel.applyPreset(it) },
                onSaveCustomClick = { showSaveDialog = true },
                onDeleteCustomClick = { viewModel.deleteCustomPreset(it) },
            )

            Spacer(modifier = Modifier.height(Spacing.lg))

            // 8 Band Sliders
            SectionHeader(text = "Equalizer Bands")
            Spacer(modifier = Modifier.height(Spacing.sm))

            for (i in 0 until CustomEqAudioProcessor.BAND_COUNT) {
                val freqLabel = formatFrequency(CustomEqAudioProcessor.BAND_FREQUENCIES[i])
                val gainDb = soundSettings.currentBandGains.getOrElse(i) { 0f }

                BandGainSlider(
                    bandNumber = i + 1,
                    freqLabel = freqLabel,
                    gainDb = gainDb,
                    enabled = soundSettings.isEqEnabled,
                    onGainChanged = { newGain ->
                        viewModel.setBandGain(i, newGain)
                    },
                )
            }

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("MagicNumber", "LongParameterList", "LongMethod")
private fun PresetSelectorRow(
    soundSettings: SoundSettings,
    onPresetSelected: (String) -> Unit,
    onSaveCustomClick: () -> Unit,
    onDeleteCustomClick: (String) -> Unit,
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

        Spacer(modifier = Modifier.width(Spacing.md))

        IconButton(onClick = onSaveCustomClick) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Save Custom Preset",
            )
        }
    }
}

@Composable
@Suppress("MagicNumber", "LongParameterList")
private fun BandGainSlider(
    bandNumber: Int,
    freqLabel: String,
    gainDb: Float,
    enabled: Boolean,
    onGainChanged: (Float) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$bandNumber. $freqLabel",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(80.dp),
        )
        Slider(
            value = gainDb,
            onValueChange = onGainChanged,
            valueRange = -12f..12f,
            enabled = enabled,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${if (gainDb > 0) "+" else ""}${String.format("%.1f", gainDb)} dB",
            style = TextStyles.metadata,
            maxLines = 1,
            modifier = Modifier.widthIn(min = 60.dp),
        )
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

    AlertDialog(
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

@Suppress("MagicNumber")
private fun formatFrequency(freqHz: Float): String =
    if (freqHz >= 1000f) {
        "${(freqHz / 1000f).roundToInt()} kHz"
    } else {
        "${freqHz.roundToInt()} Hz"
    }
