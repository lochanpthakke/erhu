package com.erhu.erhu.feature.settings.sound

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import com.erhu.erhu.core.designsystem.component.TopBarNavigation
import com.erhu.erhu.core.designsystem.foundation.Spacing
import com.erhu.erhu.core.model.AudioUtilities
import com.erhu.erhu.core.model.ReplayGainMode
import com.erhu.erhu.core.model.TransitionChoice
import com.erhu.erhu.core.model.TransitionSpec
import com.erhu.erhu.feature.settings.component.SettingsGroup
import com.erhu.erhu.feature.settings.component.SettingsPage
import com.erhu.erhu.feature.settings.component.SettingsPanel
import com.erhu.erhu.feature.settings.component.SettingsSection
import com.erhu.erhu.feature.settings.component.SettingsSegmentedControl
import com.erhu.erhu.feature.settings.component.SettingsSwitchRow
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

@Composable
internal fun AudioUtilityControls(
    u: AudioUtilities,
    update: ((AudioUtilities) -> AudioUtilities) -> Unit,
) {
    SettingsSection("Channels") {
        SettingsGroup {
            row {
                SettingsSwitchRow("Mono audio", u.mono, { checked ->
                    update {
                        it.copy(mono = checked)
                    }
                }, summary = "Combine channels so both sides carry the same mix.")
            }
        }
        UtilitySlider(
            "Left / right balance",
            u.balance,
            -1f..1f,
            if (u.balance ==
                0f
            ) {
                "Centre"
            } else if (u.balance <
                0f
            ) {
                "Left ${(100 * -u.balance).roundToInt()}%"
            } else {
                "Right ${(100 * u.balance).roundToInt()}%"
            },
            { v -> update { it.copy(balance = v) } },
            { update { it.copy(balance = 0f) } },
        )
    }
    SettingsSection("Level & protection") {
        UtilitySlider(
            "Preamp",
            u.preampDb,
            -24f..12f,
            String.format(Locale.ROOT, "%+.1f dB", u.preampDb),
            { v -> update { it.copy(preampDb = v) } },
            { update { it.copy(preampDb = 0f) } },
        )
        UtilitySlider(
            "Bass boost",
            u.bassDb,
            0f..12f,
            String.format(Locale.ROOT, "+%.1f dB", u.bassDb),
            { v -> update { it.copy(bassDb = v) } },
            { update { it.copy(bassDb = 0f) } },
        )
        SettingsGroup {
            row {
                SettingsSwitchRow(
                    "Clipping protection",
                    u.limiter,
                    { checked -> update { it.copy(limiter = checked) } },
                    summary = "Linked sample-peak limiter at −1 dBFS after EQ. Not a true-peak limiter.",
                )
            }
        }
    }
    SettingsSection(
        "Volume normalization (ReplayGain)",
        footer =
            "Uses embedded ReplayGain gain and peak tags. Untagged tracks stay unchanged. " +
                "Album mode preserves album loudness relationships and requires album tags.",
    ) {
        SettingsSegmentedControl(
            listOf("Off", "Track", "Album"),
            u.replayGain.ordinal,
            { index -> update { it.copy(replayGain = ReplayGainMode.entries[index]) } },
            describe = { "ReplayGain ${listOf("Off", "Track", "Album")[it]}" },
        )
    }
}

