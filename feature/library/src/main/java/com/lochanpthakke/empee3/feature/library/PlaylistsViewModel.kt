package com.lochanpthakke.empee3.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lochanpthakke.empee3.core.common.result.AppResult
import com.lochanpthakke.empee3.core.database.repository.PlaylistRepository
import com.lochanpthakke.empee3.core.model.Playlist
import com.lochanpthakke.empee3.core.model.Song
import com.lochanpthakke.empee3.core.playback.repository.PlaybackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistsViewModel
    @Inject
    constructor(
        private val playlistRepository: PlaylistRepository,
        private val playbackRepository: PlaybackRepository,
    ) : ViewModel() {
        val playlists: StateFlow<List<Playlist>> =
            playlistRepository
                .observePlaylists()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

        private val openPlaylistId = MutableStateFlow<Long?>(null)

        val openPlaylist: StateFlow<Playlist?> =
            openPlaylistId
                .flatMapLatest { id -> id?.let { playlistRepository.observePlaylist(it) } ?: flowOf(null) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

        val openPlaylistSongs: StateFlow<List<Song>> =
            openPlaylistId
                .flatMapLatest { id -> id?.let { playlistRepository.observePlaylistSongs(it) } ?: flowOf(emptyList()) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

        private val _userMessage = MutableStateFlow<String?>(null)
        val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

        fun consumeUserMessage() {
            _userMessage.value = null
        }

        fun openPlaylist(playlistId: Long) {
            openPlaylistId.value = playlistId
        }

        fun createPlaylist(
            name: String,
            thenAddSongs: List<Song> = emptyList(),
        ) {
            viewModelScope.launch {
                when (val result = playlistRepository.createPlaylist(name)) {
                    is AppResult.Success -> {
                        if (thenAddSongs.isNotEmpty()) {
                            addSongsToPlaylist(result.data, name.trim(), thenAddSongs)
                        } else {
                            _userMessage.value = "Created \"${name.trim()}\""
                        }
                    }
                    is AppResult.Error -> _userMessage.value = result.error.message
                }
            }
        }

        fun deletePlaylist(playlist: Playlist) {
            viewModelScope.launch {
                when (val result = playlistRepository.deletePlaylist(playlist.id)) {
                    is AppResult.Success -> _userMessage.value = "Deleted \"${playlist.name}\""
                    is AppResult.Error -> _userMessage.value = result.error.message
                }
            }
        }

        fun addSongsToPlaylist(
            playlist: Playlist,
            songs: List<Song>,
        ) {
            viewModelScope.launch { addSongsToPlaylist(playlist.id, playlist.name, songs) }
        }

        /** Appends [songs] in order; songs already in the playlist are skipped by the repository. */
        private suspend fun addSongsToPlaylist(
            playlistId: Long,
            playlistName: String,
            songs: List<Song>,
        ) {
            var added = 0
            for (song in songs) {
                when (val result = playlistRepository.addSong(playlistId, song.id)) {
                    is AppResult.Success -> if (result.data) added++
                    is AppResult.Error -> {
                        _userMessage.value = result.error.message
                        return
                    }
                }
            }
            _userMessage.value =
                when {
                    added == 0 -> "Already in \"$playlistName\""
                    songs.size == 1 -> "Added to \"$playlistName\""
                    else -> "Added ${countLabel(added, "song")} to \"$playlistName\""
                }
        }

        fun removeSongFromOpenPlaylist(song: Song) {
            val playlistId = openPlaylistId.value ?: return
            viewModelScope.launch {
                playlistRepository.removeSong(playlistId, song.id)
            }
        }

        fun playOpenPlaylist(startIndex: Int = 0) {
            val songs = openPlaylistSongs.value
            if (songs.isEmpty()) return
            playbackRepository.playSongsFromQueue(songs, startIndex.coerceIn(0, songs.lastIndex))
        }

        fun shuffleOpenPlaylist() {
            val songs = openPlaylistSongs.value
            if (songs.isEmpty()) return
            playbackRepository.setShuffleModeEnabled(true)
            playbackRepository.playSongsFromQueue(songs, songs.indices.random())
        }

        companion object {
            private const val STOP_TIMEOUT_MS = 5_000L
        }
    }
