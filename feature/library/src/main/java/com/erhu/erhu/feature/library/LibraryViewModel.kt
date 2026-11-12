package com.erhu.erhu.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.erhu.erhu.core.common.locale.AppLocaleManager
import com.erhu.erhu.core.common.locale.LocaleFormatters
import com.erhu.erhu.core.common.locale.localizedMessage
import com.erhu.erhu.core.common.result.AppError
import com.erhu.erhu.core.common.result.AppResult
import com.erhu.erhu.core.common.result.getOrDefault
import com.erhu.erhu.core.database.library.ArtistArtworkCache
import com.erhu.erhu.core.database.repository.AnimatedArtworkRepository
import com.erhu.erhu.core.database.repository.ArtistArtworkRepository
import com.erhu.erhu.core.database.repository.LibraryRepository
import com.erhu.erhu.core.database.repository.RecentlyPlayedRecorder
import com.erhu.erhu.core.designsystem.artwork.ArtworkLoader
import com.erhu.erhu.core.model.Album
import com.erhu.erhu.core.model.Artist
import com.erhu.erhu.core.model.CollectionRef
import com.erhu.erhu.core.model.MetadataCandidate
import com.erhu.erhu.core.model.MetadataField
import com.erhu.erhu.core.model.PersistedRepairBatch
import com.erhu.erhu.core.model.PersistedRepairItem
import com.erhu.erhu.core.model.ProviderStatus
import com.erhu.erhu.core.model.Song
import com.erhu.erhu.core.network.lyrics.CachedLyrics
import com.erhu.erhu.core.network.lyrics.LyricsCandidate
import com.erhu.erhu.core.network.lyrics.LyricsTrack
import com.erhu.erhu.core.network.lyrics.OnlineLyricsRepository
import com.erhu.erhu.core.network.lyrics.OnlineLyricsResult
import com.erhu.erhu.core.network.lyrics.ProviderResponse
import com.erhu.erhu.core.network.repository.MetadataRepository
import com.erhu.erhu.core.network.scorer.HIGH_CONFIDENCE_THRESHOLD
import com.erhu.erhu.core.network.tagger.EditableTags
import com.erhu.erhu.core.network.tagger.EmbeddedTagReader
import com.erhu.erhu.core.network.tagger.PLAUSIBLE_BPM
import com.erhu.erhu.core.network.tagger.TagWriteRequest
import com.erhu.erhu.core.network.tagger.buildTagWriteRequest
import com.erhu.erhu.core.network.tagger.fieldSelection
import com.erhu.erhu.core.playback.repository.PlaybackRepository
import com.erhu.erhu.feature.library.mix.RadioPlan
import com.erhu.erhu.feature.library.mix.RadioRepository
import com.erhu.erhu.feature.library.mix.RadioSeed
import com.erhu.erhu.feature.library.mix.RadioSignal
import com.erhu.erhu.feature.library.mix.SmartMixRepository
import com.erhu.erhu.feature.library.mix.SmartMixes
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import com.erhu.erhu.core.common.R as CommonR

data class RepairQueueItem(
    val song: Song,
    val status: String = "PENDING", // PENDING, FIXING, DONE, NEEDS_REVIEW, FAILED
    val failureReason: String? = null,
    val diagnostics: com.erhu.erhu.core.model.RepairDiagnostics? = null,
    val successDetails: com.erhu.erhu.core.model.SuccessDetails? = null,
)

/** Which library list a tapped song belongs to â€” that list becomes the play queue. */
enum class SongSource {
    ALL_SONGS,
    FAVORITES,
}

/** What the repair queue's "select all" toggle added: [matched] songs for [query] ("" = whole library), [songIds] new to the queue. */
data class SelectAllSelection(
    val query: String,
    val matched: Int,
    val songIds: Set<Long>,
)

data class ArtworkCandidateDetail(
    val url: String,
    val sourceProvider: String,
    val isHighRes: Boolean = false,
    val matchedAlbum: String = "",
    val matchedArtist: String = "",
    val edition: String = "",
    val year: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
    val matchScore: Double = 0.0,
    val reviewReasons: List<String> = emptyList(),
    val recommended: Boolean = false,
    val previewUrl: String = url,
)

data class ArtworkSearchDiagnostics(
    val targetName: String,
    val searchQuery: String = "",
    val resolvedArtistInfo: String? = null,
    val providerOutcomes: List<com.erhu.erhu.core.model.ProviderOutcome> = emptyList(),
    val candidates: List<ArtworkCandidateDetail> = emptyList(),
)

data class ArtworkQueueItem(
    val album: Album,
    val status: String = "PENDING", // PENDING, SEARCHING, ARTWORK_FOUND, WRITING, COMPLETED, FAILED
    val failureReason: String? = null,
)

data class ArtistArtworkQueueItem(
    val artist: Artist,
    val status: String = "PENDING", // PENDING, SEARCHING, WRITING, COMPLETED, FAILED
    val failureReason: String? = null,
)

sealed interface RecentSearchItem {
    val key: String

    data class AlbumItem(
        val album: Album,
    ) : RecentSearchItem {
        override val key: String get() = "album_${album.id}"
    }

    data class ArtistItem(
        val artist: Artist,
    ) : RecentSearchItem {
        override val key: String get() = "artist_${artist.id}"
    }

    data class SongItem(
        val song: Song,
    ) : RecentSearchItem {
        override val key: String get() = "song_${song.id}"
    }

    data class PlaylistItem(
        val playlistId: Long,
        val title: String,
        val trackCount: Int,
    ) : RecentSearchItem {
        override val key: String get() = "playlist_$playlistId"
    }
}

