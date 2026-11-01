package com.erhu.erhu.core.playback.transition

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.erhu.erhu.core.model.QueueOrigin
import com.erhu.erhu.core.model.ResolvedTransition
import com.erhu.erhu.core.playback.Cancellable
import com.erhu.erhu.core.playback.libraryDurationMs
import com.erhu.erhu.core.playback.queueOrigin
import com.erhu.erhu.core.playback.toDomainRepeatMode
import com.erhu.erhu.core.sound.dsp.CustomEqAudioProcessor
import com.erhu.erhu.core.sound.dsp.TransitionAutomation
import com.erhu.erhu.core.sound.dsp.TransitionGainProcessor
import com.erhu.erhu.core.sound.dsp.applySettings
import com.erhu.erhu.core.sound.model.SoundSettings

/** [TransitionHost] over the app's ExoPlayer. Main thread only. */
@OptIn(UnstableApi::class)
internal class PlayerTransitionHost(
    private val player: ExoPlayer,
    private val resolve: (QueueOrigin?, QueueOrigin?) -> ResolvedTransition,
    private val abLoopEnabled: () -> Boolean,
) : TransitionHost {
    private val handler = Handler(Looper.getMainLooper())
    private val window = Timeline.Window()

    override val currentEntry: Any? get() = entryAt(player.currentMediaItemIndex)

    /** The playlist entry's own uid: stable while the entry is moved, distinct for duplicates. */
    override fun entryAt(index: Int): Any? {
        val timeline = player.currentTimeline
        return if (index in 0 until timeline.windowCount) timeline.getWindow(index, window).uid else null
    }

    override val currentSource: Any? get() = player.currentMediaItem
    override val positionMs: Long get() = player.currentPosition.coerceAtLeast(0L)
    override val playWhenReady: Boolean get() =
        player.playWhenReady &&
            player.playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE
    override val speed: Float get() = player.playbackParameters.speed
    override val volume: Float get() = player.volume

    override fun boundary(): BoundaryInfo? {
        val current = player.currentMediaItem ?: return null
        val nextIndex = player.nextMediaItemIndex.takeIf { it != C.INDEX_UNSET && it in 0 until player.mediaItemCount } ?: -1
        val next = if (nextIndex >= 0) player.getMediaItemAt(nextIndex) else null
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: current.libraryDurationMs()
        return BoundaryInfo(
            // Resolved from the two entries' own origins: pair, then collection, then global default.
            spec = if (next == null) null else resolve(current.queueOrigin(), next.queueOrigin()).spec,
            nextIndex = nextIndex,
            repeatMode = player.repeatMode.toDomainRepeatMode(),
            abLoopEnabled = abLoopEnabled(),
            outgoingDurationMs = duration,
            incomingDurationMs = next?.libraryDurationMs() ?: 0L,
        )
    }

    override fun scheduleAt(
        positionMs: Long,
        action: () -> Unit,
    ): Cancellable {
        val message =
            player
                .createMessage { _, _ -> action() }
                .setLooper(Looper.getMainLooper())
                .setPosition(player.currentMediaItemIndex, positionMs)
                .setDeleteAfterDelivery(true)
                .send()
        return Cancellable { message.cancel() }
    }

    override fun postDelayed(
        delayMs: Long,
        action: () -> Unit,
    ): Cancellable {
        val runnable = Runnable(action)
        handler.postDelayed(runnable, delayMs)
        return Cancellable { handler.removeCallbacks(runnable) }
    }

    override fun advanceTo(
        index: Int,
        positionMs: Long,
    ) {
        if (index in 0 until player.mediaItemCount) player.seekTo(index, positionMs)
    }
}

/** [IncomingFade] on the main player's [TransitionGainProcessor]. */
@OptIn(UnstableApi::class)
internal class ProcessorIncomingFade(
    private val processor: TransitionGainProcessor,
) : IncomingFade {
    override val hasStarted: Boolean get() = processor.hasStarted

    override fun arm(automation: TransitionAutomation) = processor.arm(automation)

    override fun cancel() = processor.cancel()
}

/**
 * The secondary player for one transition's tail. Its own ExoPlayer with its own gain stage and an
 * EQ copy set to the user's current EQ, so the outgoing track sounds as it did in the main player.
 * It does not handle audio focus or becoming-noisy itself (the main player does, and this one
 * follows it), holds no wake lock of its own, and is released when the tail ends.
 */
