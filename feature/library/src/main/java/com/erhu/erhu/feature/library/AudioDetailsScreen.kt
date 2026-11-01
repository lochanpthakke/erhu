package com.erhu.erhu.feature.library

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.erhu.erhu.core.designsystem.foundation.Spacing
import com.erhu.erhu.core.playback.diagnostics.DecoderInfo
import com.erhu.erhu.core.playback.diagnostics.MediaFormats
import com.erhu.erhu.core.playback.diagnostics.PlayerDiagnostics
import com.erhu.erhu.core.playback.diagnostics.Provenance
import com.erhu.erhu.core.sound.dsp.StageStatus
import java.util.Locale

/**
 * Audio details for the playing track: the file, what the player reports, the app's processing,
 * the output, and headroom. A read-only presentation over Now Playing; it holds the audio tap only
 * while visible (for the peak meter) and never changes playback or sound settings.
 */
@Composable
@Suppress("LongMethod")
internal fun AudioDetailsScreen(
    viewModel: AudioDetailsViewModel,
    onOpenEqualizer: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val source by viewModel.source.collectAsStateWithLifecycle()
    val player by viewModel.player.collectAsStateWithLifecycle()
    val decoder by viewModel.decoder.collectAsStateWithLifecycle()
    val eq by viewModel.eq.collectAsStateWithLifecycle()
    val headroom by viewModel.headroom.collectAsStateWithLifecycle()
    val output by viewModel.output.collectAsStateWithLifecycle()
    val peak by viewModel.peak.collectAsStateWithLifecycle()

    LifecycleStartEffect(viewModel.audioTap) {
        viewModel.audioTap.acquire()
        viewModel.audioTap.resetMeter()
        onStopOrDispose { viewModel.audioTap.release() }
    }
    LaunchedEffect(Unit) { viewModel.refresh() }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm)) {
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Close audio details") }
                Text("Audio details", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    viewModel.copyReport()
                    Toast.makeText(context, "Audio details copied", Toast.LENGTH_SHORT).show()
                }) { Icon(Icons.Default.ContentCopy, contentDescription = "Copy audio details") }
                IconButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, viewModel.report())
                    context.startActivity(Intent.createChooser(send, "Share audio details"))
                }) { Icon(Icons.Default.Share, contentDescription = "Share audio details") }
            }
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                item(key = "source") { SourceSection(source) }
                item(key = "playback") { PlaybackSection(player, decoder, source) }
                item(key = "chain") {
                    ChainSection(player, decoder, eq, output, viewModel.audioTap.isCollecting, viewModel.transitionFading) {
                        TextButton(onClick = onOpenEqualizer) { Text("Equalizer settings") }
                    }
                }
                item(key = "output") { OutputSection(output) }
                item(key = "analysis") { AnalysisSection(eq.status == StageStatus.ACTIVE, headroom, peak, viewModel::resetPeak) }
            }
        }
    }
}

@Composable
private fun SourceSection(source: SourceState) {
    DetailSection(
        title = "Source file",
        explanation =
            "Read from the file itself without decoding it. \"File header\" values are parsed from the audio header and are " +
                "exact; \"Container\" values are what Android's extractor reports; \"Library index\" values were recorded when the " +
                "library was scanned. Bit depth exists only for lossless formats that store it.",
    ) {
        when (source) {
            SourceState.Loading -> DetailNote("Inspecting the file…")
            SourceState.NothingPlaying -> DetailNote("Nothing is playing.")
            is SourceState.Ready -> {
                val s = source.diagnostics
                DetailRow("Title", s.title)
                DetailRow("Artist", s.artist)
                DetailRow("Album", s.album)
                DetailRow("Container", s.container)
                DetailRow("Codec", s.codec)
                DetailRow("Sample rate", s.sampleRate)
                DetailRow("Channels", s.channels)
                DetailRow("Bit depth", s.bitDepth, s.bitDepthNote)
                DetailRow("Bitrate", s.bitrate)
                s.bitrateMode?.let { DetailRow("Bitrate mode", it) }
                DetailRow("Duration", s.duration)
                DetailRow("File size", s.fileSize)
                s.problem?.let { DetailNote(it) }
            }
        }
    }
}

