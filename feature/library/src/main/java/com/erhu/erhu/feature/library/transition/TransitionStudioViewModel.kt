package com.erhu.erhu.feature.library.transition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erhu.erhu.core.model.BandWaveformLoad
import com.erhu.erhu.core.model.CollectionRef
import com.erhu.erhu.core.model.EqMove
import com.erhu.erhu.core.model.GlobalTransitionSettings
import com.erhu.erhu.core.model.TransitionChoice
import com.erhu.erhu.core.model.TransitionConfig
import com.erhu.erhu.core.model.TransitionCurve
import com.erhu.erhu.core.model.TransitionFilter
import com.erhu.erhu.core.model.TransitionPairKey
import com.erhu.erhu.core.model.TransitionPreset
import com.erhu.erhu.core.model.TransitionResolver
import com.erhu.erhu.core.model.TransitionRole
import com.erhu.erhu.core.model.TransitionSpec
import com.erhu.erhu.core.model.VolumeShape
import com.erhu.erhu.core.playback.repository.PlaybackRepository
import com.erhu.erhu.core.playback.transition.PreviewState
import com.erhu.erhu.core.playback.transition.TransitionPreview
import com.erhu.erhu.core.playback.transition.TransitionSettingsRepository
import com.erhu.erhu.core.playback.transition.specOrNull
import com.erhu.erhu.core.playback.waveform.WaveformRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/** The collection a Studio is open for, with its tracks in timeline order. */
data class StudioInput(
    val collection: CollectionRef,
    val title: String,
    val tracks: List<StudioTrack>,
) {
    fun pair(
        key: TransitionPairKey,
        config: TransitionConfig,
    ): StudioPair? = studioPairs(collection, tracks, config).firstOrNull { it.key == key }
}

data class StudioUi(
    val input: StudioInput,
    val global: GlobalTransitionSettings,
    val collectionChoice: TransitionChoice,
    /** What this collection's pairs inherit (its own setting, or the global default); null: no crossfade. */
    val collectionDefault: TransitionSpec?,
    val pairs: List<StudioPair>,
    /** Whether this collection is what is playing now. */
    val isPlaying: Boolean,
    val collectionEnabled: Boolean,
)

/** What the editor is editing. */
sealed interface EditorTarget {
    data object Global : EditorTarget

    data class Collection(
        val collection: CollectionRef,
    ) : EditorTarget

    data class Pair(
        val pair: StudioPair,
    ) : EditorTarget
}

/** A preset card in the editor. */
sealed interface PresetChoice {
    data object Auto : PresetChoice

    data object Off : PresetChoice

    data class Preset(
        val preset: TransitionPreset,
    ) : PresetChoice
}

/** Whether the edited level inherits, is off, or has its own transition. */
enum class EditorMode { INHERIT, OFF, CUSTOM }

/** Unsaved editor state. Nothing is written until Save. */
data class EditorDraft(
    val target: EditorTarget,
    val mode: EditorMode,
    /** The custom transition being edited (kept while another mode is selected, so switching back loses nothing). */
    val spec: TransitionSpec,
    /** What the level would play when it inherits. */
    val inherited: TransitionSpec?,
    val original: TransitionChoice,
    val undo: List<Pair<EditorMode, TransitionSpec>> = emptyList(),
) {
    /** What an edit starts from: the inherited transition while inheriting (so "tweak Auto" works), else this level's own. */
    fun editBase(): TransitionSpec = if (mode == EditorMode.INHERIT) inherited ?: spec else spec

    /** What plays with the current choice (null: no transition). */
    val shown: TransitionSpec?
        get() =
            when (mode) {
                EditorMode.INHERIT -> inherited
                EditorMode.OFF -> null
                EditorMode.CUSTOM -> spec
            }
    val choice: TransitionChoice
        get() =
            when (mode) {
                EditorMode.INHERIT -> TransitionChoice.Inherit
                EditorMode.OFF -> TransitionChoice.Off
                EditorMode.CUSTOM -> TransitionChoice.Custom(spec)
            }

    val isDirty: Boolean get() = choice != original
}

/** A loaded waveform window of one track: [startMs] and [lengthMs] in track time. */
data class LaneWindow(
    val load: BandWaveformLoad = BandWaveformLoad.Loading,
    val startMs: Long = 0L,
    val lengthMs: Long = 0L,
) {
    fun covers(
        fromMs: Long,
        toMs: Long,
    ): Boolean = lengthMs > 0 && fromMs >= startMs && toMs <= startMs + lengthMs
}

/** Waveform windows of the edited pair, around the outgoing and incoming cue points. */
data class PairWaveforms(
    val outgoing: LaneWindow = LaneWindow(),
    val incoming: LaneWindow = LaneWindow(),
)

/**
 * Transition Studio for one album or playlist: global switch and default, this collection's
 * setting, the timeline of pairs, and the editor. Edits live here until Save; Save writes once.
 */
