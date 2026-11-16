package com.erhu.erhu.core.playback.di

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import com.erhu.erhu.core.playback.PlaybackController
import com.erhu.erhu.core.playback.PlaybackControllerImpl
import com.erhu.erhu.core.sound.dsp.CustomEqAudioProcessor
import com.erhu.erhu.core.sound.dsp.TransitionGainProcessor
import com.erhu.erhu.core.sound.visualizer.PlaybackAudioTap
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PlayerProviderModule {
    @OptIn(UnstableApi::class)
    @Provides
    @Singleton
    fun providePlayer(
        @ApplicationContext context: Context,
        customEqAudioProcessor: CustomEqAudioProcessor,
        audioTap: PlaybackAudioTap,
        transitionGain: TransitionGainProcessor,
        mediaSources: com.erhu.erhu.core.playback.RemoteMediaSources,
    ): Player {
        val renderersFactory =
            object : DefaultRenderersFactory(context) {
                override fun buildAudioSink(
                    context: Context,
                    enableFloatOutput: Boolean,
                    enableAudioTrackPlaybackParams: Boolean,
                ): AudioSink =
                    DefaultAudioSink
                        .Builder(context)
                        // The transition gain is unity except while a Smooth Transition fades the incoming track in.
                        // The tee only copies what is about to be played (after the EQ) to the visualizer's tap.
                        .setAudioProcessors(
                            arrayOf(transitionGain, customEqAudioProcessor, TeeAudioProcessor(VisualizerTapSink(audioTap))),
                        ).setEnableFloatOutput(enableFloatOutput)
                        .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                        .build()
            }
        val musicAttributes =
            AudioAttributes
                .Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build()
        // ExoPlayer owns audio focus (pause on loss, duck, resume after transient
        // loss) and pauses when headphones/Bluetooth disconnect. This applies to
        // every command source — app UI, notification, lock screen, widget.
        return ExoPlayer
            .Builder(context, renderersFactory)
            // Device files as before; remote tracks are resolved to their stream when opened.
            .setMediaSourceFactory(mediaSources.mediaSourceFactory())
            .setAudioAttributes(musicAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
    }

    /** The main player's transition gain stage (see TransitionCoordinator). */
    @Provides
    @Singleton
    fun provideTransitionGain(): TransitionGainProcessor = TransitionGainProcessor()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class PlaybackBindModule {
    @Binds
    @Singleton
    abstract fun bindPlaybackController(impl: PlaybackControllerImpl): PlaybackController

    /** Each remote provider adds its resolver to this set; with none connected it is empty. */
    @dagger.multibindings.Multibinds
    abstract fun remoteStreamResolvers(): Set<com.erhu.erhu.core.model.RemoteStreamResolver>

    @dagger.multibindings.Multibinds
    abstract fun remoteArtworkSources(): Set<com.erhu.erhu.core.model.RemoteArtworkSource>
}

/**
 * Feeds the visualizer's tap from the audio chain. Runs on the playback thread: the tap only copies
 * samples (when a visualizer is open), and any failure there is contained so it can never reach playback.
 */
@androidx.annotation.OptIn(UnstableApi::class)
private class VisualizerTapSink(
    private val tap: PlaybackAudioTap,
) : TeeAudioProcessor.AudioBufferSink {
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    override fun flush(
        sampleRateHz: Int,
        channelCount: Int,
        encoding: Int,
    ) {
        try {
            tap.onFormat(sampleRateHz, channelCount, pcm16 = encoding == C.ENCODING_PCM_16BIT, pcmFloat = encoding == C.ENCODING_PCM_FLOAT)
        } catch (e: RuntimeException) {
            // Never let the visualizer disturb playback.
        }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    override fun handleBuffer(buffer: java.nio.ByteBuffer) {
        try {
            tap.write(buffer)
        } catch (e: RuntimeException) {
            // Never let the visualizer disturb playback.
        }
    }
}
