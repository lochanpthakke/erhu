package com.lochanpthakke.empee3.core.playback.diagnostics

import android.media.MediaCodecList
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioSink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the player itself reports about the current audio, from Media3's analytics callbacks: the
 * decoder's input format, the decoder's name when it initializes, the format the audio sink
 * configured on the Android AudioTrack (after the app's processing), offload, underruns, speed and
 * state. The listener only stores a few fields when Media3 reports a change (no polling, nothing on
 * the audio path), and is kept for the player's lifetime so values reported before the inspector
 * opened (the decoder starts with the track) are not lost. Any failure here stays here.
 */
@Singleton
@OptIn(UnstableApi::class)
class PlayerDiagnosticsTracker
    @Inject
    constructor(
        private val player: Player,
    ) {
        private val _state = MutableStateFlow(PlayerDiagnostics())
        val state: StateFlow<PlayerDiagnostics> = _state.asStateFlow()

        internal val analyticsListener =
            object : AnalyticsListener {
                override fun onAudioInputFormatChanged(
                    eventTime: AnalyticsListener.EventTime,
                    format: Format,
                    decoderReuseEvaluation: DecoderReuseEvaluation?,
                ) = record { it.copy(inputFormat = MediaFormats.summarize(format)) }

                override fun onAudioDecoderInitialized(
                    eventTime: AnalyticsListener.EventTime,
                    decoderName: String,
                    initializedTimestampMs: Long,
                    initializationDurationMs: Long,
                ) = record { it.copy(decoderName = decoderName) }

                override fun onAudioTrackInitialized(
                    eventTime: AnalyticsListener.EventTime,
                    audioTrackConfig: AudioSink.AudioTrackConfig,
                ) = record { it.copy(audioTrack = summarize(audioTrackConfig)) }

                override fun onAudioTrackReleased(
                    eventTime: AnalyticsListener.EventTime,
                    audioTrackConfig: AudioSink.AudioTrackConfig,
                ) = record { it.copy(audioTrack = null) }

                override fun onAudioUnderrun(
                    eventTime: AnalyticsListener.EventTime,
                    bufferSize: Int,
                    bufferSizeMs: Long,
                    elapsedSinceLastFeedMs: Long,
                ) = record { it.copy(underruns = it.underruns + 1) }

                override fun onAudioSinkError(
                    eventTime: AnalyticsListener.EventTime,
                    audioSinkError: Exception,
                ) = record { it.copy(lastSinkError = audioSinkError.javaClass.simpleName) }

                override fun onPlaybackParametersChanged(
                    eventTime: AnalyticsListener.EventTime,
                    playbackParameters: PlaybackParameters,
                ) = record { it.copy(speed = playbackParameters.speed) }

                override fun onEvents(
                    player: Player,
                    events: AnalyticsListener.Events,
                ) = record { it.copy(activity = activityOf(player)) }
            }

        private var started = false

        /**
         * Starts listening (main thread). Called when playback is set up, so the decoder reported at the
         * start of a track is known even if the inspector is opened later. Idempotent.
         */
        fun start() {
            if (started) return
            started = true
            (player as? ExoPlayer)?.let { exo ->
                exo.addAnalyticsListener(analyticsListener)
                // What is already known if playback started before this tracker existed.
                exo.audioFormat?.let { format -> _state.update { it.copy(inputFormat = MediaFormats.summarize(format)) } }
            }
            _state.update { it.copy(activity = activityOf(player), speed = player.playbackParameters.speed) }
        }

        /** Stops listening (the player is going away). */
        fun stop() {
            if (!started) return
            started = false
            (player as? ExoPlayer)?.removeAnalyticsListener(analyticsListener)
        }

        /** Re-reads the cheap player getters (state, speed, format) on demand, e.g. when the inspector opens. */
        fun refresh() =
            record { current ->
                val exo = player as? ExoPlayer
                current.copy(
                    activity = activityOf(player),
                    speed = player.playbackParameters.speed,
                    inputFormat = exo?.audioFormat?.let(MediaFormats::summarize) ?: current.inputFormat,
                )
            }

        @Suppress("TooGenericExceptionCaught", "SwallowedException")
        private fun record(change: (PlayerDiagnostics) -> PlayerDiagnostics) {
            try {
                _state.update(change)
            } catch (e: RuntimeException) {
                // Diagnostics never disturb playback.
            }
        }

        companion object {
            fun activityOf(player: Player): PlayerActivity =
                when {
                    player.isPlaying -> PlayerActivity.PLAYING
                    player.playbackState == Player.STATE_BUFFERING -> PlayerActivity.BUFFERING
                    player.playbackState == Player.STATE_ENDED -> PlayerActivity.ENDED
                    player.playbackState == Player.STATE_IDLE -> PlayerActivity.IDLE
                    else -> PlayerActivity.PAUSED
                }

            fun summarize(config: AudioSink.AudioTrackConfig): AudioTrackSummary {
                val channels = MediaFormats.channelsInMask(config.channelConfig)
                return AudioTrackSummary(
                    encoding = MediaFormats.encodingName(config.encoding),
                    sampleRateHz = config.sampleRate.takeIf { it > 0 },
                    channelCount = channels,
                    offload = config.offload,
                    tunneling = config.tunneling,
                    bufferBytes = config.bufferSize.takeIf { it > 0 },
                )
            }

            /** Android's description of a codec, by the name Media3 reported. Call off the main thread. */
            @Suppress("TooGenericExceptionCaught", "SwallowedException")
            fun decoderInfo(name: String): DecoderInfo =
                try {
                    val codec = MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.firstOrNull { it.name == name }
                    val type = codec?.supportedTypes?.firstOrNull { it.startsWith("audio/") }
                    val capabilities = type?.let { codec.getCapabilitiesForType(it).audioCapabilities }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && codec != null) {
                        DecoderInfo(
                            name,
                            codec.isHardwareAccelerated,
                            codec.isSoftwareOnly,
                            codec.isVendor,
                            capabilities?.maxInputChannelCount,
                        )
                    } else {
                        DecoderInfo(name, maxInputChannels = capabilities?.maxInputChannelCount)
                    }
                } catch (e: RuntimeException) {
                    DecoderInfo(name)
                }
        }
    }
