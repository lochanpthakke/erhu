package com.erhu.erhu.feature.library.vybe

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erhu.erhu.core.common.locale.AppLocaleManager
import com.erhu.erhu.core.common.locale.LocaleFormatters
import com.erhu.erhu.core.database.repository.LibraryRepository
import com.erhu.erhu.core.model.Song
import com.erhu.erhu.core.playback.repository.PlaybackRepository
import com.erhu.erhu.core.vybe.EntityKind
import com.erhu.erhu.core.vybe.VybeAnswer
import com.erhu.erhu.core.vybe.VybeHit
import com.erhu.erhu.core.vybe.VybeReference
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
import com.erhu.erhu.core.common.R as CommonR

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
        @param:ApplicationContext private val appContext: Context,
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

        private fun localized(): Context = AppLocaleManager.localizedContext(appContext)

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
                engine.importModel(uri).onFailure { error ->
                    _message.value =
                        localized().getString(
                            CommonR.string.vybe_error_copy_model,
                            error.message.orEmpty(),
                        )
                }
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
            run(localized().getString(CommonR.string.vybe_like_song, song.title)) { engine.similarTo(song.id, RESULT_LIMIT) }
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
                val context = localized()
                val locale = AppLocaleManager.activeLocale(context)
                _message.value =
                    context.resources.getQuantityString(
                        CommonR.plurals.vybe_added_songs_to_queue,
                        results.size,
                        LocaleFormatters.formatInteger(results.size, locale),
                    )
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
                    val context = localized()
                    _query.value =
                        try {
                            val result = answer()
                            if (result == null) {
                                VybeQueryState.Error(label, context.getString(CommonR.string.vybe_error_not_ready))
                            } else if (result is VybeAnswer.Described && result.error != null) {
                                VybeQueryState.Error(label, localizeVybeError(context, result.error!!))
                            } else {
                                VybeQueryState.Done(label, present(result, context))
                            }
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (
                            @Suppress("TooGenericExceptionCaught") e: Exception,
                        ) {
                            VybeQueryState.Error(label, e.message ?: context.getString(CommonR.string.vybe_error_search_failed))
                        }
                }
        }

        /** Turns an answer into rows of library songs, with a header that says what was matched how. */
        private suspend fun present(
            answer: VybeAnswer,
            context: Context,
        ): VybeResults =
            when (answer) {
                is VybeAnswer.Named -> {
                    val named = songs(answer.songIds.map { VybeHit(it, 1f, listOf(exactReason(context, answer.kind))) }, context)
                    val similar = songs(answer.similar, context)
                    VybeResults(
                        title = context.getString(CommonR.string.vybe_result_named_title, kindLabel(context, answer.kind), answer.name),
                        note =
                            listOfNotNull(
                                context.getString(CommonR.string.vybe_result_found_by_name),
                                context.getString(CommonR.string.vybe_note_waiting_selected_ref).takeIf { answer.waitingForIndex },
                                answer.filters.takeIf { it.isNotEmpty() }?.joinToString(" · "),
                            ).joinToString(" "),
                        songs = named + similar,
                        waitingForIndex = answer.waitingForIndex,
                    )
                }
                is VybeAnswer.Described ->
                    VybeResults(
                        title = context.getString(CommonR.string.vybe_result_closest_title),
                        note =
                            listOfNotNull(
                                answer.unknownName?.let { context.getString(CommonR.string.vybe_note_unknown_name, it) },
                                context.getString(CommonR.string.vybe_note_matched_against),
                                context.getString(CommonR.string.vybe_note_weak).takeIf { answer.weak },
                                answer.filters.takeIf { it.isNotEmpty() }?.joinToString(" · "),
                            ).joinToString(" "),
                        songs = songs(answer.hits, context),
                    )
                is VybeAnswer.References -> presentReferences(answer, context)
                is VybeAnswer.SimilarTo ->
                    VybeResults(
                        title = context.getString(CommonR.string.vybe_title_like_playing),
                        note = context.getString(CommonR.string.vybe_note_similar_to_playing),
                        songs = songs(answer.hits, context),
                    )
            }

        private suspend fun presentReferences(
            answer: VybeAnswer.References,
            context: Context,
        ): VybeResults =
            VybeResults(
                title =
                    context.getString(
                        if (answer.wantsSimilar) {
                            CommonR.string.vybe_title_like_references
                        } else {
                            CommonR.string.vybe_title_your_matches
                        },
                    ),
                note =
                    (
                        answer.references.map { reference ->
                            when {
                                reference.filteredOut ->
                                    context.getString(CommonR.string.vybe_ref_excluded, reference.text)
                                reference.selected != null ->
                                    context.getString(CommonR.string.vybe_ref_matched, reference.selected!!.label)
                                reference.matches.isEmpty() ->
                                    context.getString(CommonR.string.vybe_ref_not_found, reference.text)
                                else ->
                                    context.getString(CommonR.string.vybe_choose_match_for, reference.text)
                            }
                        } + answer.filters +
                            listOfNotNull(
                                context.getString(CommonR.string.vybe_note_waiting_all_refs).takeIf { answer.waitingForIndex },
                            )
                    ).joinToString("\n"),
                songs = songs(answer.hits, context),
                references = answer.references,
                waitingForIndex = answer.waitingForIndex,
            )

        private suspend fun songs(
            hits: List<VybeHit>,
            context: Context,
        ): List<VybeResult> {
            val byId = libraryRepository.getSongsByIds(hits.map { it.songId }).associateBy { it.id }
            return hits.mapNotNull { hit ->
                byId[hit.songId]?.let { song ->
                    VybeResult(song, hit.reasons.map { localizeVybeReason(context, it) })
                }
            }
        }

        private fun exactReason(
            context: Context,
            kind: EntityKind,
        ) = context.getString(CommonR.string.vybe_reason_kind_match, kindLabel(context, kind))

        private fun kindLabel(
            context: Context,
            kind: EntityKind,
        ) = context.getString(
            when (kind) {
                EntityKind.ARTIST -> CommonR.string.vybe_kind_artist
                EntityKind.ALBUM -> CommonR.string.vybe_kind_album
                EntityKind.TITLE -> CommonR.string.vybe_kind_title
            },
        )

        private fun localizeVybeError(
            context: Context,
            error: String,
        ): String =
            when {
                error == ERROR_QUERY_TOO_LONG -> context.getString(CommonR.string.vybe_error_query_too_long)
                error == ERROR_INVALID_RANGE -> context.getString(CommonR.string.vybe_error_invalid_filter_range)
                error.startsWith(PREFIX_UNKNOWN_EXCLUSION) ->
                    context.getString(
                        CommonR.string.vybe_error_unknown_exclusion,
                        error.removePrefix(PREFIX_UNKNOWN_EXCLUSION),
                    )
                else -> error
            }

        private fun localizeVybeReason(
            context: Context,
            reason: String,
        ): String =
            when {
                reason == REASON_SELECTED_REF -> context.getString(CommonR.string.vybe_reason_selected_reference)
                reason == REASON_SIMILAR_ALL_REFS -> context.getString(CommonR.string.vybe_reason_similar_all_refs)
                reason == REASON_SIMILAR_GENRE_TEMPO -> context.getString(CommonR.string.vybe_reason_similar_genre_tempo)
                reason.startsWith(PREFIX_ARTIST_BOOST) ->
                    context.getString(
                        CommonR.string.vybe_reason_artist_boost,
                        reason.removePrefix(PREFIX_ARTIST_BOOST),
                    )
                reason.startsWith(PREFIX_GENRE_BOOST) ->
                    context.getString(
                        CommonR.string.vybe_reason_genre_boost,
                        reason.removePrefix(PREFIX_GENRE_BOOST),
                    )
                reason.startsWith(PREFIX_TEMPO_BOOST) && reason.endsWith(SUFFIX_TEMPO_BOOST) -> {
                    val rawBpm = reason.removePrefix(PREFIX_TEMPO_BOOST).removeSuffix(SUFFIX_TEMPO_BOOST)
                    val formatted =
                        rawBpm.toIntOrNull()?.let { LocaleFormatters.formatInteger(it, AppLocaleManager.activeLocale(context)) }
                            ?: rawBpm
                    context.getString(CommonR.string.vybe_reason_tempo_boost, formatted)
                }
                else -> reason
            }

        private companion object {
            const val RESULT_LIMIT = 30
            const val STOP_TIMEOUT_MS = 5_000L
            const val ERROR_QUERY_TOO_LONG = "Use a shorter query (up to 1024 characters)."
            const val ERROR_INVALID_RANGE = "These filters do not describe a valid range."
            const val PREFIX_UNKNOWN_EXCLUSION = "Unknown exclusion: "
            const val REASON_SELECTED_REF = "Selected catalog reference"
            const val REASON_SIMILAR_ALL_REFS = "Similar to all selected references"
            const val REASON_SIMILAR_GENRE_TEMPO = "Similar genre and tempo"
            const val PREFIX_ARTIST_BOOST = "Artist: "
            const val PREFIX_GENRE_BOOST = "Genre: "
            const val PREFIX_TEMPO_BOOST = "Tempo: "
            const val SUFFIX_TEMPO_BOOST = " BPM (tag)"
        }
    }