@Composable
internal fun PlaybackSettingsScreen(
    vm: SoundViewModel,
    title: String,
    onBack: () -> Unit,
) {
    val sound by vm.soundSettings.collectAsState()
    val transition by vm.transitionConfig.collectAsState()
    val playback by vm.playbackState.collectAsState()
    val u = sound.utilities
    var minutes by remember { mutableStateOf("30") }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(u.sleepDeadlineMs) {
        while (u.sleepDeadlineMs > 0) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    SettingsPage(title, TopBarNavigation.Detail(onBack)) {
        SettingsSection(
            "Transitions",
            footer =
                "Default crossfade applies to inherited album/playlist transitions. " +
                    "Collection and pair edits remain in Transition Studio. Gapless playback is automatic when no crossfade applies.",
        ) {
            SettingsGroup {
                row {
                    SettingsSwitchRow(
                        "Enable transitions",
                        transition.global.masterEnabled,
                        vm::setTransitionMaster,
                        summary = "Master switch; preserves collection and song-pair settings.",
                    )
                }
                row {
                    SettingsSwitchRow(
                        "Default crossfade",
                        transition.global.default is TransitionChoice.Custom,
                        vm::setDefaultCrossfade,
                    )
                }
            }
            val spec = (transition.global.default as? TransitionChoice.Custom)?.spec
            if (spec != null) {
                UtilitySlider(
                    "Default crossfade duration",
                    spec.durationMs.toFloat(),
                    TransitionSpec.MIN_DURATION_MS.toFloat()..TransitionSpec.MAX_DURATION_MS.toFloat(),
                    String.format(Locale.ROOT, "%.2f s", spec.durationMs / 1000f),
                    {
                        vm.setCrossfadeDuration(it.roundToInt())
                    },
                    { vm.setCrossfadeDuration(2000) },
                )
            }
        }
        SettingsSection("Playback rate") {
            UtilitySlider(
                "Playback speed",
                u.speed,
                0.5f..2f,
                String.format(Locale.ROOT, "%.2f×", u.speed),
                { v -> vm.updateUtilities { it.copy(speed = v) } },
                { vm.updateUtilities { it.copy(speed = 1f) } },
            )
            val semitones = (12 * ln(u.pitch.toDouble()) / ln(2.0)).toFloat()
            UtilitySlider(
                "Pitch",
                semitones,
                -12f..12f,
                "${semitones.roundToInt()} semitones",
                { v ->
                    vm.updateUtilities { it.copy(pitch = 2.0.pow(v.roundToInt() / 12.0).toFloat()) }
                },
                { vm.updateUtilities { it.copy(pitch = 1f) } },
            )
            Text(
                "Speed preserves your selected pitch. Pitch adjustment does not change the selected playback speed.",
                style = MaterialTheme.typography.bodySmall,
            )
            SettingsGroup {
                row {
                    SettingsSwitchRow("Skip silence", u.skipSilence, { checked ->
                        vm.updateUtilities {
                            it.copy(skipSilence = checked)
                        }
                    }, summary = "Skip detected silent sections; can alter intentional pauses in music.")
                }
            }
        }
        SettingsSection("Sleep timer") {
            Text(
                when {
                    u.sleepAtTrackEnd -> "Pause at the end of the current track"
                    u.sleepDeadlineMs > 0 -> "${((u.sleepDeadlineMs - now).coerceAtLeast(0) / 1000 + 59) / 60} min remaining"
                    else -> "Off"
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                listOf(15, 30, 60).forEach { value -> TextButton({ vm.setSleepMinutes(value) }) { Text("$value min") } }
            }
            OutlinedTextField(
                minutes,
                { minutes = it.filter(Char::isDigit).take(3) },
                label = { Text("Minutes (1–720)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Button({
                minutes.toIntOrNull()?.let(vm::setSleepMinutes)
            }, enabled = minutes.toIntOrNull()?.let { it in 1..720 } == true) { Text("Start timer") }
            TextButton(
                { vm.updateUtilities { it.copy(sleepDeadlineMs = 0, sleepAtTrackEnd = true) } },
                enabled =
                    playback.currentSong != null,
            ) { Text("End of current track") }
            TextButton(
                { vm.updateUtilities { it.copy(sleepDeadlineMs = 0, sleepAtTrackEnd = false) } },
                enabled =
                    u.sleepDeadlineMs > 0 || u.sleepAtTrackEnd,
            ) { Text("Cancel timer") }
            Text(
                "Pauses playback without clearing the queue. Runs with the screen off; no notification permission is required.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        SettingsSection("Automatic playback handling") {
            Text(
                "Gapless playback, queue restoration, headphone-disconnect pausing and audio-focus handling remain automatic. Repeat, shuffle and A–B repeat are available in Now Playing.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun UtilitySlider(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    description: String,
    change: (Float) -> Unit,
    reset: () -> Unit,
) {
    SettingsPanel {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodyMedium)
            Slider(value.coerceIn(range), change, valueRange = range, modifier = Modifier.semantics { contentDescription = title })
            TextButton(reset) { Text("Reset") }
        }
    }
}
