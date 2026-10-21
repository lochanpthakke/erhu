package com.lochanpthakke.empee3.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lochanpthakke.empee3.core.common.result.AppResult
import com.lochanpthakke.empee3.core.database.repository.PlaylistRepository
import com.lochanpthakke.empee3.core.model.CollectionRef
import com.lochanpthakke.empee3.core.model.Playlist
import com.lochanpthakke.empee3.core.model.PlaylistEntry
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
import kotlinx.coroutines.flow.map
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

        /** Playlists recently played from a playlist, newest first (Tracks home). */
        val recentPlaylists: StateFlow<List<Playlist>> =
            playlistRepository
                .observeRecentPlaylists(RECENT_LIMIT)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

        private val openPlaylistId = MutableStateFlow<Long?>(null)

        val openPlaylist: StateFlow<Playlist?> =
            openPlaylistId
                .flatMapLatest { id -> id?.let { playlistRepository.observePlaylist(it) } ?: flowOf(null) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

        /** The open playlist's entries with their positions (each entry's identity, duplicates included). */
        val openPlaylistEntries: StateFlow<List<PlaylistEntry>> =
            openPlaylistId
                .flatMapLatest { id -> id?.let { playlistRepository.observePlaylistEntries(it) } ?: flowOf(emptyList()) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

        val openPlaylistSongs: StateFlow<List<Song>> =
            openPlaylistEntries
                .map { entries -> entries.map { it.song } }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

        private val _userMessage = MutableStateFlow<String?>(null)
        val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

        fun consumeUserMessage() {
            _userMessage.value = null
        }

        fun openPlaylist(playlistId: Long) {
            openPlaylistId.value = playlistId
        }

        /** [onResult], when given, receives the outcome message instead of [userMessage]. */
        fun createPlaylist(
            name: String,
            thenAddSongs: List<Song> = emptyList(),
            onResult: ((String) -> Unit)? = null,
        ) {
            viewModelScope.launch {
                val message =
                    when (val result = playlistRepository.createPlaylist(name)) {
                        is AppResult.Success ->
                            if (thenAddSongs.isNotEmpty()) {
                                addSongsToPlaylist(result.data, name.trim(), thenAddSongs)
                            } else {
                                "Created \"${name.trim()}\""
                            }
                        is AppResult.Error -> result.error.message
                    }
                report(message, onResult)
            }
        }

        /**
         * Saves [songs] (the current and upcoming queue entries, in queue order, duplicates kept) as a
         * new playlist named [name]. [onResult] receives the message to show, on the main thread.
         */
        fun saveQueueAsPlaylist(
            name: String,
            songs: List<Song>,
            onResult: (String) -> Unit,
        ) {
            viewModelScope.launch {
                val message =
                    when (val result = playlistRepository.createPlaylistWithSongs(name, songs.map { it.id })) {
                        is AppResult.Success -> "Saved ${countLabel(songs.size, "song")} to \"${name.trim()}\""
                        is AppResult.Error -> result.error.message
                    }
                onResult(message)
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

        /** [onResult], when given, receives the outcome message instead of [userMessage]. */
        fun addSongsToPlaylist(
            playlist: Playlist,
            songs: List<Song>,
            onResult: ((String) -> Unit)? = null,
        ) {
            viewModelScope.launch { report(addSongsToPlaylist(playlist.id, playlist.name, songs), onResult) }
        }

        private fun report(
            message: String,
            onResult: ((String) -> Unit)?,
        ) {
            if (onResult != null) onResult(message) else _userMessage.value = message
        }

        /**
         * Appends [songs] in order; songs already in the playlist are skipped by the repository.
         * Returns the message to show.
         */
        private suspend fun addSongsToPlaylist(
            playlistId: Long,
            playlistName: String,
            songs: List<Song>,
        ): String {
            var added = 0
            for (song in songs) {
                when (val result = playlistRepository.addSong(playlistId, song.id)) {
                    is AppResult.Success -> if (result.data) added++
                    is AppResult.Error -> return result.error.message
                }
            }
            return when {
                added == 0 -> "Already in \"$playlistName\""
                songs.size == 1 -> "Added to \"$playlistName\""
                else -> "Added ${countLabel(added, "song")} to \"$playlistName\""
            }
        }

        fun renameOpenPlaylist(name: String) {
            val playlistId = openPlaylistId.value ?: return
            viewModelScope.launch {
                (playlistRepository.renamePlaylist(playlistId, name) as? AppResult.Error)?.let { _userMessage.value = it.error.message }
            }
        }

        /** [imageBytes]: the cropped square cover, already encoded. */
        fun setOpenPlaylistArtwork(imageBytes: ByteArray) {
            val playlistId = openPlaylistId.value ?: return
            viewModelScope.launch {
                (playlistRepository.setPlaylistArtwork(playlistId, imageBytes) as? AppResult.Error)?.let {
                    _userMessage.value =
                        it.error.message
                }
            }
        }

        fun clearOpenPlaylistArtwork() {
            val playlistId = openPlaylistId.value ?: return
            viewModelScope.launch {
                (playlistRepository.clearPlaylistArtwork(playlistId) as? AppResult.Error)?.let { _userMessage.value = it.error.message }
            }
        }

        fun removeSongFromOpenPlaylist(song: Song) {
            val playlistId = openPlaylistId.value ?: return
            viewModelScope.launch {
                playlistRepository.removeSong(playlistId, song.id)
            }
        }

        fun playOpenPlaylist(startIndex: Int = 0) {
            val entries = openPlaylistEntries.value
            if (entries.isEmpty()) return
            val origin = openPlaylistId.value?.let(CollectionRef::playlist)
            playbackRepository.playCollection(
                entries.map { it.song },
                startIndex.coerceIn(0, entries.lastIndex),
                origin,
                entries.map { it.position },
            )
            markOpenPlaylistPlayed()
        }

        fun shuffleOpenPlaylist() {
            val entries = openPlaylistEntries.value
            if (entries.isEmpty()) return
            playbackRepository.setShuffleModeEnabled(true)
            playbackRepository.playCollection(
                entries.map { it.song },
                entries.indices.random(),
                openPlaylistId.value?.let(CollectionRef::playlist),
                entries.map { it.position },
            )
            markOpenPlaylistPlayed()
        }

        private fun markOpenPlaylistPlayed() {
            val playlistId = openPlaylistId.value ?: return
            viewModelScope.launch { playlistRepository.markPlaylistPlayed(playlistId) }
        }

        companion object {
            private const val STOP_TIMEOUT_MS = 5_000L
            private const val RECENT_LIMIT = 12
        }
    }
