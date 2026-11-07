package com.erhu.erhu.feature.library.vybe

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erhu.erhu.core.database.repository.LibraryRepository
import com.erhu.erhu.core.model.Song
import com.erhu.erhu.core.playback.repository.PlaybackRepository
import com.erhu.erhu.core.vybe.EntityKind
import com.erhu.erhu.core.vybe.VybeAnswer
import com.erhu.erhu.core.vybe.VybeHit
import com.erhu.erhu.core.vybe.VybeReference
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One result row: the library song and why VYBE picked it. */
data class VybeResult(
    val song: Song,
    val reasons: List<String>,
)

/** What the results answer, for the header above them. */
data class VybeResults(
    val title: String,
    val note: String?,
    val songs: List<VybeResult>,
    val references: List<VybeReference> = emptyList(),
    val waitingForIndex: Boolean = false,
)

/** The screen's query state. */
sealed interface VybeQueryState {
    data object Idle : VybeQueryState

    data class Searching(
        val query: String,
    ) : VybeQueryState

    data class Done(
        val query: String,
        val results: VybeResults,
    ) : VybeQueryState

    data class Error(
        val query: String,
        val message: String,
    ) : VybeQueryState
}

@HiltViewModel
class VybeViewModel
    @Inject
    constructor(
        private val engine: VybeEngine,
        private val libraryRepository: LibraryRepository,
        private val playbackRepository: PlaybackRepository,
    ) : ViewModel() {
        val status: StateFlow<VybeStatus> = engine.status
        val timings: StateFlow<VybeTimings> = engine.timings

        private val _query = MutableStateFlow<VybeQueryState>(VybeQueryState.Idle)
        val query: StateFlow<VybeQueryState> = _query.asStateFlow()

        private val _message = MutableStateFlow<String?>(null)
        val message: StateFlow<String?> = _message.asStateFlow()

        /** The playing song, for "Like what's playing". */
        val currentSong: StateFlow<Song?> =
            playbackRepository.playbackState
                .map { it.currentSong }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

        private var running: Job? = null
        private var submitted: String? = null
        private var selections: Map<Int, String> = emptyMap()

        init {
            viewModelScope.launch {
                engine.catalogVersion.drop(1).collect {
                    if (_query.value is VybeQueryState.Done) submitted?.let(::searchSubmitted)
                }
            }
        }

        private fun searchSubmitted(text: String) {
            val selected = selections
            run(text) { engine.search(text, RESULT_LIMIT, selected) }
        }

        fun chooseReference(
            referenceId: Int,
            matchId: String,
        ) {
            val done = _query.value as? VybeQueryState.Done ?: return
            if (done.results.references.any { it.id == referenceId && it.matches.any { match -> match.id == matchId } }) {
                selections = selections + (referenceId to matchId)
                searchSubmitted(done.query)
            }
        }

        /** Loads the model and indexes, if not already (VYBE was opened). */
        fun start() = engine.start()

        fun setVisible(visible: Boolean) = engine.setScreenVisible(visible)

        fun importModel(uri: Uri) {
            viewModelScope.launch {
                engine.importModel(uri).onFailure { _message.value = "The model couldn't be copied: ${it.message}" }
            }
        }

        /** Runs [text] (submitted, never per keystroke). */
        fun submit(text: String) {
            val query = text.trim()
            if (query.isEmpty()) return
            submitted = query
            selections = emptyMap()
            searchSubmitted(query)
        }

        /** Songs like the playing one. */
        fun similarToCurrent() {
            val song = currentSong.value ?: return
            submitted = null
            selections = emptyMap()
            run("Like \"${song.title}\"") { engine.similarTo(song.id, RESULT_LIMIT) }
        }

        fun clear() {
            running?.cancel()
            submitted = null
            selections = emptyMap()
            _query.value = VybeQueryState.Idle
        }

        fun play(
            results: List<VybeResult>,
            index: Int,
        ) {
            if (results.isEmpty()) return
            playbackRepository.setShuffleModeEnabled(false)
            playbackRepository.playCollection(results.map { it.song }, index.coerceIn(results.indices), null)
        }

        fun shuffle(results: List<VybeResult>) {
            if (results.isEmpty()) return
            playbackRepository.setShuffleModeEnabled(true)
            playbackRepository.playCollection(results.map { it.song }, results.indices.random(), null)
        }

        fun addAllToQueue(results: List<VybeResult>) {
            viewModelScope.launch {
                results.forEach { playbackRepository.addToQueue(it.song) }
                _message.value = "Added ${results.size} songs to the queue"
            }
        }

        fun consumeMessage() {
            _message.value = null
        }

        private fun run(
            label: String,
            answer: suspend () -> VybeAnswer?,
        ) {
            running?.cancel()
            _query.value = VybeQueryState.Searching(label)
            running =
                viewModelScope.launch {
                    _query.value =
                        try {
                            val result = answer()
                            if (result == null) {
                                VybeQueryState.Error(label, "VYBE isn't ready yet")
                            } else if (result is VybeAnswer.Described && result.error != null) {
                                VybeQueryState.Error(label, result.error!!)
                            } else {
                                VybeQueryState.Done(label, present(result))
                            }
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (
                            @Suppress("TooGenericExceptionCaught") e: Exception,
                        ) {
                            VybeQueryState.Error(label, e.message ?: "The search failed")
                        }
                }
        }

        /** Turns an answer into rows of library songs, with a header that says what was matched how. */
        private suspend fun present(answer: VybeAnswer): VybeResults =
            when (answer) {
                is VybeAnswer.Named -> {
                    val named = songs(answer.songIds.map { VybeHit(it, 1f, listOf(exactReason(answer.kind))) })
                    val similar = songs(answer.similar)
                    VybeResults(
                        title = "${kindLabel(answer.kind)}: ${answer.name}",
                        note =
                            listOfNotNull(
                                "Found by name in your library.",
                                "Waiting for the selected reference to finish indexing.".takeIf { answer.waitingForIndex },
                                answer.filters.takeIf { it.isNotEmpty() }?.joinToString(" · "),
                            ).joinToString(" "),
                        songs = named + similar,
                        waitingForIndex = answer.waitingForIndex,
                    )
                }
                is VybeAnswer.Described ->
                    VybeResults(
                        title = "Closest in your library",
                        note =
                            listOfNotNull(
                                answer.unknownName?.let { "\"$it\" isn't in your library, so this is matched as a description." },
                                "Matched against each song's genres, tempo and artist; there is no mood or audio analysis.",
                                WEAK_NOTE.takeIf { answer.weak },
                                answer.filters.takeIf { it.isNotEmpty() }?.joinToString(" · "),
                            ).joinToString(" "),
                        songs = songs(answer.hits),
                    )
                is VybeAnswer.References ->
                    VybeResults(
                        title = if (answer.wantsSimilar) "Like your references" else "Your matches",
                        note =
                            (
                                answer.references.map { reference ->
                                    when {
                                        reference.filteredOut -> reference.text + ": excluded by your filters."
                                        reference.selected != null -> "Matched: " + reference.selected!!.label
                                        reference.matches.isEmpty() -> "Not found: " + reference.text
                                        else -> "Choose a match for " + reference.text
                                    }
                                } + answer.filters +
                                    listOfNotNull(
                                        "Waiting for all selected references to finish indexing.".takeIf { answer.waitingForIndex },
                                    )
                            ).joinToString("\n"),
                        songs = songs(answer.hits),
                        references = answer.references,
                        waitingForIndex = answer.waitingForIndex,
                    )
                is VybeAnswer.SimilarTo ->
                    VybeResults(
                        title = "Like what's playing",
                        note = "Closest songs by genre, tempo and artist.",
                        songs = songs(answer.hits),
                    )
            }

        private suspend fun songs(hits: List<VybeHit>): List<VybeResult> {
            val byId = libraryRepository.getSongsByIds(hits.map { it.songId }).associateBy { it.id }
            return hits.mapNotNull { hit -> byId[hit.songId]?.let { VybeResult(it, hit.reasons) } }
        }

        private fun exactReason(kind: EntityKind) = "${kindLabel(kind)} match"

        private fun kindLabel(kind: EntityKind) =
            when (kind) {
                EntityKind.ARTIST -> "Artist"
                EntityKind.ALBUM -> "Album"
                EntityKind.TITLE -> "Title"
            }

        private companion object {
            const val RESULT_LIMIT = 30
            const val WEAK_NOTE = "Your library's tags barely tell songs apart for this, so the order says little."
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
