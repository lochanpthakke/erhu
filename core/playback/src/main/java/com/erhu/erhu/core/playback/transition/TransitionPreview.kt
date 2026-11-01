package com.erhu.erhu.core.playback.transition

import android.content.Context
import androidx.media3.common.Player
import com.erhu.erhu.core.common.dispatcher.DispatcherProvider
import com.erhu.erhu.core.model.Song
import com.erhu.erhu.core.model.TransitionRole
import com.erhu.erhu.core.model.TransitionSpec
import com.erhu.erhu.core.playback.Cancellable
import com.erhu.erhu.core.playback.toMediaItem
import com.erhu.erhu.core.sound.dsp.TransitionAutomation
import com.erhu.erhu.core.sound.repository.SoundRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** What the editor shows about a preview. */
sealed interface PreviewState {
    data object Idle : PreviewState

    /**
     * Playing. [positionMs] is relative to the transition start (negative during the lead-in),
     * [durationMs] the effective transition duration.
     */
    data class Playing(
        val positionMs: Long,
        val durationMs: Int,
    ) : PreviewState

    data class Unavailable(
        val reason: String,
    ) : PreviewState
}

/**
 * Plays one transition on two private players, exactly as the engine would: the outgoing track from
 * a few seconds before the transition point with its automation, the incoming track started at
 * that point with its own. The user's queue, position, repeat and shuffle are never touched; if
 * the main player was playing it is paused for the preview and resumed afterwards (only if the
 * user did not start or change playback meanwhile). Main thread only.
 */
@Singleton
class TransitionPreview
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val player: Player,
        private val soundRepository: SoundRepository,
        dispatchers: DispatcherProvider,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + dispatchers.main)
        private val _state = MutableStateFlow<PreviewState>(PreviewState.Idle)
        val state: StateFlow<PreviewState> = _state.asStateFlow()

        private var outgoing: ExoTailPlayer? = null
        private var incoming: ExoTailPlayer? = null
        private var startIncoming: Cancellable? = null
        private var ticker: Job? = null
        private var resumeMain = false
        private var mainItemAtStart: Any? = null

        /** The user started the main player during a preview: the preview yields at once. */
        private val mainListener =
            object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying && outgoing != null) {
                        resumeMain = false
                        stop()
                    }
                }
            }

        fun start(
            from: Song,
            to: Song,
            spec: TransitionSpec,
        ) {
            stop()
            val clean = spec.sanitized()
            val duration = clean.effectiveDurationMs(from.durationMs, to.durationMs)
            if (from.uri.isBlank() || to.uri.isBlank() || duration < TransitionPlanner.MIN_EFFECTIVE_MS) {
                _state.value = PreviewState.Unavailable("These tracks are too short or unavailable for a preview.")
                return
            }
            val startAt = clean.outgoingEndMs(from.durationMs) - duration
            val incomingStart = clean.incomingStartMs.toLong()
            val leadIn = minOf(LEAD_IN_MS, startAt)
            resumeMain = player.playWhenReady
            mainItemAtStart = player.currentMediaItem
            if (resumeMain) player.pause()
            player.addListener(mainListener)

            val settings = soundRepository.soundSettings.value
            val outPlayer = ExoTailPlayer(context, settings).also { outgoing = it }
            val inPlayer = ExoTailPlayer(context, settings).also { incoming = it }
            val failed =
                object : TailListener {
                    override fun onAudible() = Unit

                    override fun onFinished() = Unit

                    override fun onError() = fail()
                }
            outPlayer.prepareSegment(
                from.toMediaItem(),
                startAt - leadIn,
                TransitionAutomation(TransitionRole.OUTGOING, clean, duration, leadInMs = leadIn.toInt()),
                1f,
                player.volume,
                startAt + duration,
                failed,
            )
            inPlayer.prepareSegment(
                to.toMediaItem(),
                incomingStart,
                TransitionAutomation(TransitionRole.INCOMING, clean, duration),
                1f,
                player.volume,
                incomingStart + duration + TAIL_OUT_MS,
                object : TailListener {
                    override fun onAudible() = Unit

                    override fun onFinished() = stop()

                    override fun onError() = fail()
                },
            )
            // The incoming track starts when the outgoing one reaches the transition point (media time).
            startIncoming = outPlayer.atPosition(startAt) { inPlayer.start() }
            outPlayer.start()
            _state.value = PreviewState.Playing(-leadIn, duration)
            ticker =
                scope.launch {
                    while (isActive && outgoing != null) {
                        _state.value = PreviewState.Playing(previewPosition(startAt, incomingStart), duration)
                        delay(TICK_MS)
                    }
                }
        }

        /** Stops the preview and frees both players; resumes the main player if the preview paused it. */
        fun stop() {
            ticker?.cancel()
            ticker = null
            startIncoming?.cancel()
            startIncoming = null
            outgoing?.release()
            incoming?.release()
            val hadPreview = outgoing != null
            player.removeListener(mainListener)
            outgoing = null
            incoming = null
            if (_state.value is PreviewState.Playing) _state.value = PreviewState.Idle
            if (hadPreview && shouldResumeMain()) player.play()
            resumeMain = false
            mainItemAtStart = null
        }

        /** Only if the preview paused it and the user has not changed what is playing since. */
        private fun shouldResumeMain(): Boolean = resumeMain && !player.playWhenReady && player.currentMediaItem == mainItemAtStart

        /** Time relative to the transition start: the outgoing player until the incoming one runs. */
        private fun previewPosition(
            startAt: Long,
            incomingStart: Long,
        ): Long {
            val inc = incoming?.positionMs ?: 0L
            return if (inc > incomingStart) inc - incomingStart else (outgoing?.positionMs ?: 0L) - startAt
        }

        private fun fail() {
            stop()
            _state.value = PreviewState.Unavailable("This preview could not be played (the file may be unavailable).")
        }

        private companion object {
            /** Outgoing audio heard before the transition starts. */
            const val LEAD_IN_MS = 4_000L

            /** Incoming audio heard after the transition ends. */
            const val TAIL_OUT_MS = 3_000L
            const val TICK_MS = 50L
        }
    }
