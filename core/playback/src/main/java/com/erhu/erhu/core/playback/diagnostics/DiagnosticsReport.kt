package com.erhu.erhu.core.playback.diagnostics

import com.erhu.erhu.core.sound.dsp.EqStage
import com.erhu.erhu.core.sound.dsp.HeadroomEstimate
import com.erhu.erhu.core.sound.dsp.StageStatus
import com.erhu.erhu.core.sound.visualizer.PeakReading
import java.util.Locale

/** Everything the report can include; any part may be missing. */
data class DiagnosticsSnapshot(
    val source: SourceDiagnostics?,
    val player: PlayerDiagnostics,
    val decoder: DecoderInfo?,
    val eq: EqStage?,
    val headroom: HeadroomEstimate?,
    val peak: PeakReading?,
    val output: OutputDiagnostics?,
    /** True while a Smooth Transition fades the incoming track in; null when not known. */
    val transitionFading: Boolean? = null,
)

/**
 * A plain-text summary of the current track's audio path, for the user to copy or share. Built only
 * from the inspector's values; it never contains file paths, URIs, library ids or device product
 * names (Bluetooth names are often personal), and text values are stripped of control characters
 * and length-limited. Unknown values are written as "unavailable", never as 0.
 */
object DiagnosticsReport {
    private const val MAX_TEXT = 120
    private const val UNAVAILABLE = "unavailable"

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    fun build(snapshot: DiagnosticsSnapshot): String =
        buildString {
            appendLine("erhu audio details")
            val source = snapshot.source
            section("Track")
            if (source == null) {
                line("Track", UNAVAILABLE)
            } else {
                line("Title", clean(source.title))
                line("Artist", clean(source.artist))
                line("Album", clean(source.album))
                section("Source file")
                fact("Container", source.container)
                fact("Codec", source.codec)
                fact("Sample rate", source.sampleRate)
                fact("Channels", source.channels)
                if (source.bitDepth != null) fact("Bit depth", source.bitDepth) else line("Bit depth", source.bitDepthNote ?: UNAVAILABLE)
                fact("Bitrate", source.bitrate)
                fact("Bitrate mode", source.bitrateMode)
                fact("Duration", source.duration)
                fact("File size", source.fileSize)
                source.problem?.let { line("Note", clean(it)) }
            }

            section("Playback (reported by the player)")
            val player = snapshot.player
            line("State", player.activity.name.lowercase(Locale.ROOT))
            line("Speed", player.speed?.let { String.format(Locale.ROOT, "%.2fx", it) } ?: UNAVAILABLE)
            val input = player.inputFormat
            line("Decoder input", input?.let(::formatText) ?: UNAVAILABLE)
            val decoder = snapshot.decoder
            line("Decoder", decoder?.name?.let(::clean) ?: player.decoderName?.let(::clean) ?: UNAVAILABLE)
            decoder?.hardwareAccelerated?.let { line("Hardware accelerated", yesNo(it)) }
            decoder?.softwareOnly?.let { line("Software only", yesNo(it)) }
            val track = player.audioTrack
            line(
                "Sent to Android audio",
                track?.let {
                    listOfNotNull(
                        it.encoding,
                        it.sampleRateHz?.let(MediaFormats::sampleRateText),
                        it.channelCount?.let(MediaFormats::channelsText),
                    ).joinToString(", ")
                } ?: UNAVAILABLE,
            )
            track?.let {
                line("Offload", yesNo(it.offload))
                line("Tunneling", yesNo(it.tunneling))
            }
            line("Underruns", player.underruns.toString())

            section("App processing")
            line(
                "Transition fade",
                when (snapshot.transitionFading) {
                    true -> "fading the incoming track in (Smooth Transitions)"
                    false -> "unity gain (no change)"
                    null -> UNAVAILABLE
                },
            )
            val eq = snapshot.eq
            if (eq == null) {
                line("Equalizer", UNAVAILABLE)
            } else {
                line("Equalizer", "${eq.status.label}: ${clean(eq.reason)}")
                if (eq.status == StageStatus.ACTIVE) {
                    eq.activeBands.forEachIndexed { i, b ->
                        line(
                            "  Band ${i + 1}",
                            String.format(
                                Locale.ROOT,
                                "%.0f Hz %+.1f dB Q %.2f %s%s",
                                b.frequencyHz,
                                b.gainDb,
                                b.q,
                                b.type.name.lowercase(Locale.ROOT),
                                if (b.skippedAtThisSampleRate) " (skipped at this sample rate)" else "",
                            ),
                        )
                    }
                }
            }
            line("Audio utilities", "Mono, balance, preamp, bass shelf, ReplayGain and sample-peak limiter: Settings → Audio & Equalizer")
            snapshot.headroom?.let {
                line(
                    "EQ-only clipping risk (estimate)",
                    String.format(Locale.ROOT, "%s, max EQ boost %+.1f dB", it.risk.label, it.maxBoostDb),
                )
            }
            snapshot.peak?.takeIf { it.frames > 0 }?.let {
                line(
                    "Measured sample peak (after EQ)",
                    String.format(
                        Locale.ROOT,
                        "%s dBFS over %.0f s, %d samples at full scale",
                        it.peakDbfs?.let { db -> String.format(Locale.ROOT, "%.1f", db) } ?: UNAVAILABLE,
                        it.secondsMeasured,
                        it.fullScaleSamples,
                    ),
                )
            }

            section("Output (reported by Android)")
            val output = snapshot.output
            if (output == null) {
                line("Route", UNAVAILABLE)
            } else {
                line(
                    "Route",
                    output.route.joinToString { it.typeName }.ifEmpty { UNAVAILABLE } +
                        if (output.routeSource == Provenance.ESTIMATE) " (estimated)" else "",
                )
                output.route.firstOrNull()?.let { device ->
                    line(
                        "Advertised sample rates",
                        device.sampleRatesHz.joinToString { MediaFormats.sampleRateText(it) }.ifEmpty { "not listed" },
                    )
                    line("Advertised channels", device.channelCounts.joinToString().ifEmpty { "not listed" })
                }
                output.spatializer?.let {
                    line(
                        "Spatial audio",
                        if (it.enabled) {
                            "enabled"
                        } else if (it.available) {
                            "available, off"
                        } else {
                            "not available"
                        },
                    )
                }
            }
            section("Limits")
            appendLine("- Device capabilities are what the device advertises, not the format it receives now.")
            appendLine("- Android and the device may resample or process audio after the app; that part is not visible to the app.")
            appendLine("- Peaks are sample peaks measured after the EQ; peaks between samples are not measured.")
        }.trimEnd()

    private fun StringBuilder.section(title: String) {
        appendLine()
        appendLine("[$title]")
    }

    private fun StringBuilder.line(
        label: String,
        value: String,
    ) {
        appendLine("$label: $value")
    }

    private fun StringBuilder.fact(
        label: String,
        fact: Fact?,
    ) {
        line(label, fact?.let { "${clean(it.value)} (${it.source.label}${it.note?.let { n -> "; ${clean(n)}" } ?: ""})" } ?: UNAVAILABLE)
    }

    private fun formatText(format: FormatSummary): String =
        listOfNotNull(
            MediaFormats.codecName(format.sampleMimeType),
            format.pcmEncoding,
            format.sampleRateHz?.let(MediaFormats::sampleRateText),
            format.channelCount?.let(MediaFormats::channelsText),
            format.averageBitrate?.let(MediaFormats::bitrateText),
        ).joinToString(", ").ifEmpty { UNAVAILABLE }

    private fun yesNo(value: Boolean) = if (value) "yes" else "no"

    /** No control characters or line breaks, and a sane length. */
    fun clean(text: String): String = text.replace(Regex("[\\p{Cntrl}]"), " ").trim().take(MAX_TEXT)
}
