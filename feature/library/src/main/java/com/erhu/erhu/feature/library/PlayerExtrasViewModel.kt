package com.erhu.erhu.feature.library

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erhu.erhu.core.common.locale.AppLocaleManager
import com.erhu.erhu.core.common.locale.localizedMessage
import com.erhu.erhu.core.common.result.AppResult
import com.erhu.erhu.core.database.repository.TrackBookmarkRepository
import com.erhu.erhu.core.model.Song
import com.erhu.erhu.core.model.TrackBookmark
import com.erhu.erhu.core.model.WaveformLoad
import com.erhu.erhu.core.playback.repository.PlaybackRepository
import com.erhu.erhu.core.playback.waveform.WaveformRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Waveform and timestamp bookmarks of the track that is playing, for Now Playing. Both follow the
 * current song (by library id and file), so a track change cancels the previous waveform request;
 * position ticks never reach these flows. Bookmarks are persisted; the drag state of the waveform
 * stays in the UI.
 */
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerExtrasViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val appContext: Context,
        private val playbackRepository: PlaybackRepository,
        private val waveformRepository: WaveformRepository,
        private val bookmarkRepository: TrackBookmarkRepository,
    ) : ViewModel() {
        private val currentSong: Flow<Song?> =
            playbackRepository.playbackState
                .map { it.currentSong }
                .distinctUntilChanged { old, new -> old?.id == new?.id && old?.uri == new?.uri }

        val waveform: StateFlow<WaveformLoad> =
            currentSong
                .flatMapLatest { song -> song?.let(waveformRepository::waveform) ?: flowOf(WaveformLoad.Unavailable) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), WaveformLoad.Loading)

        val bookmarks: StateFlow<List<TrackBookmark>> =
            currentSong
                .flatMapLatest { song -> song?.let { bookmarkRepository.observeBookmarks(it.id) } ?: flowOf(emptyList()) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

        /** Saves the current playback position of the current track; [onResult] gets a message to show (or null). */
        fun addBookmarkAtCurrentPosition(onResult: (String?) -> Unit = {}) {
            val state = playbackRepository.playbackState.value
            val song = state.currentSong ?: return
            val position = state.positionMs
            viewModelScope.launch {
                val localized = AppLocaleManager.localizedContext(appContext)
                onResult((bookmarkRepository.addBookmark(song.id, position) as? AppResult.Error)?.error?.localizedMessage(localized))
            }
        }

        fun renameBookmark(
            bookmark: TrackBookmark,
            label: String,
        ) {
            viewModelScope.launch { bookmarkRepository.renameBookmark(bookmark.id, label) }
        }

        fun deleteBookmark(bookmark: TrackBookmark) {
            viewModelScope.launch { bookmarkRepository.deleteBookmark(bookmark.id) }
        }

        /** Seeks only within the track the bookmark belongs to. */
        fun seekTo(bookmark: TrackBookmark) {
            if (playbackRepository.playbackState.value.currentSong
                    ?.id == bookmark.songId
            ) {
                playbackRepository.seekTo(bookmark.positionMs)
            }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
