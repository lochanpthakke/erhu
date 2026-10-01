package com.lochanpthakke.empee3.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.lochanpthakke.empee3.core.common.result.AppError
import com.lochanpthakke.empee3.core.common.result.AppResult
import com.lochanpthakke.empee3.core.common.result.getOrDefault
import com.lochanpthakke.empee3.core.database.repository.LibraryRepository
import com.lochanpthakke.empee3.core.designsystem.artwork.ArtworkLoader
import com.lochanpthakke.empee3.core.model.Album
import com.lochanpthakke.empee3.core.model.Artist
import com.lochanpthakke.empee3.core.model.MetadataCandidate
import com.lochanpthakke.empee3.core.model.Song
import com.lochanpthakke.empee3.core.network.repository.MetadataRepository
import com.lochanpthakke.empee3.core.network.scorer.HIGH_CONFIDENCE_THRESHOLD
import com.lochanpthakke.empee3.core.network.scorer.UNCERTAIN_THRESHOLD
import com.lochanpthakke.empee3.core.network.tagger.EditableTags
import com.lochanpthakke.empee3.core.network.tagger.TagWriteRequest
import com.lochanpthakke.empee3.core.network.tagger.WriteAuthorization
import com.lochanpthakke.empee3.core.network.tagger.buildTagWriteRequest
import com.lochanpthakke.empee3.core.playback.repository.PlaybackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RepairQueueItem(
    val song: Song,
    val status: String = "PENDING", // PENDING, FIXING, DONE, NEEDS_REVIEW, FAILED
    val failureReason: String? = null,
)

/** Which library list a tapped song belongs to — that list becomes the play queue. */
enum class SongSource {
    ALL_SONGS,
    FAVORITES,
}

