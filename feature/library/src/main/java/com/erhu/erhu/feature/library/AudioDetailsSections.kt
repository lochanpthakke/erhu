package com.erhu.erhu.feature.library

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.erhu.erhu.core.common.R
import com.erhu.erhu.core.common.locale.AppLocaleManager
import com.erhu.erhu.core.common.locale.LocaleFormatters
import com.erhu.erhu.core.playback.diagnostics.DecoderInfo
import com.erhu.erhu.core.playback.diagnostics.MediaFormats
import com.erhu.erhu.core.playback.diagnostics.OutputDevice
import com.erhu.erhu.core.playback.diagnostics.OutputDiagnostics
import com.erhu.erhu.core.playback.diagnostics.OutputKind
import com.erhu.erhu.core.playback.diagnostics.PlayerDiagnostics
import com.erhu.erhu.core.playback.diagnostics.Provenance
import com.erhu.erhu.core.sound.dsp.ClippingRisk
import com.erhu.erhu.core.sound.dsp.EqStage
import com.erhu.erhu.core.sound.dsp.HeadroomEstimate
import com.erhu.erhu.core.sound.dsp.StageStatus
import com.erhu.erhu.core.sound.visualizer.PeakReading
import java.util.Locale
import kotlin.math.roundToInt

private const val HZ_PER_KHZ = 1000f

/**
 * The app-side signal path as it is implemented, in order: decoder, the EQ processor, the
 * visualizer's read-only copy, then Android's own audio system (mixing, resampling, system and
 * device effects), which the app cannot see into, and the output.
 */
@Composable
@Suppress("LongParameterList", "LongMethod")
internal fun ChainSection(
    player: PlayerDiagnostics,
    decoder: DecoderInfo?,
    eq: EqStage,
    output: OutputDiagnostics?,
    tapCollecting: Boolean,
    transitionFading: Boolean,
    equalizerLink: @Composable () -> Unit,
) {
    val locale = AppLocaleManager.activeLocale(LocalContext.current)
    val offloaded = player.audioTrack?.offload == true
    DetailSection(
        title = stringResource(R.string.audio_details_chain_title),
        explanation = stringResource(R.string.audio_details_chain_explanation),
    ) {
        ChainStep(
            title = stringResource(R.string.audio_details_step_decoder),
            state =
                stringResource(
                    if (player.inputFormat !=
                        null
                    ) {
                        R.string.audio_details_step_running
                    } else {
                        R.string.audio_details_step_idle
                    },
                ),
            active = player.inputFormat != null,
            detail = decoder?.name ?: player.decoderName ?: stringResource(R.string.audio_details_step_not_reported),
            isLast = false,
            appControlled = false,
        )
        ChainStep(
            title = stringResource(R.string.audio_details_step_transition),
            state = stringResource(if (transitionFading) R.string.audio_details_step_fading_in else R.string.audio_details_step_unity),
            active = transitionFading,
            detail = stringResource(R.string.audio_details_step_transition_detail),
            isLast = false,
            appControlled = true,
        )
        ChainStep(
            title = stringResource(R.string.audio_details_step_equalizer),
            state = stringResource(if (offloaded) R.string.audio_details_step_skipped_offload else eq.status.labelRes()),
            active = eq.status == StageStatus.ACTIVE && !offloaded,
            detail = localizedEqReason(eq, locale),
            isLast = false,
            appControlled = true,
            action = equalizerLink,
        )
        ChainStep(
            title = stringResource(R.string.audio_details_step_visualizer),
            state = stringResource(if (tapCollecting) R.string.audio_details_step_reading else R.string.audio_details_step_idle),
            active = tapCollecting,
            detail = stringResource(R.string.audio_details_step_visualizer_detail),
            isLast = false,
            appControlled = true,
        )
        ChainStep(
            title = stringResource(R.string.audio_details_step_android_system),
            state = stringResource(R.string.audio_details_step_not_visible),
            active = false,
            detail =
                stringResource(
                    if (output?.spatializer?.enabled == true) {
                        R.string.audio_details_step_android_detail_spatial
                    } else {
                        R.string.audio_details_step_android_detail
                    },
                ),
            isLast = false,
            appControlled = false,
        )
        ChainStep(
            title = stringResource(R.string.audio_details_step_output),
            state =
                stringResource(
                    output
                        ?.route
                        ?.firstOrNull()
                        ?.kind
                        ?.labelRes() ?: R.string.audio_details_output_unknown,
                ),
            active = output?.route?.isNotEmpty() == true,
            detail =
                output?.route?.joinToString { it.typeName }?.ifEmpty { null } ?: stringResource(R.string.audio_details_route_not_reported),
            isLast = true,
            appControlled = false,
        )
    }
}

