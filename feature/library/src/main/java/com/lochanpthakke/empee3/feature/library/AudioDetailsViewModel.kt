package com.lochanpthakke.empee3.feature.library

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lochanpthakke.empee3.core.common.dispatcher.DispatcherProvider
import com.lochanpthakke.empee3.core.model.Song
import com.lochanpthakke.empee3.core.playback.diagnostics.AudioOutputMonitor
import com.lochanpthakke.empee3.core.playback.diagnostics.DecoderInfo
import com.lochanpthakke.empee3.core.playback.diagnostics.DiagnosticsReport
import com.lochanpthakke.empee3.core.playback.diagnostics.DiagnosticsSnapshot
import com.lochanpthakke.empee3.core.playback.diagnostics.OutputDiagnostics
import com.lochanpthakke.empee3.core.playback.diagnostics.PlayerDiagnostics
import com.lochanpthakke.empee3.core.playback.diagnostics.PlayerDiagnosticsTracker
import com.lochanpthakke.empee3.core.playback.diagnostics.SourceDiagnostics
import com.lochanpthakke.empee3.core.playback.diagnostics.SourceInspector
import com.lochanpthakke.empee3.core.playback.repository.PlaybackRepository
import com.lochanpthakke.empee3.core.playback.transition.TransitionStageInfo
import com.lochanpthakke.empee3.core.sound.dsp.DspDiagnostics
import com.lochanpthakke.empee3.core.sound.dsp.EqStage
import com.lochanpthakke.empee3.core.sound.dsp.HeadroomEstimate
import com.lochanpthakke.empee3.core.sound.repository.SoundRepository
import com.lochanpthakke.empee3.core.sound.visualizer.PeakReading
import com.lochanpthakke.empee3.core.sound.visualizer.PlaybackAudioTap
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** The source section's state: inspecting, done, or nothing playing. */
sealed interface SourceState {
    data object Loading : SourceState

    data object NothingPlaying : SourceState

    data class Ready(
        val diagnostics: SourceDiagnostics,
    ) : SourceState
}

/**
 * The audio inspector. Everything is observed only while the screen is shown (WhileSubscribed):
 * the file is inspected once per track, the decoder is looked up once per name, the EQ state is
 * recomputed when its settings or the player's reports change, output info when Android reports a
 * device change or the player opens a new audio track, and the peak meter once a second while open.
 * Nothing here changes playback or sound settings.
 */
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class AudioDetailsViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        playbackRepository: PlaybackRepository,
        private val tracker: PlayerDiagnosticsTracker,
        private val sourceInspector: SourceInspector,
        private val outputMonitor: AudioOutputMonitor,
        private val soundRepository: SoundRepository,
        val audioTap: PlaybackAudioTap,
        private val transitionStage: TransitionStageInfo,
        private val dispatchers: DispatcherProvider,
    ) : ViewModel() {
        private val sharing = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS)

        val player: StateFlow<PlayerDiagnostics> = tracker.state

        private val currentSong: Flow<Song?> =
            playbackRepository.playbackState
                .map { it.currentSong }
                .distinctUntilChanged { a, b -> a?.id == b?.id && a?.uri == b?.uri }

        val source: StateFlow<SourceState> =
            currentSong
                .mapLatest { song -> song?.let { SourceState.Ready(sourceInspector.inspect(it)) } ?: SourceState.NothingPlaying }
                .stateIn(viewModelScope, sharing, SourceState.Loading)

        val decoder: StateFlow<DecoderInfo?> =
            tracker.state
                .map { it.decoderName }
                .distinctUntilChanged()
                .mapLatest { name -> name?.let { withContext(dispatchers.io) { PlayerDiagnosticsTracker.decoderInfo(it) } } }
                .stateIn(viewModelScope, sharing, null)

        val eq: StateFlow<EqStage> =
            combine(
                soundRepository.soundSettings,
                tracker.state,
            ) { settings, _ -> DspDiagnostics.eqStage(soundRepository.eqRuntimeState(), settings) }
                .distinctUntilChanged()
                .stateIn(
                    viewModelScope,
                    sharing,
                    DspDiagnostics.eqStage(soundRepository.eqRuntimeState(), soundRepository.soundSettings.value),
                )

        val headroom: StateFlow<HeadroomEstimate?> =
            eq
                .map { DspDiagnostics.headroom(it, soundRepository.eqRuntimeState().sampleRateHz) }
                .stateIn(viewModelScope, sharing, null)

        val output: StateFlow<OutputDiagnostics?> =
            merge(
                outputMonitor.observe(),
                // A new AudioTrack can mean a new route even without a device being added or removed.
                tracker.state
                    .map { it.audioTrack }
                    .distinctUntilChanged()
                    .map { outputMonitor.snapshot() },
            ).stateIn(viewModelScope, sharing, null)

        /** Sample peaks after the EQ, read once a second while the screen is open (the tap is held by the screen). */
        val peak: StateFlow<PeakReading?> =
            flow {
                while (true) {
                    emit(audioTap.meterReading())
                    delay(PEAK_REFRESH_MS)
                }
            }.stateIn(viewModelScope, sharing, null)

        /** Read when the chain is drawn (it changes only for the few seconds of a Smooth Transition). */
        val transitionFading: Boolean get() = transitionStage.isFading

        fun refresh() = tracker.refresh()

        fun resetPeak() = audioTap.resetMeter()

        fun report(): String =
            DiagnosticsReport.build(
                DiagnosticsSnapshot(
                    source = (source.value as? SourceState.Ready)?.diagnostics,
                    player = player.value,
                    decoder = decoder.value,
                    eq = eq.value,
                    headroom = headroom.value,
                    peak = peak.value,
                    output = output.value,
                    transitionFading = transitionStage.isFading,
                ),
            )

        /** Copies the report to the clipboard (local only; nothing is sent anywhere). */
        fun copyReport() {
            context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("empee3 audio details", report()))
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 2_000L
            const val PEAK_REFRESH_MS = 1_000L
        }
    }
