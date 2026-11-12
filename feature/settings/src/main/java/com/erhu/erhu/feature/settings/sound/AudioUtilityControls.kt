/*
 * Copyright (C) 2026 Kshitij H Kekunnaya
 *
 * This file is part of erhu.
 * Licensed under the GNU General Public License v3.0.
 * See the LICENSE file in the project root.
 */

package com.erhu.erhu.feature.settings.sound

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import com.erhu.erhu.core.common.locale.AppLocaleManager
import com.erhu.erhu.core.common.locale.LocaleFormatters
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
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import com.erhu.erhu.core.common.R as CommonR

@Composable
internal fun AudioUtilityControls(
    u: AudioUtilities,
    update: ((AudioUtilities) -> AudioUtilities) -> Unit,
) {
    val locale = AppLocaleManager.activeLocale(LocalContext.current)
    val normalizationOptions =
        listOf(
            stringResource(CommonR.string.audio_normalization_off),
            stringResource(CommonR.string.audio_normalization_track),
            stringResource(CommonR.string.audio_normalization_album),
        )
    val normalizationDescriptions =
        normalizationOptions.map { stringResource(CommonR.string.audio_normalization_desc, it) }
    UtilitySection(stringResource(CommonR.string.audio_channels_section)) {
        SettingsGroup {
            row {
                SettingsSwitchRow(
                    title = stringResource(CommonR.string.audio_mono_title),
                    checked = u.mono,
                    onCheckedChange = { checked -> update { it.copy(mono = checked) } },
                    summary = stringResource(CommonR.string.audio_mono_summary),
                )
            }
        }
        UtilitySlider(
            title = stringResource(CommonR.string.audio_balance_title),
            value = u.balance,
            range = -1f..1f,
            description =
                when {
                    u.balance == 0f -> stringResource(CommonR.string.audio_balance_centre)
                    u.balance < 0f ->
                        stringResource(
                            CommonR.string.audio_balance_left,
                            LocaleFormatters.formatPercent(abs(u.balance).toDouble(), locale),
                        )
                    else ->
                        stringResource(
                            CommonR.string.audio_balance_right,
                            LocaleFormatters.formatPercent(u.balance.toDouble(), locale),
                        )
                },
            change = { v -> update { it.copy(balance = v) } },
            reset = { update { it.copy(balance = 0f) } },
        )
    }
    UtilitySection(stringResource(CommonR.string.audio_level_section)) {
        val preampSign = if (u.preampDb > 0f) "+" else ""
        UtilitySlider(
            title = stringResource(CommonR.string.audio_preamp_title),
            value = u.preampDb,
            range = -24f..12f,
            description =
                stringResource(
                    CommonR.string.eq_gain_db,
                    "$preampSign${LocaleFormatters.formatDecimal(u.preampDb.toDouble(), 1, locale)}",
                ),
            change = { v -> update { it.copy(preampDb = v) } },
            reset = { update { it.copy(preampDb = 0f) } },
        )
        UtilitySlider(
            title = stringResource(CommonR.string.audio_bass_boost_title),
            value = u.bassDb,
            range = 0f..12f,
            description =
                stringResource(
                    CommonR.string.eq_gain_db,
                    "+${LocaleFormatters.formatDecimal(u.bassDb.toDouble(), 1, locale)}",
                ),
            change = { v -> update { it.copy(bassDb = v) } },
            reset = { update { it.copy(bassDb = 0f) } },
        )
        SettingsGroup {
            row {
                SettingsSwitchRow(
                    title = stringResource(CommonR.string.audio_clipping_title),
                    checked = u.limiter,
                    onCheckedChange = { checked -> update { it.copy(limiter = checked) } },
                    summary = stringResource(CommonR.string.audio_clipping_summary),
                )
            }
        }
    }
    UtilitySection(
        title = stringResource(CommonR.string.audio_normalization_section),
        footer = stringResource(CommonR.string.audio_normalization_footer),
    ) {
        SettingsPanel {
            SettingsSegmentedControl(
                options = normalizationOptions,
                selectedIndex = u.replayGain.ordinal,
                onSelect = { index -> update { it.copy(replayGain = ReplayGainMode.entries[index]) } },
                describe = { normalizationDescriptions[it] },
            )
        }
    }
}