@Composable
private fun localizedEqReason(
    eq: EqStage,
    locale: Locale,
): String =
    when (eq.status) {
        StageStatus.OFF -> stringResource(R.string.audio_details_eq_off)
        StageStatus.IDLE -> stringResource(R.string.audio_details_eq_idle)
        StageStatus.BYPASSED -> stringResource(R.string.audio_details_eq_bypassed)
        StageStatus.ACTIVE ->
            pluralStringResource(
                R.plurals.audio_details_eq_active,
                eq.activeBands.size,
                LocaleFormatters.formatInteger(eq.activeBands.size, locale),
            )
    }

private fun StageStatus.labelRes(): Int =
    when (this) {
        StageStatus.ACTIVE -> R.string.audio_details_stage_active
        StageStatus.BYPASSED -> R.string.audio_details_stage_bypassed
        StageStatus.OFF -> R.string.audio_details_stage_off
        StageStatus.IDLE -> R.string.audio_details_stage_idle
    }

private fun OutputKind.labelRes(): Int =
    when (this) {
        OutputKind.SPEAKER -> R.string.audio_details_output_kind_speaker
        OutputKind.EARPIECE -> R.string.audio_details_output_kind_earpiece
        OutputKind.WIRED -> R.string.audio_details_output_kind_wired
        OutputKind.BLUETOOTH -> R.string.audio_details_output_kind_bluetooth
        OutputKind.USB -> R.string.audio_details_output_kind_usb
        OutputKind.HDMI -> R.string.audio_details_output_kind_hdmi
        OutputKind.HEARING_AID -> R.string.audio_details_output_kind_hearing_aid
        OutputKind.OTHER -> R.string.audio_details_output_kind_other
    }

@Composable
internal fun OutputSection(output: OutputDiagnostics?) {
    DetailSection(
        title = stringResource(R.string.audio_details_output_title),
        explanation = stringResource(R.string.audio_details_output_explanation),
    ) {
        if (output == null) {
            DetailNote(stringResource(R.string.audio_details_reading_devices))
            return@DetailSection
        }
        val route = output.route
        if (route.isEmpty()) DetailRow(stringResource(R.string.audio_details_label_route), null as String?)
        route.forEach { device -> DeviceRows(device, output.routeSource) }
        output.spatializer?.let {
            DetailRow(
                stringResource(R.string.audio_details_label_spatial),
                stringResource(
                    if (it.enabled) {
                        R.string.audio_details_spatial_on
                    } else if (it.available) {
                        R.string.audio_details_spatial_off
                    } else {
                        R.string.audio_details_spatial_unavailable
                    },
                ),
                Provenance.PLATFORM,
            )
        }
        val others = output.connected.filter { c -> route.none { it.typeName == c.typeName && it.productName == c.productName } }
        if (others.isNotEmpty()) {
            DetailRow(stringResource(R.string.audio_details_label_also_connected), others.joinToString { it.typeName }, Provenance.PLATFORM)
        }
    }
}