@HiltViewModel
@Suppress("TooManyFunctions")
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
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

        /** Reflects the real WorkManager scan state, not a fixed-length spinner. */
        val isScanning: StateFlow<Boolean> =
            libraryRepository
                .observeIsScanning()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

        private val _isFindingBadTracks = MutableStateFlow(false)
        val isFindingBadTracks: StateFlow<Boolean> = _isFindingBadTracks.asStateFlow()

        private val _repairQueue = MutableStateFlow<List<RepairQueueItem>>(emptyList())
        val repairQueue: StateFlow<List<RepairQueueItem>> = _repairQueue.asStateFlow()

        private val _forceFixTags = MutableStateFlow(false)
        val forceFixTags: StateFlow<Boolean> = _forceFixTags.asStateFlow()

        // Metadata-editor search (tracks + albums + artists to enqueue for repair).
        private val _searchResults = MutableStateFlow<List<Song>>(emptyList())
        val searchResults: StateFlow<List<Song>> = _searchResults.asStateFlow()

        private val _searchAlbums = MutableStateFlow<List<Album>>(emptyList())
        val searchAlbums: StateFlow<List<Album>> = _searchAlbums.asStateFlow()

        private val _searchArtists = MutableStateFlow<List<Artist>>(emptyList())
        val searchArtists: StateFlow<List<Artist>> = _searchArtists.asStateFlow()

        private val _isFixingQueue = MutableStateFlow(false)
        val isFixingQueue: StateFlow<Boolean> = _isFixingQueue.asStateFlow()

        // Library search (title / artist / album), live-updating with the database.
        private val _librarySearchQuery = MutableStateFlow("")
        val librarySearchQuery: StateFlow<String> = _librarySearchQuery.asStateFlow()

        val librarySearchResults: StateFlow<List<Song>> =
            _librarySearchQuery
                .debounce(SEARCH_DEBOUNCE_MS)
                .map { it.trim() }
                .distinctUntilChanged()
                .flatMapLatest { query ->
                    if (query.isEmpty()) flowOf(emptyList()) else libraryRepository.searchSongs(query)
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

        /**
         * Id of the playing song only. Screens that just highlight the current row
         * observe this instead of the full playback state, so the position ticks
         * (4x per second) do not recompose them.
         */
        val currentSongId: StateFlow<Long?> =
            playbackRepository.playbackState
                .map { it.currentSong?.id }
                .distinctUntilChanged()
                .stateIn(
                    viewModelScope,
                    SharingStarted.Eagerly,
                    playbackRepository.playbackState.value.currentSong
                        ?.id,
                )

        /** Favorite state of the playing song, read from Room (the source of truth). */
        val currentSongIsFavorite: StateFlow<Boolean> =
            currentSongId
                .flatMapLatest { id -> id?.let { libraryRepository.observeIsFavorite(it) } ?: flowOf(false) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

        /** Lyrics embedded in the playing file's tags, if any. */
        val currentSongLyrics: StateFlow<String?> =
            currentSongId
                .flatMapLatest { id -> id?.let { libraryRepository.observeEmbeddedLyrics(it) } ?: flowOf(null) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

        private val _writeAccessRequest = MutableStateFlow<WriteAuthorization?>(null)

        /** Non-null while a tag write waits for the user to grant write access. */
        val writeAccessRequest: StateFlow<WriteAuthorization?> = _writeAccessRequest.asStateFlow()
        private var pendingWrite: PendingWrite? = null

        private val _userMessage = MutableStateFlow<String?>(null)
        val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

        init {
            triggerScan()
        }

        fun consumeUserMessage() {
            _userMessage.value = null
        }

        fun setForceFixTags(enabled: Boolean) {
            _forceFixTags.value = enabled
        }

        fun clearRepairQueue() {
            _repairQueue.value = emptyList()
        }

        fun scanForBadTracks() {
            viewModelScope.launch {
                _isFindingBadTracks.value = true
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
                _isFindingBadTracks.value = false
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

        fun setLibrarySearchQuery(query: String) {
            _librarySearchQuery.value = query
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

        /**
         * Looks up matches for every pending track first, then writes all confident
         * matches in one go, so Android 11+ shows a single write-access dialog for
         * the whole batch instead of one per file.
         */
        @Suppress("MagicNumber", "NestedBlockDepth", "TooGenericExceptionCaught", "LongMethod", "CyclomaticComplexMethod")
        fun startFixingQueue() {
            if (_isFixingQueue.value) return
            viewModelScope.launch {
                _isFixingQueue.value = true
                val queue = _repairQueue.value.toMutableList()
                val writes = mutableMapOf<Int, TagWriteRequest>()

                for (i in queue.indices) {
                    val item = queue[i]
                    if (item.status != "PENDING") continue
                    queue[i] = item.copy(status = "FIXING")
                    _repairQueue.value = queue.toList()
                    delay(REPAIR_DELAY_MS)

                    queue[i] =
                        try {
                            val best =
                                metadataRepository
                                    .searchTrack(item.song.title, item.song.artist, "ALL")
                                    .getOrDefault(emptyList())
                                    .firstOrNull()
                            val filePath = libraryRepository.getSongFilePath(item.song.id)
                            when {
                                best == null || best.confidenceScore < UNCERTAIN_THRESHOLD ->
                                    item.copy(status = "FAILED", failureReason = "NO_MATCH")
                                best.confidenceScore < HIGH_CONFIDENCE_THRESHOLD -> item.copy(status = "NEEDS_REVIEW")
                                filePath == null -> item.copy(status = "FAILED", failureReason = "FILE_NOT_FOUND")
                                else -> {
                                    val request =
                                        buildTagWriteRequest(
                                            contentUri = item.song.uri,
                                            filePath = filePath,
                                            current = EditableTags(item.song.title, item.song.artist, item.song.album),
                                            title = best.title,
                                            artist = best.artist,
                                            album = best.album,
                                            year = 0,
                                            genre = "",
                                        )
                                    if (request == null) {
                                        item.copy(status = "DONE") // tags already match
                                    } else {
                                        writes[i] = request
                                        item.copy(status = "FIXING")
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            item.copy(status = "FAILED", failureReason = e.message ?: "NETWORK_ERROR")
                        }
                    _repairQueue.value = queue.toList()
                }

                if (writes.isEmpty()) {
                    _isFixingQueue.value = false
                    return@launch
                }

                withWriteAccess(
                    contentUris = writes.values.map { it.contentUri },
                    onGranted = {
                        var wroteAny = false
                        for ((index, request) in writes) {
                            val item = queue[index]
                            queue[index] =
                                when (val result = metadataRepository.writeTagsToFile(request)) {
                                    is AppResult.Success -> {
                                        wroteAny = true
                                        ArtworkLoader.invalidate(request.contentUri)
                                        item.copy(status = "DONE")
                                    }
                                    is AppResult.Error -> item.copy(status = "FAILED", failureReason = result.error.message)
                                }
                            _repairQueue.value = queue.toList()
                        }
                        if (wroteAny) libraryRepository.requestRescanAfterChange()
                        _isFixingQueue.value = false
                    },
                    onDenied = {
                        for (index in writes.keys) {
                            queue[index] = queue[index].copy(status = "FAILED", failureReason = "Write access not granted")
                        }
                        _repairQueue.value = queue.toList()
                        _isFixingQueue.value = false
                    },
                )
            }
        }

        /**
         * Plays [song] with the whole list it was tapped in as the queue. The list
         * is read from Room rather than from the paging snapshot, which only holds
         * the pages loaded so far and would misalign the start index.
         */
        fun playFromLibrary(
            song: Song,
            source: SongSource,
        ) {
            viewModelScope.launch {
                val songs =
                    when (source) {
                        SongSource.ALL_SONGS -> libraryRepository.getAllSongs().first()
                        SongSource.FAVORITES -> libraryRepository.getFavoriteSongs().first()
                    }
                val index = songs.indexOfFirst { it.id == song.id }
                if (index >= 0) {
                    playbackRepository.playSongsFromQueue(songs, index)
                } else {
                    playbackRepository.playSong(song)
                }
            }
        }

        fun playSongsFromQueue(
            songs: List<Song>,
            startIndex: Int,
        ) {
            playbackRepository.playSongsFromQueue(songs, startIndex)
        }

        fun playNext(song: Song) {
            viewModelScope.launch {
                playbackRepository.playNext(song)
                _userMessage.value = "\"${song.title}\" will play next"
            }
        }

        fun addToQueue(song: Song) {
            viewModelScope.launch {
                playbackRepository.addToQueue(song)
                _userMessage.value = "Added \"${song.title}\" to queue"
            }
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

        /** The album, or null once it is gone from the library (e.g. after a rescan). */
        fun observeAlbum(albumId: Long): Flow<Album?> = libraryRepository.getAlbumById(albumId)

        /** The album's songs in album order (disc, track, title). */
        fun observeAlbumSongs(albumId: Long): Flow<List<Song>> = libraryRepository.getSongsByAlbumId(albumId)

        /** Plays [songs] in their order from the first track, with shuffle turned off. */
        fun playInOrder(songs: List<Song>) {
            if (songs.isEmpty()) return
            playbackRepository.setShuffleModeEnabled(false)
            playbackRepository.playSongsFromQueue(songs, 0)
        }

        /** Plays [songs] shuffled: the same shuffle mode the playlists use. */
        fun playShuffled(songs: List<Song>) {
            if (songs.isEmpty()) return
            playbackRepository.setShuffleModeEnabled(true)
            playbackRepository.playSongsFromQueue(songs, songs.indices.random())
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
                libraryRepository.triggerLibraryScan()
            }
        }

        fun searchMetadataForSong(song: Song) {
            _metadataCandidates.value = emptyList()
            viewModelScope.launch {
                val result = metadataRepository.searchTrack(song.title, song.artist, "ALL")
                _metadataCandidates.value = result.getOrDefault(emptyList())
            }
        }

        /**
         * Writes the user's edits from the Fix Tags dialog. Only fields that differ
         * from the current values are written; blank year/genre keep the file's values.
         */
        @Suppress("LongParameterList")
        fun writeTags(
            song: Song,
            title: String,
            artist: String,
            album: String,
            year: Int,
            genre: String,
        ) {
            viewModelScope.launch {
                val filePath = libraryRepository.getSongFilePath(song.id)
                if (filePath == null) {
                    _userMessage.value = "Couldn't find the file for \"${song.title}\". Pull to refresh the library and try again."
                    return@launch
                }
                val request =
                    buildTagWriteRequest(
                        contentUri = song.uri,
                        filePath = filePath,
                        current = EditableTags(song.title, song.artist, song.album),
                        title = title,
                        artist = artist,
                        album = album,
                        year = year,
                        genre = genre,
                    )
                if (request == null) {
                    _userMessage.value = "Nothing changed"
                    return@launch
                }
                withWriteAccess(
                    contentUris = listOf(request.contentUri),
                    onGranted = {
                        when (val result = metadataRepository.writeTagsToFile(request)) {
                            is AppResult.Success -> {
                                ArtworkLoader.invalidate(request.contentUri)
                                libraryRepository.requestRescanAfterChange()
                                _userMessage.value = "Tags saved for \"${request.title ?: song.title}\""
                            }
                            is AppResult.Error -> _userMessage.value = tagWriteFailureMessage(result.error)
                        }
                    },
                    onDenied = { _userMessage.value = "Tags not changed: empee3 wasn't allowed to modify the file." },
                )
            }
        }

        private fun tagWriteFailureMessage(error: AppError): String =
            when (error) {
                is AppError.WriteAccessRequired -> "Tags not changed: ${error.message}"
                else -> "Couldn't write tags: ${error.message}"
            }

        /**
         * Runs [onGranted] once the app may modify [contentUris]. If Android needs the
         * user's consent first, the request is exposed through [writeAccessRequest]
         * for the UI to launch; the outcome comes back via [onWriteAccessResult].
         */
        private suspend fun withWriteAccess(
            contentUris: List<String>,
            onGranted: suspend () -> Unit,
            onDenied: suspend () -> Unit,
            attempt: Int = 0,
        ) {
            val authorization = metadataRepository.checkTagWriteAccess(contentUris)
            // Android 10 asks per file, so allow one round per file; after that just try
            // the write and let it report a precise error instead of re-prompting forever.
            if (authorization is WriteAuthorization.Granted || attempt > contentUris.size) {
                onGranted()
                return
            }
            pendingWrite =
                PendingWrite(
                    onGranted = { withWriteAccess(contentUris, onGranted, onDenied, attempt + 1) },
                    onDenied = onDenied,
                )
            _writeAccessRequest.value = authorization
        }

        /** Called by the UI with the result of the system dialog / permission request. */
        fun onWriteAccessResult(granted: Boolean) {
            _writeAccessRequest.value = null
            val pending = pendingWrite ?: return
            pendingWrite = null
            viewModelScope.launch { if (granted) pending.onGranted() else pending.onDenied() }
        }

        private class PendingWrite(
            val onGranted: suspend () -> Unit,
            val onDenied: suspend () -> Unit,
        )

        companion object {
            private const val REPAIR_DELAY_MS = 600L
            private const val SEARCH_DEBOUNCE_MS = 250L
            private const val STOP_TIMEOUT_MS = 5_000L
        }
    }
