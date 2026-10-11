package com.lochanpthakke.empee3.feature.library.transition

import com.lochanpthakke.empee3.core.model.BandWaveformLoad
import com.lochanpthakke.empee3.core.model.Song
import com.lochanpthakke.empee3.core.model.TransitionSpec
import com.lochanpthakke.empee3.core.playback.waveform.WaveformRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Keeps the edited pair's waveform windows around the cue points: when the timeline needs audio the
 * loaded window does not cover (a cue point was dragged far), it loads a new window centred on it.
 * Decoding runs on the waveform thread; a new request cancels the previous one of that lane.
 */
internal class PairWaveformLoader(
    private val waveforms: WaveformRepository,
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<PairWaveforms>,
) {
    private var outJob: Job? = null
    private var inJob: Job? = null

    fun sync(
        pair: StudioPair,
        spec: TransitionSpec,
    ) {
        val outDuration = pair.from.song.durationMs
        val inDuration = pair.to.song.durationMs
        val duration = spec.effectiveDurationMs(outDuration, inDuration).coerceAtLeast(1)
        val margin = contextFor(duration).toLong()
        val outEnd = spec.outgoingEndMs(outDuration)
        val inStart = spec.incomingStartMs.toLong()
        val waves = state.value
        if (!waves.outgoing.covers((outEnd - duration - margin).coerceAtLeast(0L), (outEnd + margin).coerceAtMost(outDuration))) {
            outJob = load(pair.from.song, outDuration, outEnd - duration / 2, outJob) { w -> copy(outgoing = w) }
        }
        if (!waves.incoming.covers((inStart - margin).coerceAtLeast(0L), (inStart + duration + margin).coerceAtMost(inDuration))) {
            inJob = load(pair.to.song, inDuration, inStart + duration / 2, inJob) { w -> copy(incoming = w) }
        }
    }

    fun cancel() {
        outJob?.cancel()
        inJob?.cancel()
    }

    private fun load(
        song: Song,
        trackMs: Long,
        centerMs: Long,
        previous: Job?,
        set: PairWaveforms.(LaneWindow) -> PairWaveforms,
    ): Job {
        previous?.cancel()
        val length = TransitionStudioViewModel.WINDOW_MS.coerceAtMost(trackMs.coerceAtLeast(1L))
        val start = (centerMs - length / 2).coerceIn(0L, (trackMs - length).coerceAtLeast(0L))
        state.update { it.set(LaneWindow(BandWaveformLoad.Loading, start, length)) }
        return scope.launch {
            waveforms.segment(song, start, start + length, TransitionStudioViewModel.WAVEFORM_BINS).collect { load ->
                state.update { it.set(LaneWindow(load, start, length)) }
            }
        }
    }
}