@Composable
private fun DeviceRows(
    device: OutputDevice,
    source: Provenance,
) {
    DetailRow(
        stringResource(R.string.audio_details_label_route),
        stringResource(device.kind.labelRes()),
        source,
        if (source == Provenance.ESTIMATE) stringResource(R.string.audio_details_route_estimated_note) else null,
    )
    DetailRow(
        stringResource(R.string.audio_details_label_device),
        listOfNotNull(device.productName, device.typeName).distinct().joinToString(" · "),
        Provenance.PLATFORM,
    )
    DetailRow(
        stringResource(R.string.audio_details_label_sample_rates),
        device.sampleRatesHz.takeIf { it.isNotEmpty() }?.joinToString { MediaFormats.sampleRateText(it) }
            ?: stringResource(R.string.audio_details_not_listed_any),
        Provenance.PLATFORM,
    )
    DetailRow(
        stringResource(R.string.audio_details_label_advertised_channels),
        device.channelCounts.takeIf { it.isNotEmpty() }?.joinToString() ?: stringResource(R.string.audio_details_not_listed_any),
        Provenance.PLATFORM,
    )
    if (device.encodings.isNotEmpty()) {
        DetailRow(stringResource(R.string.audio_details_label_encodings), device.encodings.joinToString(), Provenance.PLATFORM)
    }
}

@Composable
internal fun AnalysisSection(
    eqActive: Boolean,
    headroom: HeadroomEstimate?,
    peak: PeakReading?,
    onResetPeak: () -> Unit,
) {
    val locale = AppLocaleManager.activeLocale(LocalContext.current)
    DetailSection(
        title = stringResource(R.string.audio_details_headroom_title),
        explanation = stringResource(R.string.audio_details_headroom_explanation),
    ) {
        if (headroom != null) {
            val riskLabel = stringResource(headroom.risk.labelRes())
            DetailRow(
                stringResource(R.string.audio_details_label_clipping_risk),
                if (eqActive) {
                    stringResource(
                        R.string.audio_details_risk_with_boost,
                        riskLabel,
                        String.format(locale, "%+.1f", headroom.maxBoostDb),
                    )
                } else {
                    riskLabel
                },
                Provenance.ESTIMATE,
            )
            DetailNote(localizedHeadroomAdvice(eqActive, headroom, locale))
        }
        val reading = peak?.takeIf { it.frames > 0 }
        DetailRow(
            stringResource(R.string.audio_details_label_sample_peak),
            reading?.peakDbfs?.let {
                stringResource(R.string.audio_details_peak_dbfs, LocaleFormatters.formatDecimal(it.toDouble(), 1, locale))
            },
            Provenance.MEASURED,
            reading?.let {
                pluralStringResource(
                    R.plurals.audio_details_peak_note,
                    it.fullScaleSamples.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    LocaleFormatters.formatInteger(it.secondsMeasured.roundToInt(), locale),
                    LocaleFormatters.formatInteger(it.fullScaleSamples, locale),
                )
            } ?: stringResource(R.string.audio_details_peak_measuring_note),
        )
        TextButton(onClick = onResetPeak) { Text(stringResource(R.string.audio_details_reset_measurement)) }
    }
}

@Composable
private fun localizedHeadroomAdvice(
    eqActive: Boolean,
    headroom: HeadroomEstimate,
    locale: Locale,
): String {
    val freqHz = headroom.atFrequencyHz
    return when {
        !eqActive -> stringResource(R.string.audio_details_advice_inactive)
        headroom.risk == ClippingRisk.NONE || freqHz == null -> stringResource(R.string.audio_details_advice_cut_only)
        else -> {
            val freqText =
                if (freqHz >= HZ_PER_KHZ) {
                    stringResource(
                        R.string.audio_details_khz,
                        LocaleFormatters.formatDecimal((freqHz / HZ_PER_KHZ).toDouble(), 1, locale),
                    )
                } else {
                    stringResource(R.string.audio_details_hz, LocaleFormatters.formatInteger(freqHz.toInt(), locale))
                }
            stringResource(
                R.string.audio_details_advice_boost,
                String.format(locale, "%+.1f", headroom.maxBoostDb),
                freqText,
                LocaleFormatters.formatDecimal(headroom.maxBoostDb.toDouble(), 1, locale),
            )
        }
    }
}

private fun ClippingRisk.labelRes(): Int =
    when (this) {
        ClippingRisk.NONE -> R.string.audio_details_risk_none
        ClippingRisk.LOW -> R.string.audio_details_risk_low
        ClippingRisk.MODERATE -> R.string.audio_details_risk_moderate
        ClippingRisk.HIGH -> R.string.audio_details_risk_high
    }