@HiltViewModel
@Suppress("TooManyFunctions") // Existing editor actions stay together; the collection switch shares their state.
class TransitionStudioViewModel
    @Inject
    constructor(
        private val settings: TransitionSettingsRepository,
        private val playbackRepository: PlaybackRepository,
        private val waveforms: WaveformRepository,
        private val preview: TransitionPreview,
    ) : ViewModel() {
        private val input = MutableStateFlow<StudioInput?>(null)

        val ui: StateFlow<StudioUi?> =
            combine(
                input,
                settings.config,
                playbackRepository.playbackState.map { it.currentCollection }.distinctUntilChanged(),
            ) { input, config, playing ->
                input?.let { buildUi(it, config, playing) }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

        private val _draft = MutableStateFlow<EditorDraft?>(null)
        val draft: StateFlow<EditorDraft?> = _draft.asStateFlow()

        private val _pairWaveforms = MutableStateFlow(PairWaveforms())
        val pairWaveforms: StateFlow<PairWaveforms> = _pairWaveforms.asStateFlow()
        private val laneLoader = PairWaveformLoader(waveforms, viewModelScope, _pairWaveforms)

        val previewState: StateFlow<PreviewState> = preview.state

        /** All transition settings, for the entry rows on album and playlist pages. */
        val config: StateFlow<TransitionConfig> = settings.config

        fun open(
            input: StudioInput,
            initialPair: TransitionPairKey? = null,
        ) {
            this.input.value = input
            initialPair?.let { key ->
                input
                    .pair(key, settings.config.value)
                    ?.let { openEditor(EditorTarget.Pair(it)) }
            }
        }

        fun setCollectionEnabled(
            collection: CollectionRef,
            enabled: Boolean,
        ) = settings.setCollectionEnabled(collection, enabled)

        fun close() {
            cancelEditor()
            input.value = null
        }

        // Global level

        fun setMasterEnabled(enabled: Boolean) =
            settings.setGlobal(
                settings.config.value.global
                    .copy(masterEnabled = enabled),
            )

        // Collection and pair quick actions

        fun setCollectionChoice(choice: TransitionChoice) {
            val collection = input.value?.collection ?: return
            settings.setCollection(collection, choice)
        }

        fun resetPair(key: TransitionPairKey) = settings.setPair(key, TransitionChoice.Inherit)

        // Editor

        fun openEditor(target: EditorTarget) {
            val config = settings.config.value
            val (original, inherited) =
                when (target) {
                    EditorTarget.Global -> config.global.default to null
                    is EditorTarget.Collection -> config.collection(target.collection) to config.global.default.specOrNull()
                    is EditorTarget.Pair ->
                        config.pair(target.pair.key) to
                            TransitionResolver.collectionDefault(config, target.pair.key.collection)
                }
            val mode =
                when (original) {
                    TransitionChoice.Inherit -> EditorMode.INHERIT
                    TransitionChoice.Off -> EditorMode.OFF
                    is TransitionChoice.Custom -> EditorMode.CUSTOM
                }
            val spec = original.specOrNull() ?: inherited ?: TransitionSpec()
            _draft.value = EditorDraft(target, mode, spec, inherited, original)
            _pairWaveforms.value = PairWaveforms()
            if (target is EditorTarget.Pair) refreshWaveforms() else laneLoader.cancel()
        }

        /** Slides a track under the transition: moves the outgoing hand-over point or the incoming start. */
        fun shiftCue(
            role: TransitionRole,
            deltaMs: Float,
        ) {
            val pair = (_draft.value?.target as? EditorTarget.Pair)?.pair ?: return
            drag { TransitionEditing.shiftCue(it, role, deltaMs, pair.from.song.durationMs, pair.to.song.durationMs) }
        }

        fun resetCues() = edit { it.copy(outgoingCutMs = 0, incomingStartMs = 0) }

        /** A preset card: Auto (inherit), Off, or a preset, which becomes this level's own transition. */
        fun choose(choice: PresetChoice) =
            record { draft ->
                when (choice) {
                    PresetChoice.Auto -> draft.copy(mode = EditorMode.INHERIT)
                    PresetChoice.Off -> draft.copy(mode = EditorMode.OFF)
                    is PresetChoice.Preset ->
                        draft.copy(mode = EditorMode.CUSTOM, spec = TransitionEditing.applyPreset(draft.editBase(), choice.preset))
                }
            }

        /** One discrete edit (a tap, or the end of a drag), recorded for undo; it makes the level custom. */
        fun edit(transform: (TransitionSpec) -> TransitionSpec) =
            record { draft -> draft.copy(mode = EditorMode.CUSTOM, spec = transform(draft.editBase()).sanitized()) }

        private fun record(change: (EditorDraft) -> EditorDraft) =
            _draft.update { draft ->
                draft?.let {
                    val next = change(it)
                    if (next.mode == it.mode &&
                        next.spec == it.spec
                    ) {
                        it
                    } else {
                        next.copy(undo = (it.undo + (it.mode to it.spec)).takeLast(UNDO_LIMIT))
                    }
                }
            }

        /** A drag in progress: updates without filling the undo history; [beginDrag] recorded the state before it. */
        fun drag(transform: (TransitionSpec) -> TransitionSpec) =
            _draft.update { draft -> draft?.copy(mode = EditorMode.CUSTOM, spec = transform(draft.editBase())) }

        fun beginDrag() = _draft.update { it?.copy(undo = (it.undo + (it.mode to it.spec)).takeLast(UNDO_LIMIT)) }

        fun undo() =
            _draft.update { draft ->
                draft?.undo?.lastOrNull()?.let { (mode, spec) -> draft.copy(mode = mode, spec = spec, undo = draft.undo.dropLast(1)) }
                    ?: draft
            }

        fun setDuration(durationMs: Int) = edit { TransitionEditing.setDuration(it, durationMs) }

        fun setVolumeShape(shape: VolumeShape) = edit { TransitionEditing.setVolumeShape(it, shape) }

        /** Turns the volume curves into editable points (the shape is kept as the starting point). */
        fun customizeVolume() = edit { TransitionEditing.setCurve(it, TransitionCurve.CUSTOM) }

        fun setEqMove(move: EqMove) = edit { TransitionEditing.setEqMove(it, move) }

        fun setBassSwapAt(at: Float) = drag { TransitionEditing.setBassSwapAt(it, at) }

        fun setFilter(filter: TransitionFilter) = edit { TransitionEditing.setFilter(it, filter) }

        fun movePoint(
            role: TransitionRole,
            index: Int,
            t: Float,
            gain: Float,
        ) = drag { TransitionEditing.movePoint(it, role, index, t, gain) }

        fun addPoint(
            role: TransitionRole,
            t: Float,
        ) = edit { TransitionEditing.addPoint(it, role, t) }

        fun removePoint(
            role: TransitionRole,
            index: Int,
        ) = edit { TransitionEditing.removePoint(it, role, index) }

        /** Back to the current preset's own settings (or Fade for a custom one). */
        fun resetToPreset() =
            edit { spec ->
                val preset = if (spec.preset == TransitionPreset.CUSTOM) TransitionPreset.FADE else spec.preset
                TransitionEditing.applyPreset(spec, preset)
            }

        fun save() {
            val draft = _draft.value ?: return
            when (val target = draft.target) {
                EditorTarget.Global -> {
                    val default = if (draft.mode == EditorMode.CUSTOM) TransitionChoice.Custom(draft.spec) else TransitionChoice.Off
                    settings.setGlobal(
                        settings.config.value.global
                            .copy(default = default),
                    )
                }
                is EditorTarget.Collection -> settings.setCollection(target.collection, draft.choice)
                is EditorTarget.Pair -> settings.setPair(target.pair.key, draft.choice)
            }
            cancelEditor()
        }

        /** Discards unsaved edits. */
        fun cancelEditor() {
            preview.stop()
            laneLoader.cancel()
            _draft.value = null
        }

        /** Stops a running preview, or previews what the editor shows now (Auto: the inherited transition; nothing for Off). */
        fun togglePreview() {
            if (preview.state.value is PreviewState.Playing) return preview.stop()
            val draft = _draft.value
            val pair = (draft?.target as? EditorTarget.Pair)?.pair
            val spec =
                when (draft?.mode) {
                    EditorMode.INHERIT -> draft.inherited
                    EditorMode.CUSTOM -> draft.spec
                    else -> null
                }
            if (pair != null && spec != null) preview.start(pair.from.song, pair.to.song, spec)
        }

        /** Loads waveform windows if the cue points moved off what is loaded (after a drag, an edit or undo). */
        fun refreshWaveforms() {
            val draft = _draft.value
            val pair = (draft?.target as? EditorTarget.Pair)?.pair ?: return
            laneLoader.sync(pair, draft.shown ?: draft.editBase())
        }

        override fun onCleared() {
            preview.stop()
        }

        companion object {
            const val STOP_TIMEOUT_MS = 5_000L
            const val UNDO_LIMIT = 30

            /** Audio shown around the transition window (each side). */
            const val CONTEXT_MS = 8_000

            /** Waveform windows: 40 s of a track at 25 ms per bin, re-centred when a cue point moves out of it. */
            const val WAVEFORM_BINS = 1_600
            const val WINDOW_MS = 40_000L

            fun buildUi(
                input: StudioInput,
                config: TransitionConfig,
                playing: CollectionRef?,
            ) = StudioUi(
                input = input,
                global = config.global,
                collectionChoice = config.collection(input.collection),
                collectionDefault = TransitionResolver.collectionDefault(config, input.collection),
                pairs = studioPairs(input.collection, input.tracks, config),
                isPlaying = playing == input.collection,
                collectionEnabled = config.collectionEnabled(input.collection),
            )
        }
    }
