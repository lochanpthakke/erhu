package com.erhu.erhu.feature.library

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.erhu.erhu.core.playback.diagnostics.DecoderInfo
import com.erhu.erhu.core.playback.diagnostics.MediaFormats
import com.erhu.erhu.core.playback.diagnostics.OutputDevice
import com.erhu.erhu.core.playback.diagnostics.OutputDiagnostics
import com.erhu.erhu.core.playback.diagnostics.PlayerDiagnostics
import com.erhu.erhu.core.playback.diagnostics.Provenance
import com.erhu.erhu.core.sound.dsp.EqStage
import com.erhu.erhu.core.sound.dsp.HeadroomEstimate
import com.erhu.erhu.core.sound.dsp.StageStatus
import com.erhu.erhu.core.sound.visualizer.PeakReading
import java.util.Locale

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
    val offloaded = player.audioTrack?.offload == true
    DetailSection(
        title = "Processing (app side)",
        explanation =
            "A logical view of the processing erhu itself performs, in its real order. After the app, Android mixes, may " +
                "resample, and may apply system or device effects (and Bluetooth re-encodes); that part is hidden from apps, so it " +
                "is shown as one step. The app has no preamp, bass boost, virtualizer, loudness or limiter stage. During a " +
                "Smooth Transition the outgoing track's last seconds play in a separate, private player that is not shown here.",
    ) {
        ChainStep(
            title = "Decoder",
            state = if (player.inputFormat != null) "Running" else "Idle",
            active = player.inputFormat != null,
            detail = decoder?.name ?: player.decoderName ?: "Not reported yet",
            isLast = false,
            appControlled = false,
        )
        ChainStep(
            title = "Transition fade",
            state = if (transitionFading) "Fading in" else "Unity",
            active = transitionFading,
            detail = "Changes the level only while Smooth Transitions fades the next track of an album or playlist in",
            isLast = false,
            appControlled = true,
        )
        ChainStep(
            title = "Equalizer",
            state = if (offloaded) "Skipped (offload)" else eq.status.label,
            active = eq.status == StageStatus.ACTIVE && !offloaded,
            detail = eq.reason + (if (eq.status == StageStatus.ACTIVE) "; samples beyond full scale are clipped" else ""),
            isLast = false,
            appControlled = true,
            action = equalizerLink,
        )
        ChainStep(
            title = "Visualizer tap",
            state = if (tapCollecting) "Reading" else "Idle",
            active = tapCollecting,
            detail = "Read-only copy for the visualizer and the peak meter below; never changes the audio",
            isLast = false,
            appControlled = true,
        )
        ChainStep(
            title = "Android audio system",
            state = "Not visible",
            active = false,
            detail =
                "Mixing, possible resampling, system and device effects" +
                    (output?.spatializer?.takeIf { it.enabled }?.let { "; spatial audio on" } ?: ""),
            isLast = false,
            appControlled = false,
        )
        ChainStep(
            title = "Output",
            state =
                output
                    ?.route
                    ?.firstOrNull()
                    ?.kind
                    ?.label ?: "Unknown",
            active = output?.route?.isNotEmpty() == true,
            detail = output?.route?.joinToString { it.typeName }?.ifEmpty { null } ?: "Route not reported",
            isLast = true,
            appControlled = false,
        )
    }
}

@Composable
internal fun OutputSection(output: OutputDiagnostics?) {
    DetailSection(
        title = "Output",
        explanation =
            "Devices as Android describes them. Sample rates, channels and encodings are what a device advertises it can accept, " +
                "not the exact format it receives now. On Android 13 and later the route for music is reported by Android; on " +
                "earlier versions it is estimated from the connected devices.",
    ) {
        if (output == null) {
            DetailNote("Reading output devices…")
            return@DetailSection
        }
        val route = output.route
        if (route.isEmpty()) DetailRow("Route", null as String?)
        route.forEach { device -> DeviceRows(device, output.routeSource) }
        output.spatializer?.let {
            DetailRow(
                "Spatial audio",
                if (it.enabled) {
                    "On"
                } else if (it.available) {
                    "Available, off"
                } else {
                    "Not available"
                },
                Provenance.PLATFORM,
            )
        }
        val others = output.connected.filter { c -> route.none { it.typeName == c.typeName && it.productName == c.productName } }
        if (others.isNotEmpty()) DetailRow("Also connected", others.joinToString { it.typeName }, Provenance.PLATFORM)
    }
}

@Composable
private fun DeviceRows(
    device: OutputDevice,
    source: Provenance,
) {
    DetailRow(
        "Route",
        device.kind.label,
        source,
        if (source ==
            Provenance.ESTIMATE
        ) {
            "Estimated: this Android version does not report the route"
        } else {
            null
        },
    )
    DetailRow("Device", listOfNotNull(device.productName, device.typeName).distinct().joinToString(" · "), Provenance.PLATFORM)
    DetailRow(
        "Advertised sample rates",
        device.sampleRatesHz.takeIf { it.isNotEmpty() }?.joinToString { MediaFormats.sampleRateText(it) } ?: "Not listed (any)",
        Provenance.PLATFORM,
    )
    DetailRow(
        "Advertised channels",
        device.channelCounts.takeIf { it.isNotEmpty() }?.joinToString() ?: "Not listed (any)",
        Provenance.PLATFORM,
    )
    if (device.encodings.isNotEmpty()) DetailRow("Advertised encodings", device.encodings.joinToString(), Provenance.PLATFORM)
}

@Composable
internal fun AnalysisSection(
    eqActive: Boolean,
    headroom: HeadroomEstimate?,
    peak: PeakReading?,
    onResetPeak: () -> Unit,
) {
    DetailSection(
        title = "Headroom",
        explanation =
            "The estimate comes from the EQ settings: any boost can push loud music past full scale (0 dBFS), where the EQ clips. " +
                "The measurement shows the largest sample after the EQ while this screen is open. It cannot see peaks between samples " +
                "(true peak) or what Android does afterwards, and full-scale samples may already be in the recording.",
    ) {
        if (headroom != null) {
            DetailRow(
                "Clipping risk from EQ",
                if (eqActive) {
                    String.format(
                        Locale.ROOT,
                        "%s (max boost %+.1f dB)",
                        headroom.risk.label,
                        headroom.maxBoostDb,
                    )
                } else {
                    headroom.risk.label
                },
                Provenance.ESTIMATE,
            )
            DetailNote(headroom.advice)
        }
        val reading = peak?.takeIf { it.frames > 0 }
        DetailRow(
            "Sample peak after EQ",
            reading?.peakDbfs?.let { String.format(Locale.ROOT, "%.1f dBFS", it) },
            Provenance.MEASURED,
            reading?.let { String.format(Locale.ROOT, "Over %.0f s · %d samples at full scale", it.secondsMeasured, it.fullScaleSamples) }
                ?: "Measured while music plays with this screen open",
        )
        TextButton(onClick = onResetPeak) { Text("Reset measurement") }
    }
}
