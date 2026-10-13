package com.lochanpthakke.empee3.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.lochanpthakke.empee3.core.common.result.AppError
import com.lochanpthakke.empee3.core.common.result.AppResult
import com.lochanpthakke.empee3.core.common.result.getOrDefault
import com.lochanpthakke.empee3.core.database.library.ArtistArtworkCache
import com.lochanpthakke.empee3.core.database.repository.ArtistArtworkRepository
import com.lochanpthakke.empee3.core.database.repository.LibraryRepository
import com.lochanpthakke.empee3.core.database.repository.RecentlyPlayedRecorder
import com.lochanpthakke.empee3.core.database.repository.SmartPlaylistRepository
import com.lochanpthakke.empee3.core.designsystem.artwork.ArtworkLoader
import com.lochanpthakke.empee3.core.model.Album
import com.lochanpthakke.empee3.core.model.Artist
import com.lochanpthakke.empee3.core.model.CollectionRef
import com.lochanpthakke.empee3.core.model.MetadataCandidate
import com.lochanpthakke.empee3.core.model.MetadataFixAction
import com.lochanpthakke.empee3.core.model.SmartPlaylist
import com.lochanpthakke.empee3.core.model.Song
import com.lochanpthakke.empee3.core.model.TagChange
import com.lochanpthakke.empee3.core.network.lyrics.LyricsCandidate
import com.lochanpthakke.empee3.core.network.lyrics.OnlineLyricsRepository
import com.lochanpthakke.empee3.core.network.lyrics.ProviderResponse
import com.lochanpthakke.empee3.core.network.repository.GenreLookup
import com.lochanpthakke.empee3.core.network.repository.MetadataRepository
import com.lochanpthakke.empee3.core.network.scorer.HIGH_CONFIDENCE_THRESHOLD
import com.lochanpthakke.empee3.core.network.scorer.UNCERTAIN_THRESHOLD
import com.lochanpthakke.empee3.core.network.tagger.EditableTags
import com.lochanpthakke.empee3.core.network.tagger.EmbeddedTagReader
import com.lochanpthakke.empee3.core.network.tagger.FieldFixPlan
import com.lochanpthakke.empee3.core.network.tagger.FixField
import com.lochanpthakke.empee3.core.network.tagger.LocalTrackTags
import com.lochanpthakke.empee3.core.network.tagger.ReleaseDate
import com.lochanpthakke.empee3.core.network.tagger.ReleaseDateDecision
import com.lochanpthakke.empee3.core.network.tagger.fields
import com.lochanpthakke.empee3.core.network.tagger.isUsableTag
import com.lochanpthakke.empee3.core.network.tagger.isUsableYearTag
import com.lochanpthakke.empee3.core.network.tagger.planFieldFix
import com.lochanpthakke.empee3.core.network.tagger.releaseDateFor
import com.lochanpthakke.empee3.core.network.tagger.TagWriteRequest
import com.lochanpthakke.empee3.core.network.tagger.WriteAuthorization
import com.lochanpthakke.empee3.core.network.tagger.buildTagWriteRequest
import com.lochanpthakke.empee3.core.playback.repository.PlaybackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import javax.inject.Inject

data class RepairQueueItem(
    val song: Song,
    val status: String = "PENDING", // PENDING, FIXING, DONE, NEEDS_REVIEW, FAILED
    val failureReason: String? = null,
    val diagnostics: com.lochanpthakke.empee3.core.model.RepairDiagnostics? = null,
    val successDetails: com.lochanpthakke.empee3.core.model.SuccessDetails? = null,
)

/** Which library list a tapped song belongs to â€” that list becomes the play queue. */
enum class SongSource {
    ALL_SONGS,
    FAVORITES,
}

data class ArtworkQueueItem(
    val album: Album,
    val status: String = "PENDING", // PENDING, SEARCHING, ARTWORK_FOUND, WRITING, COMPLETED, FAILED
    val failureReason: String? = null,
)

