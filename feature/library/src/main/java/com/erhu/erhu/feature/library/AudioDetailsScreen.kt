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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.erhu.erhu.core.common.R
import com.erhu.erhu.core.common.locale.AppLocaleManager
import com.erhu.erhu.core.common.locale.LocaleFormatters
import com.erhu.erhu.core.designsystem.foundation.Spacing
import com.erhu.erhu.core.playback.diagnostics.DecoderInfo
import com.erhu.erhu.core.playback.diagnostics.MediaFormats
import com.erhu.erhu.core.playback.diagnostics.PlayerActivity
import com.erhu.erhu.core.playback.diagnostics.PlayerDiagnostics
import com.erhu.erhu.core.playback.diagnostics.Provenance
import com.erhu.erhu.core.sound.dsp.StageStatus

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
            val copiedMessage = stringResource(R.string.audio_details_copied)
            val shareTitle = stringResource(R.string.audio_details_share_title)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm)) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.audio_details_cd_close))
                }
                Text(
                    stringResource(R.string.audio_details_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = {
                    viewModel.copyReport()
                    Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                }) { Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.audio_details_cd_copy)) }
                IconButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, viewModel.report())
                    context.startActivity(Intent.createChooser(send, shareTitle))
                }) { Icon(Icons.Default.Share, contentDescription = stringResource(R.string.audio_details_cd_share)) }
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
                        TextButton(onClick = onOpenEqualizer) { Text(stringResource(R.string.audio_details_open_equalizer)) }
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
        title = stringResource(R.string.audio_details_source_title),
        explanation = stringResource(R.string.audio_details_source_explanation),
    ) {
        when (source) {
            SourceState.Loading -> DetailNote(stringResource(R.string.audio_details_inspecting))
            SourceState.NothingPlaying -> DetailNote(stringResource(R.string.audio_details_nothing_playing))
            is SourceState.Ready -> {
                val s = source.diagnostics
                DetailRow(stringResource(R.string.audio_details_label_title), s.title)
                DetailRow(stringResource(R.string.audio_details_label_artist), s.artist)
                DetailRow(stringResource(R.string.audio_details_label_album), s.album)
                DetailRow(stringResource(R.string.audio_details_label_container), s.container)
                DetailRow(stringResource(R.string.audio_details_label_codec), s.codec)
                DetailRow(stringResource(R.string.audio_details_label_sample_rate), s.sampleRate)
                DetailRow(stringResource(R.string.audio_details_label_channels), s.channels)
                DetailRow(stringResource(R.string.audio_details_label_bit_depth), s.bitDepth, s.bitDepthNote)
                DetailRow(stringResource(R.string.audio_details_label_bitrate), s.bitrate)
                s.bitrateMode?.let { DetailRow(stringResource(R.string.audio_details_label_bitrate_mode), it) }
                DetailRow(stringResource(R.string.audio_details_label_duration), s.duration)
                DetailRow(stringResource(R.string.audio_details_label_file_size), s.fileSize)
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
    val locale = AppLocaleManager.activeLocale(LocalContext.current)
    DetailSection(
        title = stringResource(R.string.audio_details_playback_title),
        explanation = stringResource(R.string.audio_details_playback_explanation),
    ) {
        val state = stringResource(player.activity.labelRes())
        DetailRow(stringResource(R.string.audio_details_label_state), state, Provenance.PLAYER)
        DetailRow(
            stringResource(R.string.audio_details_label_speed),
            player.speed?.let {
                stringResource(R.string.audio_details_speed_format, LocaleFormatters.formatDecimal(it.toDouble(), 2, locale))
            },
            Provenance.PLAYER,
        )
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
        DetailRow(stringResource(R.string.audio_details_label_decoder_input), input, Provenance.PLAYER)
        DecoderRows(player, decoder)
        AudioTrackRows(player, source)
    }
}

private fun PlayerActivity.labelRes(): Int =
    when (this) {
        PlayerActivity.PLAYING -> R.string.audio_details_state_playing
        PlayerActivity.PAUSED -> R.string.audio_details_state_paused
        PlayerActivity.BUFFERING -> R.string.audio_details_state_buffering
        PlayerActivity.IDLE -> R.string.audio_details_state_idle
        PlayerActivity.ENDED -> R.string.audio_details_state_ended
    }

@Composable
private fun DecoderRows(
    player: PlayerDiagnostics,
    decoder: DecoderInfo?,
) {
    val locale = AppLocaleManager.activeLocale(LocalContext.current)
    val pending = if (player.decoderName == null) stringResource(R.string.audio_details_pending_decoder) else null
    DetailRow(stringResource(R.string.audio_details_label_decoder), decoder?.name ?: player.decoderName, Provenance.PLAYER, pending)
    decoder?.let { d ->
        val kind =
            when {
                d.hardwareAccelerated == true -> stringResource(R.string.audio_details_decoder_hw)
                d.softwareOnly == true -> stringResource(R.string.audio_details_decoder_sw)
                else -> null
            }
        val maxChannels =
            d.maxInputChannels?.let {
                pluralStringResource(R.plurals.audio_details_decoder_max_channels, it, LocaleFormatters.formatInteger(it, locale))
            }
        DetailRow(
            stringResource(R.string.audio_details_label_decoder_type),
            formatLine(kind, maxChannels),
            Provenance.PLATFORM,
        )
    }
}

/** The PCM the player configured on Android's AudioTrack, and how that output has behaved. */
@Composable
private fun AudioTrackRows(
    player: PlayerDiagnostics,
    source: SourceState,
) {
    val locale = AppLocaleManager.activeLocale(LocalContext.current)
    val track = player.audioTrack
    val sent =
        track?.let {
            formatLine(it.encoding, it.sampleRateHz?.let(MediaFormats::sampleRateText), it.channelCount?.let(MediaFormats::channelsText))
        }
    DetailRow(
        stringResource(R.string.audio_details_label_sent_audio),
        sent,
        Provenance.PLAYER,
        if (track == null) stringResource(R.string.audio_details_pending_audio) else null,
    )
    val yes = stringResource(R.string.audio_details_yes)
    val no = stringResource(R.string.audio_details_no)
    track?.let {
        DetailRow(stringResource(R.string.audio_details_label_offload), if (it.offload) yes else no, Provenance.PLAYER)
        if (it.tunneling) DetailRow(stringResource(R.string.audio_details_label_tunneling), yes, Provenance.PLAYER)
    }
    DetailRow(
        stringResource(R.string.audio_details_label_underruns),
        LocaleFormatters.formatInteger(player.underruns, locale),
        Provenance.PLAYER,
        stringResource(R.string.audio_details_underruns_note),
    )
    player.lastSinkError?.let { DetailRow(stringResource(R.string.audio_details_label_last_error), it, Provenance.PLAYER) }
    val sourceBits = ((source as? SourceState.Ready)?.diagnostics?.bitDepth?.value)?.removeSuffix("-bit")?.toIntOrNull()
    if (sourceBits != null && sourceBits > PCM_16_BITS && track?.encoding == "PCM 16-bit") {
        DetailNote(stringResource(R.string.audio_details_bit_depth_downconvert, LocaleFormatters.formatInteger(sourceBits, locale)))
    }
}

private const val PCM_16_BITS = 16