@Composable
internal fun PlaybackSettingsScreen(
    vm: SoundViewModel,
    title: String,
    onBack: () -> Unit,
) {
    val locale = AppLocaleManager.activeLocale(LocalContext.current)
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
        UtilitySection(
            title = stringResource(CommonR.string.playback_transitions_section),
            footer = stringResource(CommonR.string.playback_transitions_footer),
        ) {
            SettingsGroup {
                row {
                    SettingsSwitchRow(
                        title = stringResource(CommonR.string.playback_enable_transitions_title),
                        checked = transition.global.masterEnabled,
                        onCheckedChange = vm::setTransitionMaster,
                        summary = stringResource(CommonR.string.playback_enable_transitions_summary),
                    )
                }
                row {
                    SettingsSwitchRow(
                        title = stringResource(CommonR.string.playback_default_crossfade_title),
                        checked = transition.global.default is TransitionChoice.Custom,
                        onCheckedChange = vm::setDefaultCrossfade,
                    )
                }
            }
            val spec = (transition.global.default as? TransitionChoice.Custom)?.spec
            if (spec != null) {
                UtilitySlider(
                    title = stringResource(CommonR.string.playback_default_crossfade_duration_title),
                    value = spec.durationMs.toFloat(),
                    range = TransitionSpec.MIN_DURATION_MS.toFloat()..TransitionSpec.MAX_DURATION_MS.toFloat(),
                    description =
                        stringResource(
                            CommonR.string.playback_duration_seconds,
                            LocaleFormatters.formatDecimal(spec.durationMs / 1000.0, 2, locale),
                        ),
                    change = { vm.setCrossfadeDuration(it.roundToInt()) },
                    reset = { vm.setCrossfadeDuration(2000) },
                )
            }
        }
        UtilitySection(stringResource(CommonR.string.playback_rate_section)) {
            UtilitySlider(
                title = stringResource(CommonR.string.playback_speed_title),
                value = u.speed,
                range = 0.5f..2f,
                description =
                    stringResource(
                        CommonR.string.playback_speed_value,
                        LocaleFormatters.formatDecimal(u.speed.toDouble(), 2, locale),
                    ),
                change = { v -> vm.updateUtilities { it.copy(speed = v) } },
                reset = { vm.updateUtilities { it.copy(speed = 1f) } },
            )
            val semitones = (12 * ln(u.pitch.toDouble()) / ln(2.0)).toFloat()
            val roundedSemitones = semitones.roundToInt()
            UtilitySlider(
                title = stringResource(CommonR.string.playback_pitch_title),
                value = semitones,
                range = -12f..12f,
                description =
                    pluralStringResource(
                        CommonR.plurals.playback_pitch_semitones,
                        abs(roundedSemitones),
                        roundedSemitones,
                    ),
                change = { v ->
                    vm.updateUtilities { it.copy(pitch = 2.0.pow(v.roundToInt() / 12.0).toFloat()) }
                },
                reset = { vm.updateUtilities { it.copy(pitch = 1f) } },
            )
            Text(
                stringResource(CommonR.string.playback_rate_hint),
                style = MaterialTheme.typography.bodySmall,
            )
            SettingsGroup {
                row {
                    SettingsSwitchRow(
                        title = stringResource(CommonR.string.playback_skip_silence_title),
                        checked = u.skipSilence,
                        onCheckedChange = { checked -> vm.updateUtilities { it.copy(skipSilence = checked) } },
                        summary = stringResource(CommonR.string.playback_skip_silence_summary),
                    )
                }
            }
        }
        UtilitySection(
            title = stringResource(CommonR.string.playback_sleep_timer_section),
            footer = stringResource(CommonR.string.playback_sleep_timer_footer),
        ) {
            SettingsPanel {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Text(
                        when {
                            u.sleepAtTrackEnd -> stringResource(CommonR.string.playback_sleep_at_track_end)
                            u.sleepDeadlineMs > 0 -> {
                                val remainingMin = (((u.sleepDeadlineMs - now).coerceAtLeast(0) / 1000 + 59) / 60).toInt()
                                pluralStringResource(
                                    CommonR.plurals.playback_sleep_minutes_remaining,
                                    remainingMin,
                                    remainingMin,
                                )
                            }
                            else -> stringResource(CommonR.string.playback_sleep_off)
                        },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        listOf(15, 30, 60).forEach { value ->
                            TextButton({ vm.setSleepMinutes(value) }, modifier = Modifier.weight(1f)) {
                                Text(
                                    stringResource(
                                        CommonR.string.playback_sleep_minutes_short,
                                        LocaleFormatters.formatInteger(value, locale),
                                    ),
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        minutes,
                        { minutes = it.filter(Char::isDigit).take(3) },
                        label = { Text(stringResource(CommonR.string.playback_sleep_minutes_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button({
                        minutes.toIntOrNull()?.let(vm::setSleepMinutes)
                    }, modifier = Modifier.fillMaxWidth(), enabled = minutes.toIntOrNull()?.let { it in 1..720 } == true) {
                        Text(stringResource(CommonR.string.playback_sleep_start_timer))
                    }
                    TextButton(
                        { vm.updateUtilities { it.copy(sleepDeadlineMs = 0, sleepAtTrackEnd = true) } },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = playback.currentSong != null,
                    ) { Text(stringResource(CommonR.string.playback_sleep_end_of_track)) }
                    TextButton(
                        { vm.updateUtilities { it.copy(sleepDeadlineMs = 0, sleepAtTrackEnd = false) } },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = u.sleepDeadlineMs > 0 || u.sleepAtTrackEnd,
                    ) { Text(stringResource(CommonR.string.playback_sleep_cancel_timer)) }
                }
            }
        }
        UtilitySection(stringResource(CommonR.string.playback_automatic_section)) {
            SettingsPanel {
                Text(
                    stringResource(CommonR.string.playback_automatic_summary),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    description,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                IconButton(onClick = reset) {
                    Icon(
                        Icons.Default.RestartAlt,
                        contentDescription = stringResource(CommonR.string.audio_reset_cd, title),
                    )
                }
            }
            Slider(value.coerceIn(range), change, valueRange = range, modifier = Modifier.semantics { contentDescription = title })
        }
    }
}

@Composable
private fun UtilitySection(
    title: String,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    SettingsSection(title, footer = footer) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md), content = content)
    }
}