private fun formatLine(vararg parts: String?): String? = parts.filterNotNull().joinToString(" · ").ifEmpty { null }

@Composable
private fun PlaybackSection(
    player: PlayerDiagnostics,
    decoder: DecoderInfo?,
    source: SourceState,
) {
    DetailSection(
        title = "Playback",
        explanation =
            "What the player reports for the current track: the format going into the decoder, the decoder Android chose, and " +
                "the PCM format the player configured on Android's audio output after the app's processing. Offload means a " +
                "hardware DSP decodes the audio instead (the app's processing then does not run).",
    ) {
        val state =
            player.activity.name
                .lowercase(Locale.ROOT)
                .replaceFirstChar { it.uppercase() }
        DetailRow("State", state, Provenance.PLAYER)
        DetailRow("Speed", player.speed?.let { String.format(Locale.ROOT, "%.2f×", it) }, Provenance.PLAYER)
        val input =
            player.inputFormat?.let {
                formatLine(
                    MediaFormats.codecName(it.sampleMimeType),
                    it.pcmEncoding,
                    it.sampleRateHz?.let(MediaFormats::sampleRateText),
                    it.channelCount?.let(MediaFormats::channelsText),
                    it.averageBitrate?.let(MediaFormats::bitrateText),
                )
            }
        DetailRow("Decoder input", input, Provenance.PLAYER)
        DecoderRows(player, decoder)
        AudioTrackRows(player, source)
    }
}

@Composable
private fun DecoderRows(
    player: PlayerDiagnostics,
    decoder: DecoderInfo?,
) {
    val pending = if (player.decoderName == null) "Reported when a track starts" else null
    DetailRow("Decoder", decoder?.name ?: player.decoderName, Provenance.PLAYER, pending)
    decoder?.let { d ->
        val kind =
            when {
                d.hardwareAccelerated == true -> "Hardware accelerated"
                d.softwareOnly == true -> "Software"
                else -> null
            }
        DetailRow("Decoder type", formatLine(kind, d.maxInputChannels?.let { "up to $it channels" }), Provenance.PLATFORM)
    }
}

/** The PCM the player configured on Android's AudioTrack, and how that output has behaved. */
@Composable
private fun AudioTrackRows(
    player: PlayerDiagnostics,
    source: SourceState,
) {
    val track = player.audioTrack
    val sent =
        track?.let {
            formatLine(it.encoding, it.sampleRateHz?.let(MediaFormats::sampleRateText), it.channelCount?.let(MediaFormats::channelsText))
        }
    DetailRow("Sent to Android audio", sent, Provenance.PLAYER, if (track == null) "Reported once audio output starts" else null)
    track?.let {
        DetailRow("Offload", if (it.offload) "Yes" else "No", Provenance.PLAYER)
        if (it.tunneling) DetailRow("Tunneling", "Yes", Provenance.PLAYER)
    }
    DetailRow("Underruns", player.underruns.toString(), Provenance.PLAYER, "Times the output ran out of audio (audible gaps)")
    player.lastSinkError?.let { DetailRow("Last output error", it, Provenance.PLAYER) }
    val sourceBits = ((source as? SourceState.Ready)?.diagnostics?.bitDepth?.value)?.removeSuffix("-bit")?.toIntOrNull()
    if (sourceBits != null && sourceBits > PCM_16_BITS && track?.encoding == "PCM 16-bit") {
        DetailNote("The file stores $sourceBits-bit audio; the player sends 16-bit PCM to Android in this configuration.")
    }
}

private const val PCM_16_BITS = 16