@HiltViewModel
@Suppress("TooManyFunctions", "LargeClass")
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class LibraryViewModel
    @Inject
    constructor(
        @ApplicationContext private val appContext: android.content.Context,
        val libraryRepository: LibraryRepository,
        val playbackRepository: PlaybackRepository,
        val animatedArtworkRepository: AnimatedArtworkRepository,
        private val metadataRepository: MetadataRepository,
        private val themeRepository: com.erhu.erhu.core.datastore.repository.ThemeRepository,
        private val artistArtworkRepository: ArtistArtworkRepository,
        private val artistArtworkUpdater: ArtistArtworkUpdater,
        recentlyPlayed: RecentlyPlayedRecorder,
        private val smartMixRepository: SmartMixRepository,
        private val onlineLyrics: OnlineLyricsRepository,
        private val learnedWatermarkDao: com.erhu.erhu.core.database.dao.LearnedWatermarkDao,
        private val fileRenamer: com.erhu.erhu.core.network.tagger.FileRenamer,
        private val embeddedTagReader: EmbeddedTagReader,
        val metadataRepair: MetadataRepairOperation,
        private val metadataWriteAccess: MetadataWriteAccess,
        private val radioRepository: RadioRepository,
        private val lyricsTimingPersistence: com.erhu.erhu.core.datastore.LyricsTimingPersistence,
    ) : ViewModel() {
        internal val artworkUpgrades =
            ArtworkUpgradeController(
                appContext,
                viewModelScope,
                libraryRepository,
                metadataRepository,
                artistArtworkRepository,
                writeAccess = { uris, granted, denied ->
                    check(
                        !metadataWriteAccess.isPending && !_isFixingQueue.value,
                    ) { "Another tag write is in progress. Wait for it to finish and try again." }
                    withWriteAccess(uris, granted, denied)
                },
            )

        /**
         * The smart mixes on the Tracks page: built from local listening history and tags, cached
         * app-wide and rebuilt only when that data changes (null until the first build).
         */
        val smartMixes: StateFlow<SmartMixes?> = smartMixRepository.mixes

        /** Songs the user hid from Smart Playlists (Now Playing's menu). */
        val hiddenFromMixes: StateFlow<Set<Long>> =
            smartMixRepository.hidden.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptySet())

        val preferredLiveCoverResolution: StateFlow<String> =
            animatedArtworkRepository
                .observePreferredResolution()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), "1080p")

        fun setPreferredLiveCoverResolution(resolution: String) {
            viewModelScope.launch {
                animatedArtworkRepository.setPreferredResolution(resolution)
            }
        }

        /** Hides [songId] from (or returns it to) Smart Playlists; the song itself is untouched. */
        fun setHiddenFromMixes(
            songId: Long,
            hidden: Boolean,
        ) {
            viewModelScope.launch { smartMixRepository.setHidden(songId, hidden) }
        }

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
        private val scanTimeoutTrigger = MutableStateFlow(false)

        /** Reflects the real WorkManager scan state, guarded by a timeout on startup. */
        val isScanning: StateFlow<Boolean> =
            libraryRepository
                .observeIsScanning()
                .combine(isScanningLocal) { libScan, localScan -> libScan || localScan }
                .combine(scanTimeoutTrigger) { scanning, timedOut -> scanning && !timedOut }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

        private val _isFindingBadTracks = MutableStateFlow(false)
        val isFindingBadTracks: StateFlow<Boolean> = _isFindingBadTracks.asStateFlow()

        private val _repairQueue = metadataRepair.queue
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

        private val _recentSearches = MutableStateFlow<List<String>>(emptyList())
        val recentSearches: StateFlow<List<String>> = _recentSearches.asStateFlow()

        private val _recentSearchItems = MutableStateFlow<List<RecentSearchItem>>(emptyList())
        val recentSearchItems: StateFlow<List<RecentSearchItem>> = _recentSearchItems.asStateFlow()

        private val _isFixingQueue = metadataRepair.busy
        val isFixingQueue: StateFlow<Boolean> = _isFixingQueue.asStateFlow()

        /** Metadata & Artwork source, stored in settings; one of [metadataSourceOptions]. */
        val selectedProvider: StateFlow<String> =
            themeRepository.metadataProvider
                .map(::offeredMetadataSource)
                .stateIn(viewModelScope, SharingStarted.Eagerly, offeredMetadataSource(themeRepository.metadataProvider.value))

        /**
         * The fields a fix may change (Lochan's multi-select), stored in settings. All tag fields
         * selected = "All Fields" (missing values filled, valid ones kept); fewer = replace those.
         */
        val selectedFields: StateFlow<Set<MetadataField>> =
            themeRepository.metadataFixAction
                .map(MetadataField::fromStored)
                .stateIn(viewModelScope, SharingStarted.Eagerly, MetadataField.fromStored(themeRepository.metadataFixAction.value))

        /** Turns [field] on or off; at least one field stays selected. */
        fun toggleField(field: MetadataField) {
            if (!field.isSupported) return
            val current = selectedFields.value
            val next =
                when {
                    field !in current -> current + field
                    current.size > 1 -> current - field
                    else -> current
                }
            viewModelScope.launch { themeRepository.setMetadataFixAction(MetadataField.toStored(next)) }
        }

        fun selectAllFields() {
            viewModelScope.launch { themeRepository.setMetadataFixAction(MetadataField.toStored(MetadataField.all)) }
        }

        /** Score a match needs to be written automatically (Lochan's slider); 85 % unless the user changes it. */
        val confidenceThreshold: StateFlow<Float> =
            themeRepository.metadataConfidenceThreshold
                .map(::effectiveThreshold)
                .stateIn(viewModelScope, SharingStarted.Eagerly, effectiveThreshold(themeRepository.metadataConfidenceThreshold.value))

        fun setConfidenceThreshold(threshold: Float) {
            viewModelScope.launch { themeRepository.setMetadataConfidenceThreshold(threshold.coerceIn(MIN_THRESHOLD, MAX_THRESHOLD)) }
        }

        enum class RepairMode { AUTOMATIC, MANUAL }

        private val _repairMode = MutableStateFlow(RepairMode.AUTOMATIC)
        val repairMode: StateFlow<RepairMode> = _repairMode.asStateFlow()

        fun setRepairMode(mode: RepairMode) {
            _repairMode.value = mode
        }

        fun setSelectedProvider(provider: String) {
            viewModelScope.launch { themeRepository.setMetadataProvider(offeredMetadataSource(provider)) }
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
        private var lyricsIdentity: LyricsTrack? = null
        private var lyricsGeneration = 0L
        private var lyricsPersistenceJob: Job? = null

        /**
         * Lyrics of the playing song, with the song they belong to (so a new song never shows the
         * previous song's lyrics while its own are read): the file's own lyrics when it has readable
         * ones, otherwise what was found online (see [chooseLyrics]).
         */
        val currentSongLyrics: StateFlow<SongLyrics?> =
            combine(
                currentSongId
                    .flatMapLatest { id ->
                        id?.let { songId ->
                            combine(
                                libraryRepository.observeEmbeddedLyrics(songId),
                                onlineLyricsView,
                                manualLyricsSearch,
                            ) { embedded, online, search ->
                                chooseLyrics(songId, embedded, online.takeIf { lyricsIdentity == currentTrack(songId) }, search)
                            }
                        } ?: flowOf(null)
                    },
                playbackRepository.playbackState.map { it.currentSong }.distinctUntilChanged(),
                lyricsTimingPersistence.observe(),
            ) { lyrics, song, timing -> withLyricsTiming(song, lyrics, timing) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

        /** What the lyrics page can do: automatic lookup, refresh, manual search and choice, back to the file's lyrics. */
        val lyricsActions =
            LyricsActions(
                onShown = { songId -> if (lyricsIdentity != currentTrack(songId)) startLyricsLookup(songId, refresh = false) },
                refresh = { currentSongId.value?.let { startLyricsLookup(it, refresh = true) } },
                search = ::searchLyricsOnline,
                choose = ::chooseOnlineLyrics,
                useFileLyrics = ::useFileLyrics,
                closeSearch = { manualLyricsSearch.value = ManualLyricsSearch.Idle },
                download = { changeLyricsDownload(remove = false) },
                removeDownload = { changeLyricsDownload(remove = true, expected = it) },
                saveTiming = { context, offset ->
                    if (currentSongLyrics.value?.timingContext != context) {
                        AppResult.Error(AppError.ValidationError("The track or lyrics changed. Reopen lyrics timing and try again."))
                    } else {
                        lyricsTimingPersistence.save(context, offset)
                    }
                },
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
            if (lyricsPersistenceJob?.isActive == true && lyricsIdentity == track) return
            lyricsIdentity = track
            val generation = ++lyricsGeneration
            lyricsLookupJob?.cancel()
            manualLyricsSearch.value = ManualLyricsSearch.Idle
            lyricsLookupJob =
                viewModelScope.launch {
                    val previous = onlineLyricsView.value?.takeIf { it.songId == songId }
                    if (!refresh) {
                        val fileLyrics = libraryRepository.observeEmbeddedLyrics(songId).first()
                        val saved = onlineLyrics.cached(track)
                        val chosen = saved?.userChosen == true && !onlineLyrics.prefersEmbedded(track)
                        if (LyricsParser.parse(fileLyrics) is Lyrics.Synced && !chosen) {
                            onlineLyricsView.value = OnlineLyricsView(songId, LyricsLookup.Idle, saved = saved)
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
                            previous?.saved,
                        )
                    val result = onlineLyrics.lookup(track, refresh)
                    if (currentTrack(songId) == track && lyricsGeneration == generation) {
                        val view = result.toView(songId, previous?.candidate)
                        onlineLyricsView.value =
                            view.copy(
                                saved = onlineLyrics.cached(track),
                                userChosen =
                                    if (result is OnlineLyricsResult.Found) {
                                        view.userChosen
                                    } else {
                                        previous?.userChosen ==
                                            true
                                    },
                            )
                    }
                }
        }

        private fun searchLyricsOnline(text: String) {
            val track = currentSongId.value?.let(::currentTrack) ?: return
            val songId = track.songId
            if (lyricsPersistenceJob?.isActive == true) return
            val generation = ++lyricsGeneration
            manualLyricsSearch.value = ManualLyricsSearch.Searching
            viewModelScope.launch {
                val result =
                    when (val response = onlineLyrics.search(track, text)) {
                        is ProviderResponse.Success -> ManualLyricsSearch.Results(response.value)
                        ProviderResponse.NotFound -> ManualLyricsSearch.Results(emptyList())
                        ProviderResponse.Offline -> ManualLyricsSearch.Error("You are offline.")
                        is ProviderResponse.RateLimited -> ManualLyricsSearch.Error("LRCLIB asked to wait a moment. Try again shortly.")
                        is ProviderResponse.Failed -> ManualLyricsSearch.Error("The search failed (${response.reason}).")
                    }
                if (currentTrack(songId) == track && generation == lyricsGeneration) manualLyricsSearch.value = result
            }
        }

        /** The user picked a result: it is shown and preferred this session for this song (the file is never changed). */
        private fun chooseOnlineLyrics(candidate: LyricsCandidate) {
            val track = currentSongId.value?.let(::currentTrack) ?: return
            val songId = track.songId
            if (lyricsIdentity != track || lyricsPersistenceJob?.isActive == true) return
            lyricsIdentity = track
            val generation = ++lyricsGeneration
            lyricsLookupJob?.cancel()
            manualLyricsSearch.value = ManualLyricsSearch.Idle
            viewModelScope.launch {
                val found = onlineLyrics.choose(track, candidate)
                if (currentTrack(songId) == track && generation == lyricsGeneration) onlineLyricsView.value = found.toView(songId, null)
            }
        }

        /** Back to the file's own lyrics without removing saved content. */
        private fun useFileLyrics() {
            val track = currentSongId.value?.let(::currentTrack) ?: return
            val songId = track.songId
            if (lyricsPersistenceJob?.isActive == true) return
            lyricsIdentity = track
            val generation = ++lyricsGeneration
            lyricsLookupJob?.cancel()
            viewModelScope.launch {
                onlineLyrics.forget(track)
                if (currentTrack(songId) == track && generation == lyricsGeneration) {
                    onlineLyricsView.value = OnlineLyricsView(songId, LyricsLookup.Idle, saved = onlineLyrics.cached(track))
                }
            }
        }

        /** Explicit offline storage is independent of the network lookup and embedded Room lyrics. */
        private fun changeLyricsDownload(
            remove: Boolean,
            expected: CachedLyrics? = null,
        ) {
            val track = currentSongId.value?.let(::currentTrack) ?: return
            val songId = track.songId
            val view =
                onlineLyricsView.value?.takeIf {
                    it.songId == songId && lyricsIdentity == track && lyricsPersistenceJob?.isActive != true
                } ?: return
            when {
                remove && view.saved != expected -> {
                    onlineLyricsView.value =
                        view.copy(saveError = localized().getString(CommonR.string.library_lyrics_saved_changed))
                }
                remove && view.saved == null -> Unit
                !remove && view.candidate?.bestText.isNullOrBlank() -> Unit
                else -> persistLyricsDownload(track, view, remove)
            }
        }

        private fun persistLyricsDownload(
            track: LyricsTrack,
            view: OnlineLyricsView,
            remove: Boolean,
        ) {
            val songId = track.songId
            lyricsLookupJob?.cancel()
            val generation = ++lyricsGeneration
            onlineLyricsView.value = view.copy(saving = true, saveError = null)
            lyricsPersistenceJob =
                viewModelScope.launch {
                    try {
                        val saved =
                            if (remove) {
                                onlineLyrics.removeDownload(track, requireNotNull(view.saved))
                                null
                            } else {
                                onlineLyrics.download(track, requireNotNull(view.candidate), view.userChosen, view.saved)
                            }
                        if (currentTrack(songId) == track && lyricsGeneration == generation) {
                            onlineLyricsView.value = view.copy(saved = saved, saving = false, fromCache = saved != null)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: java.io.IOException) {
                        if (currentTrack(songId) == track && lyricsGeneration == generation) {
                            val resId =
                                if (remove) {
                                    CommonR.string.library_lyrics_remove_failed
                                } else {
                                    CommonR.string.library_lyrics_download_failed
                                }
                            onlineLyricsView.value =
                                view.copy(saveError = localized().getString(resId, e.message.orEmpty()))
                        }
                    }
                }
        }

        val writeAccessRequest = metadataWriteAccess.request

        private val _userMessage = MutableStateFlow<String?>(null)
        val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

        private val _bpmMessage = MutableStateFlow<String?>(null)
        val bpmMessage: StateFlow<String?> = _bpmMessage.asStateFlow()

        private fun localized() = AppLocaleManager.localizedContext(appContext)

        fun clearBpmMessage() {
            _bpmMessage.value = null
        }

        private val _artworkQueue = MutableStateFlow<List<ArtworkQueueItem>>(emptyList())
        val artworkQueue: StateFlow<List<ArtworkQueueItem>> = _artworkQueue.asStateFlow()

        private val _artistArtworkQueue = MutableStateFlow<List<ArtistArtworkQueueItem>>(emptyList())
        val artistArtworkQueue: StateFlow<List<ArtistArtworkQueueItem>> = _artistArtworkQueue.asStateFlow()

        private val _artworkCandidates = MutableStateFlow<List<String>>(emptyList())
        val artworkCandidates: StateFlow<List<String>> = _artworkCandidates.asStateFlow()

        private val _selectedEnlargedArtworkUrl = MutableStateFlow<String?>(null)
        val selectedEnlargedArtworkUrl: StateFlow<String?> = _selectedEnlargedArtworkUrl.asStateFlow()

        private val _currentProcessingAlbum = MutableStateFlow<Album?>(null)
        val currentProcessingAlbum: StateFlow<Album?> = _currentProcessingAlbum.asStateFlow()

        private val _currentProcessingArtist = MutableStateFlow<Artist?>(null)
        val currentProcessingArtist: StateFlow<Artist?> = _currentProcessingArtist.asStateFlow()

        private val _minArtworkResolution = MutableStateFlow(500)
        val minArtworkResolution: StateFlow<Int> = _minArtworkResolution.asStateFlow()

        fun setMinArtworkResolution(resolution: Int) {
            _minArtworkResolution.value = resolution
        }

        val activeWatermarks: StateFlow<List<String>> =
            learnedWatermarkDao
                .observeActiveTokens()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

        fun addCustomWatermark(token: String) {
            if (token.isBlank()) return
            viewModelScope.launch(Dispatchers.IO) {
                learnedWatermarkDao.insertOrIncrement(token.trim())
            }
        }

        fun removeWatermark(token: String) {
            viewModelScope.launch(Dispatchers.IO) {
                learnedWatermarkDao.deleteToken(token.trim())
            }
        }

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
                    _userMessage.value = it.error.localizedMessage(localized())
                }
            }
        }

        /** Removes [artist]'s own picture, then lets the automatic Deezer lookup decide again. */
        fun clearCustomArtistArtwork(artist: Artist) {
            viewModelScope.launch(Dispatchers.IO) {
                when (val result = artistArtworkRepository.clearCustomArtwork(artist.id)) {
                    is AppResult.Error -> _userMessage.value = result.error.localizedMessage(localized())
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
            if (_isFixingQueue.value || metadataRepair.awaitingApproval.value) return
            _repairQueue.value = emptyList()
            _selectAllForRepair.value = null
            _batchPause.value = null
        }

        /**
         * The songs the "select all" toggle added to the repair queue, or null while it is off.
         * Kept so turning it off removes exactly those (still pending) and nothing added by hand.
         */
        private val _selectAllForRepair = MutableStateFlow<SelectAllSelection?>(null)
        val selectAllForRepair: StateFlow<SelectAllSelection?> = _selectAllForRepair.asStateFlow()

        /**
         * Adds everything currently available to the repair queue: with a search, the matching
         * songs plus every song of the matching albums and artists; without one, the whole library.
         * Turning it off takes those songs out again unless they were already processed.
         */
        fun setSelectAllForRepair(
            enabled: Boolean,
            query: String,
        ) {
            if (_isFixingQueue.value) return
            if (!enabled) {
                val added = _selectAllForRepair.value?.songIds.orEmpty()
                _repairQueue.value = _repairQueue.value.filterNot { it.song.id in added && it.status == "PENDING" }
                _selectAllForRepair.value = null
                return
            }
            viewModelScope.launch {
                val text = query.trim()
                val songs =
                    if (text.isEmpty()) {
                        libraryRepository.getAllSongs().first()
                    } else {
                        searchResults.value +
                            searchAlbums.value.flatMap { libraryRepository.getSongsByAlbumId(it.id).first() } +
                            searchArtists.value.flatMap { libraryRepository.getSongsByArtistId(it.id).first() }
                    }.distinctBy { it.id }
                val queued = _repairQueue.value.map { it.song.id }.toSet()
                val newSongs = songs.filter { it.id !in queued }
                _repairQueue.value = _repairQueue.value + newSongs.map { RepairQueueItem(song = it, status = "PENDING") }
                _selectAllForRepair.value =
                    SelectAllSelection(
                        query = text,
                        matched = songs.size,
                        songIds = newSongs.map { it.id }.toSet(),
                    )
            }
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
            if (_currentProcessingAlbum.value?.id == album.id) {
                _currentProcessingAlbum.value = null
                _artworkCandidates.value = emptyList()
                _artworkSearchDiagnostics.value = null
                _selectedEnlargedArtworkUrl.value = null
            }
        }

        fun clearArtworkQueue() {
            _artworkQueue.value = emptyList()
            _currentProcessingAlbum.value = null
            _artworkCandidates.value = emptyList()
            _artworkSearchDiagnostics.value = null
            _selectedEnlargedArtworkUrl.value = null
        }

        fun enqueueArtistForArtwork(artist: Artist) {
            val currentQueue = _artistArtworkQueue.value.toMutableList()
            if (currentQueue.none { it.artist.id == artist.id }) {
                currentQueue.add(ArtistArtworkQueueItem(artist = artist, status = "PENDING"))
                _artistArtworkQueue.value = currentQueue
            }
        }

        fun removeFromArtistArtworkQueue(artist: Artist) {
            val currentQueue = _artistArtworkQueue.value.toMutableList()
            currentQueue.removeAll { it.artist.id == artist.id }
            _artistArtworkQueue.value = currentQueue
            if (_currentProcessingArtist.value?.id == artist.id) {
                _currentProcessingArtist.value = null
                _artworkCandidates.value = emptyList()
                _artworkSearchDiagnostics.value = null
                _selectedEnlargedArtworkUrl.value = null
            }
        }

        fun clearArtistArtworkQueue() {
            _artistArtworkQueue.value = emptyList()
            _currentProcessingArtist.value = null
            _artworkCandidates.value = emptyList()
            _artworkSearchDiagnostics.value = null
            _selectedEnlargedArtworkUrl.value = null
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

        fun scanForMissingArtistPhotos() {
            viewModelScope.launch {
                isScanningLocal.value = true
                val allArtists = libraryRepository.getAllArtists().first()
                val missingPhotoArtists =
                    allArtists.filter { artist ->
                        artist.artworkUri.isNullOrBlank() || ArtistArtworkCache.isNotFound(artist.artworkUri)
                    }
                _artistArtworkQueue.value = missingPhotoArtists.map { ArtistArtworkQueueItem(artist = it, status = "PENDING") }
                delay(SCAN_INDICATOR_DELAY_MS)
                isScanningLocal.value = false
            }
        }

        private val _artworkSearchDiagnostics = MutableStateFlow<ArtworkSearchDiagnostics?>(null)
        val artworkSearchDiagnostics: StateFlow<ArtworkSearchDiagnostics?> = _artworkSearchDiagnostics.asStateFlow()

        private var artworkSearchJob: kotlinx.coroutines.Job? = null
        private var artworkRequestSequence = 0

        fun clearArtworkDiagnostics() {
            artworkRequestSequence++
            artworkSearchJob?.cancel()
            _isSearchingArtwork.value = false
            _artworkSearchDiagnostics.value = null
            _artworkCandidates.value = emptyList()
            _currentProcessingAlbum.value = null
            _currentProcessingArtist.value = null
            _selectedEnlargedArtworkUrl.value = null
        }

        fun artworkPreviewUrl(url: String): String = metadataRepository.artworkPreviewUrl(url)

        fun selectEnlargedArtwork(url: String?) {
            _selectedEnlargedArtworkUrl.value = url
        }

        fun searchArtworkForAlbum(album: Album) {
            artworkSearchJob?.cancel()
            val requestSequence = ++artworkRequestSequence
            artworkSearchJob =
                viewModelScope.launch {
                    _isSearchingArtwork.value = true
                    _currentProcessingAlbum.value = album
                    _currentProcessingArtist.value = null
                    _selectedEnlargedArtworkUrl.value = null
                    _artworkCandidates.value = emptyList()
                    _artworkSearchDiagnostics.value = null
                    try {
                        val firstSong = libraryRepository.getSongsByAlbumId(album.id).first().firstOrNull()
                        val ids = firstSong?.filePath?.let { embeddedTagReader.artworkIdentity(it) }
                        val type =
                            when {
                                Regex(
                                    "(?i)(?:-|–)\\s*single$",
                                ).containsMatchIn(album.title) -> com.erhu.erhu.core.network.artwork.ArtworkReleaseKind.SINGLE
                                Regex(
                                    "(?i)(?:-|–)\\s*ep$",
                                ).containsMatchIn(album.title) -> com.erhu.erhu.core.network.artwork.ArtworkReleaseKind.EP
                                album.songCount >= 4 -> com.erhu.erhu.core.network.artwork.ArtworkReleaseKind.ALBUM
                                else -> com.erhu.erhu.core.network.artwork.ArtworkReleaseKind.UNKNOWN
                            }
                        val query =
                            com.erhu.erhu.core.network.artwork.AlbumArtworkQuery(
                                album.title,
                                album.artist,
                                album.year,
                                type,
                                ids?.first,
                                ids?.second,
                                ids?.third,
                            )
                        val result = metadataRepository.searchAlbumArtwork(query)
                        val data = (result as? AppResult.Success)?.data
                        if (requestSequence != artworkRequestSequence) return@launch
                        _artworkCandidates.value = data?.candidates.orEmpty().map { it.imageUrl }
                        _artworkSearchDiagnostics.value =
                            ArtworkSearchDiagnostics(
                                targetName = album.title,
                                searchQuery = "${album.artist} — ${album.title}",
                                resolvedArtistInfo =
                                    localized().getString(
                                        if (data?.fromCache == true) {
                                            CommonR.string.library_artwork_cached_results
                                        } else {
                                            CommonR.string.library_artwork_release_matching
                                        },
                                    ),
                                providerOutcomes = data?.outcomes.orEmpty(),
                                candidates =
                                    data?.candidates.orEmpty().map { candidate ->
                                        ArtworkCandidateDetail(
                                            candidate.imageUrl,
                                            candidate.source,
                                            minOf(candidate.width, candidate.height) >= 500,
                                            candidate.album,
                                            candidate.artist,
                                            candidate.edition,
                                            candidate.year,
                                            candidate.width,
                                            candidate.height,
                                            candidate.score,
                                            candidate.reviewReasons,
                                            candidate.imageUrl == data?.recommended?.imageUrl,
                                            metadataRepository.artworkPreviewUrl(candidate.imageUrl),
                                        )
                                    },
                            )
                    } finally {
                        if (requestSequence == artworkRequestSequence) _isSearchingArtwork.value = false
                    }
                }
        }

        fun searchArtworkForArtist(artist: Artist) {
            artworkSearchJob?.cancel()
            val requestSequence = ++artworkRequestSequence
            artworkSearchJob =
                viewModelScope.launch {
                    _isSearchingArtwork.value = true
                    _currentProcessingArtist.value = artist
                    _currentProcessingAlbum.value = null
                    _selectedEnlargedArtworkUrl.value = null
                    try {
                        val result = metadataRepository.fetchArtistArtworkCandidatesDetailed(artist.name)
                        val data = (result as? AppResult.Success)?.data
                        if (requestSequence != artworkRequestSequence) return@launch
                        val urls = data?.candidates.orEmpty()
                        val deezerName = localized().getString(CommonR.string.artwork_source_deezer_artist)
                        _artworkCandidates.value = urls
                        _artworkSearchDiagnostics.value =
                            ArtworkSearchDiagnostics(
                                artist.name,
                                artist.name,
                                providerOutcomes =
                                    listOf(
                                        com.erhu.erhu.core.model.ProviderOutcome(
                                            deezerName,
                                            urls.size,
                                            if (urls.isEmpty()) {
                                                com.erhu.erhu.core.model.ProviderStatus.NO_RESULTS
                                            } else {
                                                com.erhu.erhu.core.model.ProviderStatus.SUCCESS
                                            },
                                        ),
                                    ),
                                candidates =
                                    urls.map {
                                        ArtworkCandidateDetail(
                                            it,
                                            deezerName,
                                            matchedArtist = artist.name,
                                        )
                                    },
                            )
                    } finally {
                        if (requestSequence == artworkRequestSequence) _isSearchingArtwork.value = false
                    }
                }
        }

        private val _isReverting = MutableStateFlow(false)
        val isReverting: StateFlow<Boolean> = _isReverting.asStateFlow()

        private val _revertCompletionMessage = MutableStateFlow<String?>(null)
        val revertCompletionMessage: StateFlow<String?> = _revertCompletionMessage.asStateFlow()

        fun dismissRevertCompletionMessage() {
            _revertCompletionMessage.value = null
        }

        fun revertSelectedChanges(selectedSongIds: Set<Long>) {
            if (_isReverting.value || _isFixingQueue.value) return
            val currentQueue = _repairQueue.value.toMutableList()
            val targetIndices =
                currentQueue.indices.filter {
                    currentQueue[it].song.id in selectedSongIds && currentQueue[it].successDetails != null
                }
            if (targetIndices.isEmpty()) {
                _userMessage.value = localized().getString(CommonR.string.library_revert_none_selected)
                return
            }

            _isReverting.value = true
            viewModelScope.launch(Dispatchers.IO) {
                val context = localized()
                val locale = AppLocaleManager.activeLocale(context)
                withWriteAccess(
                    contentUris = targetIndices.map { currentQueue[it].song.uri },
                    onGranted = {
                        try {
                            var revertedCount = 0
                            for (index in targetIndices) {
                                val item = currentQueue[index]
                                val succ = item.successDetails ?: continue
                                var currentPath = libraryRepository.getSongFilePath(item.song.id) ?: item.song.filePath.orEmpty()

                                val oldName = succ.oldFilename
                                if (succ.isRenamed && !oldName.isNullOrBlank()) {
                                    val renameRes = fileRenamer.renameAudioFile(item.song.uri, currentPath, oldName)
                                    if (renameRes is AppResult.Success) {
                                        currentPath = renameRes.data.newFilePath
                                        libraryRepository.updateFilePath(item.song.id, currentPath)
                                    }
                                }

                                val origTitle = succ.originalTitle.trim().takeIf { it.isNotBlank() && succ.hadEmbeddedTitle }
                                val origArtist = succ.originalArtist.trim().takeIf { it.isNotBlank() && succ.hadEmbeddedArtist }
                                val origAlbum = succ.originalAlbum.trim().takeIf { it.isNotBlank() && succ.hadEmbeddedAlbum }
                                val origYear = succ.originalYear?.trim()?.takeIf { it.isNotBlank() }
                                val origGenre = succ.originalGenre?.trim()?.takeIf { it.isNotBlank() }
                                val artworkWasChanged = succ.changes.any { it.fieldName.equals("Artwork", ignoreCase = true) }

                                val request =
                                    TagWriteRequest(
                                        contentUri = item.song.uri,
                                        filePath = currentPath,
                                        title = origTitle,
                                        clearTitle = origTitle == null,
                                        artist = origArtist,
                                        clearArtist = origArtist == null,
                                        album = origAlbum,
                                        clearAlbum = origAlbum == null,
                                        releaseDate = origYear,
                                        clearYear = origYear == null,
                                        genre = origGenre,
                                        clearGenre = origGenre == null,
                                        artworkBytes =
                                            if (artworkWasChanged &&
                                                succ.hadOriginalArtwork
                                            ) {
                                                succ.originalArtworkBytes
                                            } else {
                                                null
                                            },
                                        clearArtwork = artworkWasChanged && !succ.hadOriginalArtwork,
                                    )

                                val writeRes = metadataRepository.writeTagsToFile(request)
                                if (writeRes is AppResult.Error) {
                                    _userMessage.value =
                                        context.getString(
                                            CommonR.string.library_revert_failed_song,
                                            item.song.title,
                                            writeRes.error.localizedMessage(context),
                                        )
                                    continue
                                }

                                libraryRepository.updateSongTags(
                                    songId = item.song.id,
                                    title = succ.originalTitle,
                                    artist = succ.originalArtist,
                                    album = succ.originalAlbum,
                                )
                                ArtworkLoader.invalidate(item.song.uri)

                                val restoredSong =
                                    item.song.copy(
                                        title = succ.originalTitle,
                                        artist = succ.originalArtist,
                                        album = succ.originalAlbum,
                                    )
                                currentQueue[index] =
                                    item.copy(
                                        song = restoredSong,
                                        status = "REVERTED",
                                        failureReason = context.getString(CommonR.string.library_revert_status_reverted),
                                    )
                                revertedCount++
                            }

                            _repairQueue.value = currentQueue.toList()

                            if (revertedCount > 0) {
                                delay(500L)
                                libraryRepository.requestRescanAfterChange()
                                _revertCompletionMessage.value =
                                    context.resources.getQuantityString(
                                        CommonR.plurals.library_revert_complete,
                                        revertedCount,
                                        LocaleFormatters.formatInteger(revertedCount, locale),
                                    )
                            }
                        } finally {
                            _isReverting.value = false
                        }
                    },
                    onDenied = {
                        _isReverting.value = false
                        _userMessage.value = context.getString(CommonR.string.library_revert_permission_denied)
                    },
                )
            }
        }

        @Suppress("TooGenericExceptionCaught")
        fun applyArtworkToAlbum(
            album: Album,
            artworkUrl: String,
        ) {
            fun updateStatus(
                status: String,
                reason: String? = null,
            ) {
                _artworkQueue.update { latest ->
                    latest.map { item -> if (item.album.id == album.id) item.copy(status = status, failureReason = reason) else item }
                }
            }

            viewModelScope.launch {
                val context = localized()
                _isSearchingArtwork.value = true
                updateStatus("WRITING")

                try {
                    val downloadResult = metadataRepository.downloadArtwork(artworkUrl)
                    val imageBytes =
                        (downloadResult as? AppResult.Success)?.data
                            ?: throw IllegalStateException(
                                (downloadResult as? AppResult.Error)?.error?.localizedMessage(context)
                                    ?: "WRITE_ERROR",
                            )

                    val albumSongs = libraryRepository.getSongsByAlbumId(album.id).first()
                    val uris = albumSongs.map { it.uri }
                    withWriteAccess(
                        contentUris = uris,
                        onGranted = {
                            var writeFailed = false
                            for (song in albumSongs) {
                                val filePath = libraryRepository.getSongFilePath(song.id) ?: song.filePath.orEmpty()
                                val req =
                                    TagWriteRequest(
                                        contentUri = song.uri,
                                        filePath = filePath,
                                        artworkBytes = imageBytes,
                                    )
                                val writeRes = metadataRepository.writeTagsToFile(req)
                                if (writeRes is AppResult.Error) {
                                    writeFailed = true
                                    _userMessage.value = tagWriteFailureMessage(writeRes.error)
                                } else {
                                    ArtworkLoader.invalidate(song.uri)
                                }
                            }
                            updateStatus(if (writeFailed) "FAILED" else "COMPLETED", if (writeFailed) "WRITE_ERROR" else null)
                            libraryRepository.triggerLibraryScan()
                        },
                        onDenied = {
                            updateStatus("FAILED", "WRITE_PERMISSION_DENIED")
                            _userMessage.value = context.getString(CommonR.string.library_artwork_write_denied)
                        },
                    )
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    updateStatus("FAILED", e.message ?: "WRITE_ERROR")
                } finally {
                    _selectedEnlargedArtworkUrl.value = null
                    _artworkCandidates.value = emptyList()
                    _currentProcessingAlbum.value = null
                    _artworkSearchDiagnostics.value = null
                    _isSearchingArtwork.value = false
                }
            }
        }

        fun applyArtworkBytesToAlbum(
            album: Album,
            imageBytes: ByteArray,
        ) {
            viewModelScope.launch {
                val context = localized()
                try {
                    val albumSongs = libraryRepository.getSongsByAlbumId(album.id).first()
                    val uris = albumSongs.map { it.uri }
                    withWriteAccess(
                        contentUris = uris,
                        onGranted = {
                            for (song in albumSongs) {
                                val filePath = libraryRepository.getSongFilePath(song.id) ?: song.filePath.orEmpty()
                                val req =
                                    TagWriteRequest(
                                        contentUri = song.uri,
                                        filePath = filePath,
                                        artworkBytes = imageBytes,
                                    )
                                val writeRes = metadataRepository.writeTagsToFile(req)
                                if (writeRes is AppResult.Error) {
                                    _userMessage.value = tagWriteFailureMessage(writeRes.error)
                                } else {
                                    ArtworkLoader.invalidate(song.uri)
                                }
                            }
                            libraryRepository.triggerLibraryScan()
                        },
                        onDenied = {
                            _userMessage.value = context.getString(CommonR.string.library_album_cover_write_denied)
                        },
                    )
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _userMessage.value =
                        context.getString(CommonR.string.library_album_cover_write_failed, e.message.orEmpty())
                }
            }
        }

        @Suppress("TooGenericExceptionCaught")
        fun applyArtistArtwork(
            artist: Artist,
            imageUrl: String,
        ) {
            viewModelScope.launch(Dispatchers.IO) {
                val context = localized()
                _isSearchingArtwork.value = true
                val queue = _artistArtworkQueue.value.toMutableList()
                val queueIndex = queue.indexOfFirst { it.artist.id == artist.id }
                if (queueIndex != -1) {
                    queue[queueIndex] = queue[queueIndex].copy(status = "WRITING")
                    _artistArtworkQueue.value = queue.toList()
                }

                try {
                    val outcome = performArtistArtworkWrite(artist.id, imageUrl, context)
                    if (outcome == null) {
                        updateArtistQueueItem(queueIndex, status = "COMPLETED")
                    } else {
                        _userMessage.value = outcome
                        updateArtistQueueItem(queueIndex, status = "FAILED", failureReason = outcome)
                    }
                    libraryRepository.triggerLibraryScan()
                } catch (e: Throwable) {
                    updateArtistQueueItem(queueIndex, status = "FAILED", failureReason = e.message ?: "WRITE_ERROR")
                } finally {
                    _selectedEnlargedArtworkUrl.value = null
                    _artworkCandidates.value = emptyList()
                    _currentProcessingArtist.value = null
                    _artworkSearchDiagnostics.value = null
                    _isSearchingArtwork.value = false
                }
            }
        }

        private suspend fun performArtistArtworkWrite(
            artistId: Long,
            imageUrl: String,
            context: android.content.Context,
        ): String? =
            when (val downloadResult = metadataRepository.downloadArtwork(imageUrl)) {
                is AppResult.Success -> {
                    val result = artistArtworkRepository.setCustomArtwork(artistId, downloadResult.data)
                    if (result is AppResult.Error) result.error.localizedMessage(context) else null
                }
                is AppResult.Error -> downloadResult.error.localizedMessage(context)
            }

        private fun updateArtistQueueItem(
            queueIndex: Int,
            status: String,
            failureReason: String? = null,
        ) {
            if (queueIndex == -1) return
            val queue = _artistArtworkQueue.value.toMutableList()
            queue[queueIndex] = queue[queueIndex].copy(status = status, failureReason = failureReason)
            _artistArtworkQueue.value = queue.toList()
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
                        com.erhu.erhu.core.common.util.SearchNormalizer
                            .matches(it.title, query) ||
                            com.erhu.erhu.core.common.util.SearchNormalizer
                                .matches(it.artist, query)
                    }

                val allArtists = libraryRepository.getAllArtists().first()
                _searchArtists.value =
                    allArtists.filter {
                        com.erhu.erhu.core.common.util.SearchNormalizer
                            .matches(it.name, query)
                    }
            }
        }

        fun setLibrarySearchQuery(query: String) {
            _librarySearchQuery.value = query
        }

        fun addRecentAlbum(album: Album) {
            val current = _recentSearchItems.value.toMutableList()
            current.removeAll { it is RecentSearchItem.AlbumItem && it.album.id == album.id }
            current.add(0, RecentSearchItem.AlbumItem(album))
            _recentSearchItems.value = current.take(15)
        }

        fun addRecentArtist(artist: Artist) {
            val current = _recentSearchItems.value.toMutableList()
            current.removeAll { it is RecentSearchItem.ArtistItem && it.artist.id == artist.id }
            current.add(0, RecentSearchItem.ArtistItem(artist))
            _recentSearchItems.value = current.take(15)
        }

        fun addRecentSong(song: Song) {
            val current = _recentSearchItems.value.toMutableList()
            current.removeAll { it is RecentSearchItem.SongItem && it.song.id == song.id }
            current.add(0, RecentSearchItem.SongItem(song))
            _recentSearchItems.value = current.take(15)
        }

        fun addRecentPlaylist(
            playlistId: Long,
            title: String,
            trackCount: Int,
        ) {
            val current = _recentSearchItems.value.toMutableList()
            current.removeAll { it is RecentSearchItem.PlaylistItem && it.playlistId == playlistId }
            current.add(0, RecentSearchItem.PlaylistItem(playlistId, title, trackCount))
            _recentSearchItems.value = current.take(15)
        }

        fun removeRecentSearchItem(item: RecentSearchItem) {
            val current = _recentSearchItems.value.toMutableList()
            current.removeAll { it.key == item.key }
            _recentSearchItems.value = current
        }

        fun clearRecentSearchItems() {
            _recentSearchItems.value = emptyList()
        }

        fun enqueueSongForRepair(song: Song) {
            if (_isFixingQueue.value || metadataRepair.awaitingApproval.value) return
            val currentQueue = _repairQueue.value.toMutableList()
            if (currentQueue.none { it.song.id == song.id }) {
                currentQueue.add(RepairQueueItem(song = song, status = "PENDING"))
                _repairQueue.value = currentQueue
            }
        }

        fun removeFromRepairQueue(song: Song) {
            if (_isFixingQueue.value || metadataRepair.awaitingApproval.value) return
            val currentQueue = _repairQueue.value.toMutableList()
            currentQueue.removeAll { it.song.id == song.id }
            _repairQueue.value = currentQueue
        }

        fun retryRepairQueueItem(song: Song) {
            if (_isFixingQueue.value || metadataRepair.awaitingApproval.value) return
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
            if (_isFixingQueue.value || metadataRepair.awaitingApproval.value) return
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
            if (_isFixingQueue.value || metadataRepair.awaitingApproval.value) return
            viewModelScope.launch {
                val artistSongs = libraryRepository.getSongsByArtistId(artist.id).first()
                val currentQueue = _repairQueue.value.toMutableList()
                val existingIds = currentQueue.map { it.song.id }.toSet()
                val newItems = artistSongs.filter { it.id !in existingIds }.map { RepairQueueItem(song = it, status = "PENDING") }
                currentQueue.addAll(newItems)
                _repairQueue.value = currentQueue
            }
        }

        private val _batchPause = metadataRepair.pause
        val batchPause: StateFlow<BatchPause?> = _batchPause.asStateFlow()
        val metadataSourceOptions: StateFlow<List<MetadataSourceOption>> = MutableStateFlow(metadataSourceOptions()).asStateFlow()

        init {
            viewModelScope.launch {
                metadataRepair.messages.collect { message ->
                    if (message != null) {
                        _userMessage.value = message
                        metadataRepair.messages.value = null
                    }
                }
            }
        }

        fun pauseFixingQueue() = metadataRepair.requestPause()

        fun startFixingQueue() {
            if (!metadataRepair.prepare(selectedProvider.value, fieldSelection(selectedFields.value), confidenceThreshold.value)) return
            try {
                androidx.core.content.ContextCompat.startForegroundService(
                    appContext,
                    android.content
                        .Intent(appContext, com.erhu.erhu.feature.library.service.MetadataRepairService::class.java)
                        .setAction(com.erhu.erhu.feature.library.service.MetadataRepairService.ACTION_START),
                )
            } catch (e: RuntimeException) {
                metadataRepair.startFailed(
                    localized().getString(
                        CommonR.string.library_metadata_service_start_failed,
                        e.message.orEmpty(),
                    ),
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
                _userMessage.value = localized().getString(CommonR.string.library_queue_play_next, song.title)
            }
        }

        fun addToQueue(song: Song) {
            viewModelScope.launch {
                playbackRepository.addToQueue(song)
                _userMessage.value = localized().getString(CommonR.string.library_queue_added, song.title)
            }
        }

        fun toggleFavorite(song: Song) {
            viewModelScope.launch {
                libraryRepository.toggleFavorite(song.id)
                val current =
                    _recentSearchItems.value.map { item ->
                        if (item is RecentSearchItem.SongItem && item.song.id == song.id) {
                            item.copy(song = item.song.copy(isFavorite = !item.song.isFavorite))
                        } else {
                            item
                        }
                    }
                _recentSearchItems.value = current
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

        /**
         * Plays the smart mix [mixKey] in mix order (the home banner's Play), exactly as its page's
         * Play button does. Nothing happens when the mix isn't offered right now.
         */
        fun playSmartMix(mixKey: String) {
            if (isVybeMix(mixKey) && themeRepository.vybeEnabled.value != true) return
            val mix = smartMixes.value?.mixes?.firstOrNull { it.kind.key == mixKey } ?: return
            viewModelScope.launch { playInOrder(libraryRepository.getSongsByIds(mix.songIds)) }
        }

        /**
         * The Smart Radio in the queue: its seed, the song it started from and every song it has
         * suggested so far (so "New radio picks" brings different ones). Null when none was started.
         */
        data class RadioSession(
            val seed: RadioSeed,
            val startSongId: Long,
            val suggested: Set<Long>,
        )

        private val _radio = MutableStateFlow<RadioSession?>(null)
        val radio: StateFlow<RadioSession?> = _radio.asStateFlow()

        private val _radioMessages = MutableSharedFlow<String>(extraBufferCapacity = 1)

        /** One-line reports of what a radio was built from (or why it could not be). */
        val radioMessages: SharedFlow<String> = _radioMessages.asSharedFlow()

        /** Starts a new queue: the seed's start song, then the radio's picks, in radio order. */
        fun startRadio(seed: RadioSeed) {
            if (themeRepository.vybeEnabled.value != true) return
            viewModelScope.launch {
                val plan = radioRepository.build(seed) ?: return@launch report(null)
                val songs = libraryRepository.getSongsByIds(listOf(plan.startSongId) + plan.songIds)
                if (plan.songIds.isEmpty() || songs.isEmpty()) return@launch report(plan)
                playRadioPlan(plan, songs)
            }
        }

        /** Plays the exact station snapshot shown on its details page, without regenerating picks. */
        fun playRadioPlan(
            plan: RadioPlan,
            songs: List<Song>,
        ) {
            if (songs.isEmpty()) return
            playbackRepository.setShuffleModeEnabled(false)
            playbackRepository.playCollection(songs, 0, origin = null)
            _radio.value = RadioSession(plan.seed, plan.startSongId, plan.songIds.toSet())
            report(plan)
        }

        /** A radio from the song playing now: it keeps playing and the radio replaces what comes next. */
        fun startRadioFromCurrent() {
            val current = playbackRepository.playbackState.value.currentSong ?: return
            replaceUpcomingWithRadio(RadioSeed.Song(current.id), current.id, exclude = emptySet())
        }

        /** Fresh picks for the radio in the queue, avoiding the songs it already suggested. */
        fun refreshRadio() {
            val session = _radio.value ?: return
            replaceUpcomingWithRadio(session.seed, session.startSongId, exclude = session.suggested)
        }

        private fun replaceUpcomingWithRadio(
            seed: RadioSeed,
            startSongId: Long,
            exclude: Set<Long>,
        ) {
            viewModelScope.launch {
                val plan = radioRepository.build(seed, exclude, startSongId) ?: return@launch report(null)
                val songs = libraryRepository.getSongsByIds(plan.songIds)
                if (songs.isEmpty()) return@launch report(plan)
                playbackRepository.setShuffleModeEnabled(false)
                playbackRepository.replaceUpcoming(songs)
                _radio.value = RadioSession(seed, startSongId, exclude + plan.songIds)
                report(plan)
            }
        }

        private fun report(plan: RadioPlan?) {
            val context = localized()
            val locale = AppLocaleManager.activeLocale(context)
            val message =
                when {
                    plan == null -> context.getString(CommonR.string.library_radio_no_songs)
                    plan.songIds.isEmpty() ->
                        context.getString(CommonR.string.library_radio_not_enough, plan.name)
                    else -> {
                        val songsLabel = songCountText(context, plan.songIds.size)
                        val basis = plan.basedOn.map { context.getString(it.labelRes()) }
                        if (basis.isEmpty()) {
                            context.getString(
                                CommonR.string.library_radio_summary_no_reasons,
                                plan.name,
                                songsLabel,
                            )
                        } else {
                            context.getString(
                                CommonR.string.library_radio_summary_with_reasons,
                                plan.name,
                                songsLabel,
                                LocaleFormatters.formatList(basis, locale),
                            )
                        }
                    }
                }
            _radioMessages.tryEmit(message)
        }

        private fun RadioSignal.labelRes(): Int =
            when (this) {
                RadioSignal.SOUND -> CommonR.string.library_radio_signal_sound
                RadioSignal.GENRE -> CommonR.string.library_radio_signal_genre
                RadioSignal.TEMPO -> CommonR.string.library_radio_signal_tempo
                RadioSignal.SAME_ARTIST -> CommonR.string.library_radio_signal_same_artist
                RadioSignal.LISTENING -> CommonR.string.library_radio_signal_listening
            }

        fun shufflePlayLibrary() {
            viewModelScope.launch {
                val songs = libraryRepository.getAllSongs().first()
                if (songs.isNotEmpty()) {
                    playShuffled(songs)
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
            scanTimeoutTrigger.value = false
            viewModelScope.launch {
                libraryRepository.triggerLibraryScan()
                delay(SCAN_TIMEOUT_MS)
                scanTimeoutTrigger.value = true
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
                    _userMessage.value =
                        localized().getString(CommonR.string.library_tags_file_missing, song.title)
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
                    _userMessage.value = localized().getString(CommonR.string.library_tags_nothing_changed)
                    return@launch
                }
                val context = localized()
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
                                _userMessage.value =
                                    context.getString(
                                        CommonR.string.library_tags_saved,
                                        request.title ?: song.title,
                                    )
                            }
                            is AppResult.Error -> _userMessage.value = tagWriteFailureMessage(result.error)
                        }
                    },
                    onDenied = { _userMessage.value = context.getString(CommonR.string.library_tags_write_denied) },
                )
            }
        }

        /**
         * Writes [newBpm] as the song's BPM tag and nothing else (no rename, no other tag), after the
         * user confirmed a half / double time correction. The writer reads the file back before the
         * original is replaced; then the library row is updated and a rescan re-reads the tags.
         */
        fun correctBpm(
            song: Song,
            oldBpm: Int?,
            newBpm: Int,
        ) {
            viewModelScope.launch {
                val context = localized()
                val locale = AppLocaleManager.activeLocale(context)

                fun emitBpmMessage(message: String) {
                    _bpmMessage.value = message
                    _userMessage.value = message
                }
                if (newBpm !in PLAUSIBLE_BPM) {
                    emitBpmMessage(
                        context.getString(
                            CommonR.string.library_bpm_implausible,
                            LocaleFormatters.formatInteger(newBpm, locale),
                        ),
                    )
                    return@launch
                }
                val filePath = libraryRepository.getSongFilePath(song.id)
                if (filePath == null) {
                    emitBpmMessage(context.getString(CommonR.string.library_tags_file_missing, song.title))
                    return@launch
                }
                val request = TagWriteRequest(contentUri = song.uri, filePath = filePath, bpm = newBpm)
                withWriteAccess(
                    contentUris = listOf(request.contentUri),
                    onGranted = {
                        when (val result = metadataRepository.writeTagsToFile(request)) {
                            is AppResult.Success -> {
                                libraryRepository.updateSongBpm(song.id, newBpm)
                                libraryRepository.requestRescanAfterChange()
                                val newFormatted = LocaleFormatters.formatInteger(newBpm, locale)
                                val msg =
                                    if (oldBpm != null) {
                                        context.getString(
                                            CommonR.string.library_bpm_saved_with_previous,
                                            song.title,
                                            newFormatted,
                                            LocaleFormatters.formatInteger(oldBpm, locale),
                                        )
                                    } else {
                                        context.getString(
                                            CommonR.string.library_bpm_saved,
                                            song.title,
                                            newFormatted,
                                        )
                                    }
                                emitBpmMessage(msg)
                            }
                            is AppResult.Error -> emitBpmMessage(tagWriteFailureMessage(result.error))
                        }
                    },
                    onDenied = { emitBpmMessage(context.getString(CommonR.string.library_bpm_write_denied)) },
                )
            }
        }

        private fun tagWriteFailureMessage(error: AppError): String {
            val context = localized()
            val detail = error.localizedMessage(context)
            return when (error) {
                is AppError.WriteAccessRequired -> context.getString(CommonR.string.library_tags_not_changed_error, detail)
                else -> context.getString(CommonR.string.library_tags_write_failed_error, detail)
            }
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
        ) = metadataWriteAccess.withWriteAccess(contentUris, onGranted, onDenied, attempt)

        fun claimWriteAccessLaunch(): Boolean = metadataWriteAccess.claimLaunch()

        fun onWriteAccessResult(granted: Boolean) = metadataWriteAccess.onResult(granted)

        companion object {
            // The plan's generic notes, replaced by the specific reason when there is one.

            /** Opening an album waits at most this long for its tracks (then they load as before). */
            const val PREPARE_ALBUM_TIMEOUT_MS = 150L

            private const val SCAN_TIMEOUT_MS = 15_000L
            private const val SCAN_INDICATOR_DELAY_MS = 1000L
            const val MIN_THRESHOLD = 0.50f
            const val MAX_THRESHOLD = 0.95f
            private const val SEARCH_DEBOUNCE_MS = 250L
            private const val SEARCH_SECTION_LIMIT = 12
            private const val STOP_TIMEOUT_MS = 5_000L
            private const val RECENT_ALBUMS_LIMIT = 12
        }
    }

/** What is saved of the queue: status and reason per song. A planned but unwritten track is saved as pending. */
internal fun List<RepairQueueItem>.toPersisted(pause: BatchPause?): PersistedRepairBatch =
    PersistedRepairBatch(
        items =
            map { item ->
                val status = if (item.status == RepairStatus.FIXING) RepairStatus.PENDING else item.status
                PersistedRepairItem(item.song.id, status, item.failureReason)
            },
        pauseReason = pause?.reason?.takeIf { it.isNotEmpty() },
    )

/** The stored auto-accept threshold, or the default (0.85) when it was never set. */
internal fun effectiveThreshold(stored: Float): Float =
    if (stored <= 0f) HIGH_CONFIDENCE_THRESHOLD else stored.coerceIn(LibraryViewModel.MIN_THRESHOLD, LibraryViewModel.MAX_THRESHOLD)
