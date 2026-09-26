package com.lochanpthakke.empee3.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.lochanpthakke.empee3.core.common.result.getOrDefault
import com.lochanpthakke.empee3.core.database.repository.LibraryRepository
import com.lochanpthakke.empee3.core.model.Album
import com.lochanpthakke.empee3.core.model.Artist
import com.lochanpthakke.empee3.core.model.MetadataCandidate
import com.lochanpthakke.empee3.core.model.Song
import com.lochanpthakke.empee3.core.network.repository.MetadataRepository
import com.lochanpthakke.empee3.core.network.tagger.TagWriteRequest
import com.lochanpthakke.empee3.core.playback.repository.PlaybackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RepairQueueItem(
    val song: Song,
    val status: String = "PENDING", // PENDING, FIXING, DONE, NEEDS_REVIEW, FAILED
    val failureReason: String? = null,
)

@HiltViewModel
@Suppress("TooManyFunctions")
class LibraryViewModel
    @Inject
    constructor(
        private val libraryRepository: LibraryRepository,
        val playbackRepository: PlaybackRepository,
        private val metadataRepository: MetadataRepository,
    ) : ViewModel() {
        val songsPaged: Flow<PagingData<Song>> =
            libraryRepository
                .getSongsPaged()
                .cachedIn(viewModelScope)

        val favoriteSongsPaged: Flow<PagingData<Song>> =
            libraryRepository
                .getFavoriteSongsPaged()
                .cachedIn(viewModelScope)

        val albumsPaged: Flow<PagingData<Album>> =
            libraryRepository
                .getAlbumsPaged()
                .cachedIn(viewModelScope)

        val artistsPaged: Flow<PagingData<Artist>> =
            libraryRepository
                .getArtistsPaged()
                .cachedIn(viewModelScope)

        private val _metadataCandidates = MutableStateFlow<List<MetadataCandidate>>(emptyList())
        val metadataCandidates: StateFlow<List<MetadataCandidate>> = _metadataCandidates.asStateFlow()

        private val _isScanning = MutableStateFlow(false)
        val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

        private val _repairQueue = MutableStateFlow<List<RepairQueueItem>>(emptyList())
        val repairQueue: StateFlow<List<RepairQueueItem>> = _repairQueue.asStateFlow()

        private val _forceFixTags = MutableStateFlow(false)
        val forceFixTags: StateFlow<Boolean> = _forceFixTags.asStateFlow()

        private val _searchResults = MutableStateFlow<List<Song>>(emptyList())
        val searchResults: StateFlow<List<Song>> = _searchResults.asStateFlow()

        private val _searchAlbums = MutableStateFlow<List<Album>>(emptyList())
        val searchAlbums: StateFlow<List<Album>> = _searchAlbums.asStateFlow()

        private val _searchArtists = MutableStateFlow<List<Artist>>(emptyList())
        val searchArtists: StateFlow<List<Artist>> = _searchArtists.asStateFlow()

        private val _isFixingQueue = MutableStateFlow(false)
        val isFixingQueue: StateFlow<Boolean> = _isFixingQueue.asStateFlow()

        init {
            triggerScan()
        }

        fun setForceFixTags(enabled: Boolean) {
            _forceFixTags.value = enabled
        }

        fun clearRepairQueue() {
            _repairQueue.value = emptyList()
        }

        fun scanForBadTracks() {
            viewModelScope.launch {
                _isScanning.value = true
                val allSongs = libraryRepository.getAllSongs().first()
                val force = _forceFixTags.value
                val badTracks =
                    if (force) {
                        allSongs
                    } else {
                        allSongs.filter { song ->
                            song.title.isBlank() ||
                                song.artist.isBlank() ||
                                song.artist == "Unknown Artist" ||
                                song.album.isBlank() ||
                                song.album == "Unknown Album" ||
                                song.title.contains("mp3", ignoreCase = true) ||
                                song.title.contains(".com", ignoreCase = true) ||
                                song.title.contains("pagalworld", ignoreCase = true) ||
                                song.title.contains("[320kbps]", ignoreCase = true)
                        }
                    }
                _repairQueue.value = badTracks.map { RepairQueueItem(song = it, status = "PENDING") }
                libraryRepository.triggerLibraryScan()
                delay(SCAN_INDICATOR_DELAY_MS)
                _isScanning.value = false
            }
        }

        fun searchLocalLibrary(query: String) {
            if (query.isBlank()) {
                _searchResults.value = emptyList()
                _searchAlbums.value = emptyList()
                _searchArtists.value = emptyList()
                return
            }
            viewModelScope.launch {
                val songMatches = libraryRepository.searchSongs(query).first()
                _searchResults.value = songMatches

                val allAlbums = libraryRepository.getAllAlbums().first()
                _searchAlbums.value =
                    allAlbums.filter {
                        it.title.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true)
                    }

                val allArtists = libraryRepository.getAllArtists().first()
                _searchArtists.value =
                    allArtists.filter {
                        it.name.contains(query, ignoreCase = true)
                    }
            }
        }

        fun enqueueSongForRepair(song: Song) {
            val currentQueue = _repairQueue.value.toMutableList()
            if (currentQueue.none { it.song.id == song.id }) {
                currentQueue.add(RepairQueueItem(song = song, status = "PENDING"))
                _repairQueue.value = currentQueue
            }
        }

        fun removeFromRepairQueue(song: Song) {
            val currentQueue = _repairQueue.value.toMutableList()
            currentQueue.removeAll { it.song.id == song.id }
            _repairQueue.value = currentQueue
        }

        fun enqueueAlbumForRepair(album: Album) {
            viewModelScope.launch {
                val albumSongs = libraryRepository.getSongsByAlbumId(album.id).first()
                val currentQueue = _repairQueue.value.toMutableList()
                val existingIds = currentQueue.map { it.song.id }.toSet()
                val newItems = albumSongs.filter { it.id !in existingIds }.map { RepairQueueItem(song = it, status = "PENDING") }
                currentQueue.addAll(newItems)
                _repairQueue.value = currentQueue
            }
        }

        fun enqueueArtistForRepair(artist: Artist) {
            viewModelScope.launch {
                val artistSongs = libraryRepository.getSongsByArtistId(artist.id).first()
                val currentQueue = _repairQueue.value.toMutableList()
                val existingIds = currentQueue.map { it.song.id }.toSet()
                val newItems = artistSongs.filter { it.id !in existingIds }.map { RepairQueueItem(song = it, status = "PENDING") }
                currentQueue.addAll(newItems)
                _repairQueue.value = currentQueue
            }
        }

        @Suppress("MagicNumber", "NestedBlockDepth", "TooGenericExceptionCaught")
        fun startFixingQueue() {
            if (_isFixingQueue.value) return
            viewModelScope.launch {
                _isFixingQueue.value = true
                val queue = _repairQueue.value.toMutableList()

                for (i in queue.indices) {
                    val item = queue[i]
                    if (item.status == "PENDING") {
                        queue[i] = item.copy(status = "FIXING")
                        _repairQueue.value = queue.toList()

                        delay(REPAIR_DELAY_MS)

                        try {
                            val searchRes =
                                metadataRepository
                                    .searchTrack(
                                        item.song.title,
                                        item.song.artist,
                                        "ALL",
                                    ).getOrDefault(emptyList())
                            val best = searchRes.firstOrNull()

                            if (best != null && best.confidenceScore >= HIGH_CONFIDENCE_THRESHOLD) {
                                val req =
                                    TagWriteRequest(
                                        filePath = item.song.uri.removePrefix("file://"),
                                        title = best.title,
                                        artist = best.artist,
                                        album = best.album,
                                    )
                                metadataRepository.writeTagsToFile(req)
                                queue[i] = item.copy(status = "DONE")
                            } else if (best != null && best.confidenceScore >= UNCERTAIN_THRESHOLD) {
                                queue[i] = item.copy(status = "NEEDS_REVIEW")
                            } else {
                                queue[i] = item.copy(status = "FAILED", failureReason = "NO_MATCH")
                            }
                        } catch (e: Throwable) {
                            queue[i] = item.copy(status = "FAILED", failureReason = e.message ?: "NETWORK_ERROR")
                        }

                        _repairQueue.value = queue.toList()
                    }
                }
                _isFixingQueue.value = false
            }
        }

        fun playSong(song: Song) {
            viewModelScope.launch {
                playbackRepository.playSong(song)
            }
        }

        fun playSongsFromQueue(
            songs: List<Song>,
            startIndex: Int,
        ) {
            playbackRepository.playSongsFromQueue(songs, startIndex)
        }

        fun toggleFavorite(song: Song) {
            viewModelScope.launch {
                libraryRepository.toggleFavorite(song.id)
            }
        }

        fun playAlbum(album: Album) {
            viewModelScope.launch {
                val songs = libraryRepository.getSongsByAlbumId(album.id).first()
                if (songs.isNotEmpty()) {
                    playbackRepository.playSongs(songs)
                }
            }
        }

        fun playArtist(artist: Artist) {
            viewModelScope.launch {
                val songs = libraryRepository.getSongsByArtistId(artist.id).first()
                if (songs.isNotEmpty()) {
                    playbackRepository.playSongs(songs)
                }
            }
        }

        fun triggerScan() {
            viewModelScope.launch {
                _isScanning.value = true
                libraryRepository.triggerLibraryScan()
                delay(SCAN_INDICATOR_DELAY_MS)
                _isScanning.value = false
            }
        }

        fun searchMetadataForSong(song: Song) {
            viewModelScope.launch {
                val result = metadataRepository.searchTrack(song.title, song.artist, "ALL")
                _metadataCandidates.value = result.getOrDefault(emptyList())
            }
        }

        fun writeTags(
            song: Song,
            title: String,
            artist: String,
            album: String,
            year: Int,
            genre: String,
        ) {
            viewModelScope.launch {
                val request =
                    TagWriteRequest(
                        filePath = song.uri.removePrefix("file://"),
                        title = title,
                        artist = artist,
                        album = album,
                        year = year,
                        genre = genre,
                    )
                metadataRepository.writeTagsToFile(request)
                libraryRepository.triggerLibraryScan()
            }
        }

        companion object {
            private const val SCAN_INDICATOR_DELAY_MS = 1200L
            private const val REPAIR_DELAY_MS = 600L
            private const val HIGH_CONFIDENCE_THRESHOLD = 0.85f
            private const val UNCERTAIN_THRESHOLD = 0.60f
        }
    }