@OptIn(UnstableApi::class)
internal class ExoTailPlayer(
    context: Context,
    soundSettings: SoundSettings,
) : TailPlayer {
    private val gain = TransitionGainProcessor(cancelOnLaterFlush = false)
    private val eq = CustomEqAudioProcessor().apply { applySettings(soundSettings) }
    private val player: ExoPlayer =
        ExoPlayer
            .Builder(context, renderers(context))
            .setAudioAttributes(MUSIC_ATTRIBUTES, false)
            .setHandleAudioBecomingNoisy(false)
            .build()
    private var listener: TailListener? = null
    private var audibleReported = false
    private var released = false
    private var endMessage: Cancellable? = null

    override val isReady: Boolean get() = !released && player.playbackState == Player.STATE_READY

    private fun renderers(context: Context) =
        object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink =
                DefaultAudioSink
                    .Builder(context)
                    .setAudioProcessors(arrayOf(gain, eq))
                    .build()
        }

    private val playerListener =
        object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) listener?.onFinished()
            }

            override fun onPlayerError(error: PlaybackException) {
                listener?.onError()
            }
        }

    private val analytics =
        object : AnalyticsListener {
            override fun onAudioPositionAdvancing(
                eventTime: AnalyticsListener.EventTime,
                playoutStartSystemTimeMs: Long,
            ) {
                if (!audibleReported && player.playWhenReady) {
                    audibleReported = true
                    listener?.onAudible()
                }
            }
        }

    override fun prepare(
        source: Any,
        startMs: Long,
        automation: TransitionAutomation,
        speed: Float,
        volume: Float,
        listener: TailListener,
    ) {
        // The outgoing tail is silent once its fade is over: end it there even if the track runs on.
        prepareSegment(source, startMs, automation, speed, volume, startMs + automation.leadInMs + automation.durationMs, listener)
    }

    /**
     * Prepares [source] at [startMs] (paused), shaped by [automation]; [onFinished][TailListener.onFinished]
     * fires when playback reaches [endMs] or the track ends.
     */
    fun prepareSegment(
        source: Any,
        startMs: Long,
        automation: TransitionAutomation,
        speed: Float,
        volume: Float,
        endMs: Long,
        listener: TailListener,
    ) {
        val item = source as? MediaItem ?: return listener.onError()
        this.listener = listener
        gain.arm(automation)
        player.addListener(playerListener)
        player.addAnalyticsListener(analytics)
        player.volume = volume
        player.playbackParameters = PlaybackParameters(speed)
        player.setMediaItem(item, startMs)
        player.playWhenReady = false
        player.prepare()
        val message =
            player
                .createMessage { _, _ -> this.listener?.onFinished() }
                .setLooper(Looper.getMainLooper())
                .setPosition(0, endMs + END_MARGIN_MS)
                .send()
        endMessage = Cancellable { message.cancel() }
    }

    /** Current position of this player (main thread). */
    val positionMs: Long get() = if (released) 0L else player.currentPosition

    /** Runs [action] once when this player's playback reaches [positionMs] (media time). */
    fun atPosition(
        positionMs: Long,
        action: () -> Unit,
    ): Cancellable {
        if (released) return Cancellable {}
        val message =
            player
                .createMessage { _, _ -> if (!released) action() }
                .setLooper(Looper.getMainLooper())
                .setPosition(0, positionMs)
                .send()
        return Cancellable { message.cancel() }
    }

    override fun start() {
        if (!released) player.play()
    }

    override fun setPlaying(playing: Boolean) {
        if (!released) player.playWhenReady = playing
    }

    override fun setVolume(volume: Float) {
        if (!released) player.volume = volume
    }

    override fun setSpeed(speed: Float) {
        if (!released) player.playbackParameters = PlaybackParameters(speed)
    }

    override fun release() {
        if (released) return
        released = true
        listener = null
        endMessage?.cancel()
        // The platform ramps a volume change, so stopping does not click.
        player.volume = 0f
        player.removeListener(playerListener)
        player.removeAnalyticsListener(analytics)
        player.release()
    }

    private companion object {
        const val END_MARGIN_MS = 50L
        val MUSIC_ATTRIBUTES: AudioAttributes =
            AudioAttributes
                .Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build()
    }
}