@HiltViewModel
@Suppress("TooManyFunctions", "LargeClass")
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class LibraryViewModel
    @Inject
    constructor(
        private val libraryRepository: LibraryRepository,
        val playbackRepository: PlaybackRepository,
        private val metadataRepository: MetadataRepository,
        private val themeRepository: com.lochanpthakke.empee3.core.datastore.repository.ThemeRepository,
        private val artistArtworkRepository: ArtistArtworkRepository,
        private val artistArtworkUpdater: ArtistArtworkUpdater,
        recentlyPlayed: RecentlyPlayedRecorder,
        smartPlaylistRepository: SmartPlaylistRepository,
        private val onlineLyrics: OnlineLyricsRepository,
        private val learnedWatermarkDao: com.lochanpthakke.empee3.core.database.dao.LearnedWatermarkDao,
        private val fileRenamer: com.lochanpthakke.empee3.core.network.tagger.FileRenamer,
        private val titleCandidateGenerator: com.lochanpthakke.empee3.core.network.cleaner.TitleCandidateGenerator,
        private val embeddedTagReader: EmbeddedTagReader,
    ) : ViewModel() {
        /**
         * The album artwork (one song per album, up to four) of each smart playlist card on
         * the Tracks page, from the playlists' current songs. Queried only while the cards show.
         */
        val smartPlaylistCovers: StateFlow<Map<SmartPlaylist, List<Song>>> =
            combine(HOME_SMART_PLAYLISTS.map { playlist -> smartPlaylistRepository.observeCover(playlist).map { playlist to it } }) {
                it.toMap()
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyMap())

        val isCustomScanLocationEnabled: StateFlow<Boolean> = themeRepository.isCustomScanLocationEnabled
        val customScanLocationPath: StateFlow<String> = themeRepository.customScanLocationPath
        val scanLocationConfigured: StateFlow<Boolean> = themeRepository.scanLocationConfigured

        fun setCustomScanLocation(
            uri: String,
            displayPath: String,
        ) {
            viewModelScope.launch {
                themeRepository.setCustomScanLocation(uri, displayPath)
                triggerScan()
            }
        }

        fun setScanEntireDevice() {
            viewModelScope.launch {
                themeRepository.setScanEntireDevice()
                triggerScan()
            }
        }

        /** Albums of songs actually played, newest first (Tracks home). */
        val recentAlbums: StateFlow<List<Album>> =
            recentlyPlayed
                .observeRecentAlbums(RECENT_ALBUMS_LIMIT)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

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

        private val isScanningLocal = MutableStateFlow(false)

        /** Reflects the real WorkManager scan state, combined with local scan indicators. */
        val isScanning: StateFlow<Boolean> =
            libraryRepository
                .observeIsScanning()
                .combine(isScanningLocal) { libScan, localScan -> libScan || localScan }
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

        /** Metadata & Artwork source, stored in settings; one of [METADATA_SOURCE_OPTIONS]. */
        val selectedProvider: StateFlow<String> =
            themeRepository.metadataProvider
                .map(::offeredMetadataSource)
                .stateIn(viewModelScope, SharingStarted.Eagerly, offeredMetadataSource(themeRepository.metadataProvider.value))

        /** Metadata & Artwork fix action, stored in settings; always a supported action. */
        val selectedFixAction: StateFlow<MetadataFixAction> =
            themeRepository.metadataFixAction
                .map(MetadataFixAction::fromStored)
                .stateIn(viewModelScope, SharingStarted.Eagerly, MetadataFixAction.fromStored(themeRepository.metadataFixAction.value))

        enum class RepairMode { AUTOMATIC, MANUAL }

        private val _repairMode = MutableStateFlow(RepairMode.AUTOMATIC)
        val repairMode: StateFlow<RepairMode> = _repairMode.asStateFlow()

        fun setRepairMode(mode: RepairMode) {
            _repairMode.value = mode
        }

        fun setSelectedProvider(provider: String) {
            viewModelScope.launch { themeRepository.setMetadataProvider(offeredMetadataSource(provider)) }
        }

        fun setSelectedFixAction(action: MetadataFixAction) {
            if (!action.isSupported) return
            viewModelScope.launch { themeRepository.setMetadataFixAction(action.name) }
        }

        private fun buildSuccessDetails(
            oldTitle: String,
            oldArtist: String,
            oldAlbum: String,
            newTitle: String,
            newArtist: String,
            newAlbum: String,
            oldFileName: String,
            newFileName: String?,
            renameError: String? = null,
            isManual: Boolean = false,
            extraChanges: List<TagChange> = emptyList(),
        ): com.lochanpthakke.empee3.core.model.SuccessDetails {
            val changes = mutableListOf<com.lochanpthakke.empee3.core.model.TagChange>()
            if (oldTitle.trim() != newTitle.trim()) {
                changes.add(com.lochanpthakke.empee3.core.model.TagChange("Title", oldTitle, newTitle))
            }
            if (oldArtist.trim() != newArtist.trim()) {
                changes.add(com.lochanpthakke.empee3.core.model.TagChange("Artist", oldArtist, newArtist))
            }
            if (oldAlbum.trim() != newAlbum.trim()) {
                changes.add(com.lochanpthakke.empee3.core.model.TagChange("Album", oldAlbum, newAlbum))
            }
            changes += extraChanges
            val isRenamed = newFileName != null && newFileName != oldFileName
            return com.lochanpthakke.empee3.core.model.SuccessDetails(
                originalTitle = oldTitle,
                originalArtist = oldArtist,
                originalAlbum = oldAlbum,
                updatedTitle = newTitle,
                updatedArtist = newArtist,
                updatedAlbum = newAlbum,
                changes = changes,
                isManualSelection = isManual,
                isRenamed = isRenamed,
                oldFilename = oldFileName,
                newFilename = newFileName,
                renameError = renameError,
            )
        }

        // Library search (title / artist / album), live-updating with the database.
        private val _librarySearchQuery = MutableStateFlow("")
        val librarySearchQuery: StateFlow<String> = _librarySearchQuery.asStateFlow()

        private val settledSearchQuery =
            _librarySearchQuery
                .debounce(SEARCH_DEBOUNCE_MS)
                .map { it.trim() }
                .distinctUntilChanged()

        val librarySearchResults: StateFlow<List<Song>> =
            settledSearchQuery
                .flatMapLatest { query ->
                    if (query.isEmpty()) flowOf(emptyList()) else libraryRepository.searchSongs(query)
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

        /** Library search: albums whose title or artist matches, from the local album list. */
        val librarySearchAlbums: StateFlow<List<Album>> =
            settledSearchQuery
                .flatMapLatest { query ->
                    if (query.isEmpty()) {
                        flowOf(emptyList())
                    } else {
                        libraryRepository.getAllAlbums().map { albums ->
                            albums
                                .filter { it.title.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true) }
                                .take(SEARCH_SECTION_LIMIT)
                        }
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

        /** Library search: artists whose name matches, from the local artist list. */
        val librarySearchArtists: StateFlow<List<Artist>> =
            settledSearchQuery
                .flatMapLatest { query ->
                    if (query.isEmpty()) {
                        flowOf(emptyList())
                    } else {
                        libraryRepository.getAllArtists().map { artists ->
                            artists.filter { it.name.contains(query, ignoreCase = true) }.take(SEARCH_SECTION_LIMIT)
                        }
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

        /**
         * Artist profile artwork (artist id -> artwork uri), strictly loaded from
         * official artist profile images (Deezer API). Album or track covers are
         * NEVER used as artist profile pictures.
         */
        val artistArtwork: StateFlow<Map<Long, String>> =
            libraryRepository
                .getAllArtists()
                .map { artists ->
                    artists
                        .mapNotNull { artist ->
                            artist.artworkUri?.takeIf(ArtistArtworkCache::hasArtwork)?.let { artist.id to it }
                        }.toMap()
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyMap())

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

        private val onlineLyricsView = MutableStateFlow<OnlineLyricsView?>(null)
        private val manualLyricsSearch = MutableStateFlow<ManualLyricsSearch>(ManualLyricsSearch.Idle)
        private var lyricsLookupJob: Job? = null

        /**
         * Lyrics of the playing song, with the song they belong to (so a new song never shows the
         * previous song's lyrics while its own are read): the file's own lyrics when it has readable
         * ones, otherwise what was found online (see [chooseLyrics]).
         */
        val currentSongLyrics: StateFlow<SongLyrics?> =
            currentSongId
                .flatMapLatest { id ->
                    id?.let { songId ->
                        combine(
                            libraryRepository.observeEmbeddedLyrics(songId),
                            onlineLyricsView,
                            manualLyricsSearch,
                        ) { embedded, online, search ->
                            chooseLyrics(songId, embedded, online, search)
                        }
                    } ?: flowOf(null)
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

        /** What the lyrics page can do: automatic lookup, refresh, manual search and choice, back to the file's lyrics. */
        val lyricsActions =
            LyricsActions(
                onShown = { songId -> if (onlineLyricsView.value?.songId != songId) startLyricsLookup(songId, refresh = false) },
                refresh = { currentSongId.value?.let { startLyricsLookup(it, refresh = true) } },
                search = ::searchLyricsOnline,
                choose = ::chooseOnlineLyrics,
                useFileLyrics = ::useFileLyrics,
                closeSearch = { manualLyricsSearch.value = ManualLyricsSearch.Idle },
            )

        private fun currentTrack(songId: Long) =
            playbackRepository.playbackState.value.currentSong
                ?.takeIf { it.id == songId }
                ?.toLyricsTrack()

        /**
         * Looks the playing song's lyrics up online when the page shows it, unless the file has readable
         * lyrics of its own (and the user did not pick an online result instead). A newer song cancels
         * the wait for an older one; the shared request still finishes and is cached.
         */
        private fun startLyricsLookup(
            songId: Long,
            refresh: Boolean,
        ) {
            val track = currentTrack(songId) ?: return
            lyricsLookupJob?.cancel()
            manualLyricsSearch.value = ManualLyricsSearch.Idle
            lyricsLookupJob =
                viewModelScope.launch {
                    val previous = onlineLyricsView.value?.takeIf { it.songId == songId }
                    if (!refresh) {
                        val fileLyrics = libraryRepository.observeEmbeddedLyrics(songId).first()
                        val chosen = onlineLyrics.cached(track)?.userChosen == true
                        if (LyricsParser.parse(fileLyrics) != null && !chosen) {
                            onlineLyricsView.value = OnlineLyricsView(songId, LyricsLookup.Idle)
                            return@launch
                        }
                    }
                    onlineLyricsView.value =
                        OnlineLyricsView(
                            songId,
                            LyricsLookup.Searching,
                            previous?.candidate,
                            previous?.fromCache == true,
                            previous?.userChosen == true,
                        )
                    val result = onlineLyrics.lookup(track, refresh)
                    if (currentSongId.value == songId) onlineLyricsView.value = result.toView(songId, previous?.candidate)
                }
        }

        private fun searchLyricsOnline(text: String) {
            val songId = currentSongId.value ?: return
            val track = currentTrack(songId) ?: return
            manualLyricsSearch.value = ManualLyricsSearch.Searching
            viewModelScope.launch {
                manualLyricsSearch.value =
                    when (val response = onlineLyrics.search(track, text)) {
                        is ProviderResponse.Success -> ManualLyricsSearch.Results(response.value)
                        ProviderResponse.NotFound -> ManualLyricsSearch.Results(emptyList())
                        ProviderResponse.Offline -> ManualLyricsSearch.Error("You are offline.")
                        is ProviderResponse.RateLimited -> ManualLyricsSearch.Error("LRCLIB asked to wait a moment. Try again shortly.")
                        is ProviderResponse.Failed -> ManualLyricsSearch.Error("The search failed (${response.reason}).")
                    }
            }
        }

        /** The user picked a result: it is shown, kept, and preferred for this song (the file is never changed). */
        private fun chooseOnlineLyrics(candidate: LyricsCandidate) {
            val songId = currentSongId.value ?: return
            val track = currentTrack(songId) ?: return
            lyricsLookupJob?.cancel()
            manualLyricsSearch.value = ManualLyricsSearch.Idle
            viewModelScope.launch {
                val found = onlineLyrics.choose(track, candidate)
                onlineLyricsView.value = found.toView(songId, null)
            }
        }

        /** Back to the file's own lyrics: the online result for this song is forgotten. */
        private fun useFileLyrics() {
            val songId = currentSongId.value ?: return
            val track = currentTrack(songId) ?: return
            lyricsLookupJob?.cancel()
            viewModelScope.launch {
                onlineLyrics.forget(track)
                onlineLyricsView.value = OnlineLyricsView(songId, LyricsLookup.Idle)
            }
        }

        private val _writeAccessRequest = MutableStateFlow<WriteAuthorization?>(null)

        /** Non-null while a tag write waits for the user to grant write access. */
        val writeAccessRequest: StateFlow<WriteAuthorization?> = _writeAccessRequest.asStateFlow()
        private var pendingWrite: PendingWrite? = null

        private val _userMessage = MutableStateFlow<String?>(null)
        val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

        private val _artworkQueue = MutableStateFlow<List<ArtworkQueueItem>>(emptyList())
        val artworkQueue: StateFlow<List<ArtworkQueueItem>> = _artworkQueue.asStateFlow()

        private val _artworkCandidates = MutableStateFlow<List<String>>(emptyList())
        val artworkCandidates: StateFlow<List<String>> = _artworkCandidates.asStateFlow()

        private val _selectedEnlargedArtworkUrl = MutableStateFlow<String?>(null)
        val selectedEnlargedArtworkUrl: StateFlow<String?> = _selectedEnlargedArtworkUrl.asStateFlow()

        private val _currentProcessingAlbum = MutableStateFlow<Album?>(null)
        val currentProcessingAlbum: StateFlow<Album?> = _currentProcessingAlbum.asStateFlow()

        private val _isSearchingArtwork = MutableStateFlow(false)
        val isSearchingArtwork: StateFlow<Boolean> = _isSearchingArtwork.asStateFlow()

        init {
            triggerScan()
            resolveArtistArtwork()
        }

        /** Keys of artists whose image the user chose (custom artwork always wins over Deezer). */
        val customArtworkArtistKeys: StateFlow<Set<String>> =
            artistArtworkRepository
                .observeCustomArtworkKeys()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptySet())

        private fun resolveArtistArtwork() {
            viewModelScope.launch(Dispatchers.IO) { artistArtworkUpdater.updateDueArtists(System.currentTimeMillis()) }
        }

        /** Saves [imageBytes] (an encoded, cropped image) as [artist]'s own picture. */
        fun setCustomArtistArtwork(
            artist: Artist,
            imageBytes: ByteArray,
        ) {
            viewModelScope.launch(Dispatchers.IO) {
                (artistArtworkRepository.setCustomArtwork(artist.id, imageBytes) as? AppResult.Error)?.let {
                    _userMessage.value = it.error.message
                }
            }
        }

        /** Removes [artist]'s own picture, then lets the automatic Deezer lookup decide again. */
        fun clearCustomArtistArtwork(artist: Artist) {
            viewModelScope.launch(Dispatchers.IO) {
                when (val result = artistArtworkRepository.clearCustomArtwork(artist.id)) {
                    is AppResult.Error -> _userMessage.value = result.error.message
                    is AppResult.Success -> artistArtworkUpdater.lookUp(artist)
                }
            }
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

        fun enqueueAlbumForArtwork(album: Album) {
            val currentQueue = _artworkQueue.value.toMutableList()
            if (currentQueue.none { it.album.id == album.id }) {
                currentQueue.add(ArtworkQueueItem(album = album, status = "PENDING"))
                _artworkQueue.value = currentQueue
            }
        }

        fun removeFromArtworkQueue(album: Album) {
            val currentQueue = _artworkQueue.value.toMutableList()
            currentQueue.removeAll { it.album.id == album.id }
            _artworkQueue.value = currentQueue
        }

        fun clearArtworkQueue() {
            _artworkQueue.value = emptyList()
        }

        fun scanForMissingCovers() {
            viewModelScope.launch {
                isScanningLocal.value = true
                val allAlbums = libraryRepository.getAllAlbums().first()
                val missingCoverAlbums =
                    allAlbums.filter { album ->
                        album.artworkUri.isNullOrBlank()
                    }
                _artworkQueue.value = missingCoverAlbums.map { ArtworkQueueItem(album = it, status = "PENDING") }
                delay(SCAN_INDICATOR_DELAY_MS)
                isScanningLocal.value = false
            }
        }

        fun selectEnlargedArtwork(url: String?) {
            _selectedEnlargedArtworkUrl.value = url
        }

        fun searchArtworkForAlbum(album: Album) {
            viewModelScope.launch {
                _isSearchingArtwork.value = true
                _currentProcessingAlbum.value = album
                val result = metadataRepository.fetchAlbumArtCandidates(album.title, album.artist, "ALL")
                val artUrls = result.getOrDefault(emptyList())
                _artworkCandidates.value = artUrls
                _isSearchingArtwork.value = false
            }
        }

        @Suppress("TooGenericExceptionCaught", "detekt:UnusedParameter")
        fun applyArtworkToAlbum(
            album: Album,
            artworkUrl: String,
        ) {
            viewModelScope.launch {
                _isSearchingArtwork.value = true
                val queue = _artworkQueue.value.toMutableList()
                val queueIndex = queue.indexOfFirst { it.album.id == album.id }
                if (queueIndex != -1) {
                    queue[queueIndex] = queue[queueIndex].copy(status = "WRITING")
                    _artworkQueue.value = queue.toList()
                }

                try {
                    val albumSongs = libraryRepository.getSongsByAlbumId(album.id).first()
                    for (song in albumSongs) {
                        val req =
                            TagWriteRequest(
                                contentUri = song.uri,
                                filePath = song.uri.removePrefix("file://"),
                                title = song.title,
                                artist = song.artist,
                                album = song.album,
                            )
                        metadataRepository.writeTagsToFile(req)
                    }

                    if (queueIndex != -1) {
                        queue[queueIndex] = queue[queueIndex].copy(status = "COMPLETED")
                        _artworkQueue.value = queue.toList()
                    }
                    libraryRepository.triggerLibraryScan()
                } catch (e: Throwable) {
                    if (queueIndex != -1) {
                        queue[queueIndex] =
                            queue[queueIndex].copy(
                                status = "FAILED",
                                failureReason = e.message ?: "WRITE_ERROR",
                            )
                        _artworkQueue.value = queue.toList()
                    }
                } finally {
                    _selectedEnlargedArtworkUrl.value = null
                    _artworkCandidates.value = emptyList()
                    _currentProcessingAlbum.value = null
                    _isSearchingArtwork.value = false
                }
            }
        }

        fun scanForBadTracks() {
            viewModelScope.launch {
                _isFindingBadTracks.value = true
                val allSongs = libraryRepository.getAllSongs().first()
                val force = _forceFixTags.value
                val activeLearnedWatermarks = learnedWatermarkDao.getAllActiveTokensSync()
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
                                activeLearnedWatermarks.any { token ->
                                    song.title.contains(token, ignoreCase = true)
                                }
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

        fun retryRepairQueueItem(song: Song) {
            val currentQueue = _repairQueue.value.toMutableList()
            val index = currentQueue.indexOfFirst { it.song.id == song.id }
            if (index != -1) {
                currentQueue[index] = currentQueue[index].copy(status = "PENDING", failureReason = null)
                _repairQueue.value = currentQueue
            } else {
                enqueueSongForRepair(song)
            }
            if (!_isFixingQueue.value) {
                startFixingQueue()
            }
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
            val action = selectedFixAction.value
            if (!action.isSupported) {
                _userMessage.value = action.unsupportedReason
                return
            }
            viewModelScope.launch {
                _isFixingQueue.value = true
                val queue = _repairQueue.value.toMutableList()
                val writes = mutableMapOf<Int, TagWriteRequest>()
                val plans = mutableMapOf<Int, FieldFixPlan>()
                val provider = selectedProvider.value

                for (i in queue.indices) {
                    val item = queue[i]
                    if (item.status != "PENDING") continue
                    queue[i] = item.copy(status = "FIXING")
                    _repairQueue.value = queue.toList()
                    delay(REPAIR_DELAY_MS)

                    queue[i] =
                        try {
                            val filePath = libraryRepository.getSongFilePath(item.song.id)
                            val rawFileName = filePath?.let { java.io.File(it).name } ?: item.song.title
                            val activeLearnedTokens = learnedWatermarkDao.getAllActiveTokensSync()
                            val cleanerResult = (metadataRepository as? com.lochanpthakke.empee3.core.network.repository.MetadataRepositoryImpl)?.let {
                                metadataRepository.cleanFilename(rawFileName, activeLearnedTokens)
                            }
                            val searchTitle = cleanerResult?.title?.ifBlank { item.song.title } ?: item.song.title
                            val searchArtist = cleanerResult?.artistGuess ?: item.song.artist.takeIf { it != "Unknown Artist" }

                            val candidateTitles = titleCandidateGenerator.generateCandidates(rawFileName, activeLearnedTokens)
                            var detailedResponse: com.lochanpthakke.empee3.core.network.repository.DetailedSearchResponse? = null
                            var best: com.lochanpthakke.empee3.core.model.MetadataCandidate? = null
                            var successfulTitle: String = searchTitle

                            for (queryTitle in candidateTitles) {
                                val queryArtist = if (!searchArtist.isNullOrBlank() && queryTitle.equals(searchArtist, ignoreCase = true)) null else searchArtist
                                val res = metadataRepository.searchAndScoreTrackDetailed(
                                    title = queryTitle,
                                    artist = queryArtist,
                                    album = item.song.album,
                                    durationMs = item.song.durationMs,
                                    sourceProvider = provider,
                                ).getOrDefault(com.lochanpthakke.empee3.core.network.repository.DetailedSearchResponse(emptyList(), emptyList()))

                                val top = res.candidates.firstOrNull()
                                if (top != null) {
                                    if (best == null || top.confidenceScore > best.confidenceScore) {
                                        best = top
                                        detailedResponse = res
                                        successfulTitle = queryTitle
                                    }
                                    if (top.confidenceScore >= HIGH_CONFIDENCE_THRESHOLD) {
                                        break
                                    }
                                } else if (detailedResponse == null) {
                                    detailedResponse = res
                                }
                            }

                            val candidates = detailedResponse?.candidates ?: emptyList()
                            val outcomes = detailedResponse?.providerOutcomes ?: emptyList()

                            val finalStatus: String
                            val finalReason: String?
                            val tagRequest: TagWriteRequest?

                            when {
                                filePath == null -> {
                                    finalStatus = "FAILED"
                                    finalReason = "FILE_NOT_FOUND"
                                    tagRequest = null
                                }
                                best == null || best.confidenceScore < UNCERTAIN_THRESHOLD -> {
                                    finalStatus = "FAILED"
                                    finalReason = (if (best == null) providerFailureSummary(outcomes) else null) ?: "NO_MATCH"
                                    tagRequest = null
                                }
                                best.confidenceScore < HIGH_CONFIDENCE_THRESHOLD -> {
                                    finalStatus = "NEEDS_REVIEW"
                                    finalReason = "LOW_CONFIDENCE"
                                    tagRequest = null
                                }
                                else -> {
                                    val plan = planFix(item.song, filePath, best, candidates, action)
                                    plans[i] = plan
                                    if (plan.request == null) {
                                        // Nothing to write is not a fix: say why instead of reporting success.
                                        finalStatus = "UNCHANGED"
                                        finalReason = plan.notes.joinToString("; ").ifBlank { "The tags already match the online match" }
                                        tagRequest = null
                                    } else {
                                        finalStatus = "FIXING"
                                        finalReason = null
                                        tagRequest = plan.request
                                    }
                                }
                            }

                            val diag = com.lochanpthakke.empee3.core.model.RepairDiagnostics(
                                originalFilename = rawFileName,
                                searchTitle = successfulTitle,
                                searchArtist = searchArtist,
                                targetDurationMs = item.song.durationMs.takeIf { it > 0 },
                                providerOutcomes = outcomes,
                                topCandidates = candidates.take(3),
                                finalReason = finalReason,
                            )

                            if (tagRequest != null) {
                                writes[i] = tagRequest
                            }

                            item.copy(status = finalStatus, failureReason = finalReason, diagnostics = diag)
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
                                        // The file is named after the title: rename only when the title changed.
                                        val renameRes =
                                            request.title?.let { newTitle ->
                                                val ext = request.filePath.substringAfterLast('.', "mp3")
                                                val targetFileName = fileRenamer.buildTargetFileName(newTitle, ext)
                                                fileRenamer.renameAudioFile(request.contentUri, request.filePath, targetFileName)
                                            }
                                        val newPath = (renameRes as? AppResult.Success)?.data?.newFilePath
                                        val newName = (renameRes as? AppResult.Success)?.data?.newFileName
                                        if (newPath != null) {
                                            libraryRepository.updateFilePath(item.song.id, newPath)
                                        }
                                        val successDetails = buildSuccessDetails(
                                            oldTitle = item.song.title,
                                            oldArtist = item.song.artist,
                                            oldAlbum = item.song.album,
                                            newTitle = request.title ?: item.song.title,
                                            newArtist = request.artist ?: item.song.artist,
                                            newAlbum = request.album ?: item.song.album,
                                            oldFileName = java.io.File(request.filePath).name,
                                            newFileName = newName,
                                            renameError = (renameRes as? AppResult.Error)?.error?.message,
                                            isManual = (_repairMode.value == RepairMode.MANUAL),
                                            extraChanges = plans[index]?.let(::nonTextChanges).orEmpty(),
                                        )
                                        val itemStatus = if (renameRes is AppResult.Error) "PARTIAL_SUCCESS" else "DONE"
                                        item.copy(status = itemStatus, successDetails = successDetails)
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

        /** [origin]: the album or playlist page this was started from (its transition settings apply), or null. */
        fun playSongsFromQueue(
            songs: List<Song>,
            startIndex: Int,
            origin: CollectionRef? = null,
        ) {
            playbackRepository.playCollection(songs, startIndex, origin)
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

        /**
         * The album last tapped in a list, with its tracks: its page is complete on its first
         * frame (before Room's own flows answer), so nothing is added while it slides in.
         */
        private var openingAlbum: Pair<Album, List<Song>?>? = null

        /** Reads the tapped album's tracks (briefly: navigation waits for them), then remembers both. */
        suspend fun prepareAlbum(album: Album) {
            val songs = withTimeoutOrNull(PREPARE_ALBUM_TIMEOUT_MS) { libraryRepository.getSongsByAlbumId(album.id).first() }
            openingAlbum = album to songs
        }

        fun openingAlbum(albumId: Long): Pair<Album, List<Song>?>? = openingAlbum?.takeIf { it.first.id == albumId }

        /** The album's songs in album order (disc, track, title). */
        fun observeAlbumSongs(albumId: Long): Flow<List<Song>> = libraryRepository.getSongsByAlbumId(albumId)

        /** The artist, or null once it is gone from the library. */
        fun observeArtist(artistId: Long): Flow<Artist?> = libraryRepository.getArtistById(artistId)

        /** The artist's albums in reverse chronological order (newest year first). */
        fun observeArtistAlbums(artistId: Long): Flow<List<Album>> = libraryRepository.getAlbumsByArtistId(artistId)

        /** The artist's primary songs (where role = PRIMARY). */
        fun observeArtistPrimarySongs(artistId: Long): Flow<List<Song>> = libraryRepository.getPrimarySongsByArtistId(artistId)

        /** The artist's featured appearances (where role = FEATURED). */
        fun observeArtistFeaturedSongs(artistId: Long): Flow<List<Song>> = libraryRepository.getFeaturedSongsByArtistId(artistId)

        /** Tracks where this artist's exact name sits inside an unresolved credit (display only, never a credit). */
        fun observeArtistUnverifiedSongs(artistId: Long): Flow<List<Song>> =
            libraryRepository.getArtistById(artistId).flatMapLatest { artist ->
                val key = artist?.artistKey.orEmpty()
                if (key.isEmpty()) flowOf(emptyList()) else libraryRepository.getUnverifiedSongsByArtistKey(key)
            }

        /** Plays [songs] in their order from the first track, with shuffle turned off. */
        fun playInOrder(
            songs: List<Song>,
            origin: CollectionRef? = null,
        ) {
            if (songs.isEmpty()) return
            playbackRepository.setShuffleModeEnabled(false)
            playbackRepository.playCollection(songs, 0, origin)
        }

        /** Plays [songs] shuffled: the same shuffle mode the playlists use. */
        fun playShuffled(
            songs: List<Song>,
            origin: CollectionRef? = null,
        ) {
            if (songs.isEmpty()) return
            playbackRepository.setShuffleModeEnabled(true)
            playbackRepository.playCollection(songs, songs.indices.random(), origin)
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
                val result =
                    metadataRepository.searchTrack(
                        title = song.title,
                        artist = song.artist,
                        album = song.album,
                        sourceProvider = selectedProvider.value,
                    )
                _metadataCandidates.value = result.getOrDefault(emptyList())
            }
        }

        /**
         * Plans the tag edit for a confident [match]: reads the file's current genre and cover, looks
         * up a genre and downloads the cover only when [action] could use them, then lets
         * [planFieldFix] decide field by field. Lookup failures become notes, never empty values.
         */
        private suspend fun planFix(
            song: Song,
            filePath: String,
            match: MetadataCandidate,
            candidates: List<MetadataCandidate>,
            action: MetadataFixAction,
        ): FieldFixPlan {
            val fields = action.fields()
            val fillOnly = action == MetadataFixAction.ALL_FIELDS
            val local = localTags(song, filePath, checkDateFormat = FixField.YEAR in fields)
            // Specific reasons found here replace the plan's generic "not found" notes.
            val notes = mutableMapOf<String, String>()
            val wantsGenre = FixField.GENRE in fields && (!fillOnly || (local.tagsReadable && !isUsableTag(local.genre)))
            val genre = if (wantsGenre) onlineGenre(match, notes) else null
            val wantsArtwork = FixField.ARTWORK in fields && (!fillOnly || (local.tagsReadable && !local.hasArtwork))
            val artwork = if (wantsArtwork) onlineCover(match, notes) else null
            val wantsYear = FixField.YEAR in fields && (!fillOnly || (local.tagsReadable && !isUsableYearTag(local.year)))
            val releaseDate = if (wantsYear) onlineReleaseDate(match, candidates, notes) else null

            val plan = planFieldFix(song.uri, filePath, action, local, match, genre, artwork, releaseDate)
            return plan.copy(notes = plan.notes.map { notes[it] ?: it })
        }

        /** What the file has now, read from its embedded tags (the library's title / artist / album). */
        private suspend fun localTags(
            song: Song,
            filePath: String,
            checkDateFormat: Boolean,
        ): LocalTrackTags {
            val embedded = embeddedTagReader.readEmbeddedTags(filePath)
            return LocalTrackTags(
                title = song.title,
                artist = song.artist,
                album = song.album,
                genre = embedded.genre,
                hasArtwork = embedded.artworkBytes != null,
                tagsReadable = embedded.hasEmbeddedTag,
                year = embedded.year,
                canStoreYearMonth = checkDateFormat && embeddedTagReader.canStoreYearMonth(filePath),
            )
        }

        /** The match's release date if it is valid and unambiguous, or null with the reason in [notes]. */
        private fun onlineReleaseDate(
            match: MetadataCandidate,
            candidates: List<MetadataCandidate>,
            notes: MutableMap<String, String>,
        ): ReleaseDate? =
            when (val decision = releaseDateFor(match, candidates, LocalDate.now(), HIGH_CONFIDENCE_THRESHOLD)) {
                is ReleaseDateDecision.Found -> decision.date
                is ReleaseDateDecision.Rejected -> null.also { notes[NO_YEAR_NOTE] = "No year: ${decision.reason}" }
            }

        /** The match's genre, or null with the reason in [notes]. */
        private suspend fun onlineGenre(
            match: MetadataCandidate,
            notes: MutableMap<String, String>,
        ): String? =
            when (val lookup = metadataRepository.lookupGenre(match)) {
                is GenreLookup.Found -> lookup.genre
                is GenreLookup.NotFound -> null.also { notes[NO_GENRE_NOTE] = "No genre: ${lookup.reason}" }
                is GenreLookup.Failed -> null.also { notes[NO_GENRE_NOTE] = "Genre lookup failed: ${lookup.reason}" }
            }

        /** The match's cover image, or null with the reason in [notes]. */
        private suspend fun onlineCover(
            match: MetadataCandidate,
            notes: MutableMap<String, String>,
        ): ByteArray? {
            val url =
                match.coverArtUrl
                    ?: return null.also { notes[NO_ARTWORK_NOTE] = "No cover: ${match.sourceName} gave none for this match" }
            return when (val download = metadataRepository.downloadArtwork(url)) {
                is AppResult.Success -> download.data
                is AppResult.Error -> null.also { notes[NO_ARTWORK_NOTE] = "No cover: ${download.error.message}" }
            }
        }

        private fun nonTextChanges(plan: FieldFixPlan): List<TagChange> =
            plan.changes
                .filter { it.field == FixField.YEAR || it.field == FixField.GENRE || it.field == FixField.ARTWORK }
                .map { change ->
                    val label =
                        when (change.field) {
                            FixField.YEAR -> "Year"
                            FixField.GENRE -> "Genre"
                            else -> "Artwork"
                        }
                    TagChange(label, change.oldValue, change.newValue)
                }

        /** When every queried provider failed (not merely found nothing), the failure, so it isn't shown as "no match". */
        private fun providerFailureSummary(outcomes: List<com.lochanpthakke.empee3.core.model.ProviderOutcome>): String? {
            val queried = outcomes.filter { it.status != com.lochanpthakke.empee3.core.model.ProviderStatus.SKIPPED }
            val failed =
                queried.filter {
                    it.status == com.lochanpthakke.empee3.core.model.ProviderStatus.FAILED ||
                        it.status == com.lochanpthakke.empee3.core.model.ProviderStatus.TIMED_OUT
                }
            return when {
                queried.isEmpty() -> "No provider could be queried for this source"
                failed.size != queried.size -> null
                else -> "Lookup failed: " + failed.joinToString("; ") { "${it.providerName}: ${it.errorMessage ?: it.status.label}" }
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
            bpm: Int = 0,
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
                        bpm = bpm,
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
                                val ext = request.filePath.substringAfterLast('.', "mp3")
                                val targetFileName = fileRenamer.buildTargetFileName(request.title ?: song.title, ext)
                                val renameRes = fileRenamer.renameAudioFile(request.contentUri, request.filePath, targetFileName)
                                val newPath = (renameRes as? AppResult.Success)?.data?.newFilePath
                                if (newPath != null) {
                                    libraryRepository.updateFilePath(song.id, newPath)
                                }
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
            // The plan's generic notes, replaced by the specific reason when there is one.
            private const val NO_GENRE_NOTE = "No genre found online"
            private const val NO_ARTWORK_NOTE = "No artwork found online"
            private const val NO_YEAR_NOTE = "No release year found online"

            /** Opening an album waits at most this long for its tracks (then they load as before). */
            const val PREPARE_ALBUM_TIMEOUT_MS = 150L

            private const val SCAN_INDICATOR_DELAY_MS = 1000L
            private const val REPAIR_DELAY_MS = 600L
            private const val SEARCH_DEBOUNCE_MS = 250L
            private const val SEARCH_SECTION_LIMIT = 12
            private const val STOP_TIMEOUT_MS = 5_000L
            private const val RECENT_ALBUMS_LIMIT = 12
        }
    }
