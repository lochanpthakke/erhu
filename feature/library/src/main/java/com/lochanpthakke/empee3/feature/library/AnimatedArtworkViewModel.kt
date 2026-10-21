package com.lochanpthakke.empee3.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lochanpthakke.empee3.core.database.repository.AnimatedArtworkRepository
import com.lochanpthakke.empee3.core.database.repository.LibraryRepository
import com.lochanpthakke.empee3.core.model.Album
import com.lochanpthakke.empee3.core.model.AnimatedArtOrientation
import com.lochanpthakke.empee3.core.model.LocalAnimatedArtwork
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AnimatedArtworkQueueItem(
    val album: Album,
    val artwork: LocalAnimatedArtwork?
)

@HiltViewModel
class AnimatedArtworkViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val animatedArtworkRepository: AnimatedArtworkRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchAlbums = MutableStateFlow<List<Album>>(emptyList())
    val searchAlbums: StateFlow<List<Album>> = _searchAlbums.asStateFlow()

    private val _queuedAlbumIds = MutableStateFlow<Set<Long>>(emptySet())

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _selectedDiagnosticAlbumId = MutableStateFlow<Long?>(null)
    val selectedDiagnosticAlbumId: StateFlow<Long?> = _selectedDiagnosticAlbumId.asStateFlow()

    val preferredOrientation: StateFlow<AnimatedArtOrientation> =
        animatedArtworkRepository.observePreferredOrientation()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AnimatedArtOrientation.SQUARE_1_1)

    private val _totalStorageBytes = MutableStateFlow(0L)
    val totalStorageBytes: StateFlow<Long> = _totalStorageBytes.asStateFlow()

    val queueItems: StateFlow<List<AnimatedArtworkQueueItem>> = combine(
        _queuedAlbumIds,
        libraryRepository.getAllAlbums()
    ) { queuedIds, allAlbums ->
        val albumMap = allAlbums.associateBy { it.id }
        queuedIds.mapNotNull { albumId ->
            albumMap[albumId]?.let { album ->
                val artwork = animatedArtworkRepository.observeAnimatedArtwork(albumId).first()
                AnimatedArtworkQueueItem(album, artwork)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        refreshStorageBytes()
        viewModelScope.launch {
            animatedArtworkRepository.cleanupOnStartup()
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) {
            _searchAlbums.value = emptyList()
        } else {
            viewModelScope.launch {
                val albums = libraryRepository.getAllAlbums().first()
                _searchAlbums.value = albums.filter {
                    it.title.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true)
                }.take(10)
            }
        }
    }

    fun enqueueAlbum(album: Album) {
        _queuedAlbumIds.value = _queuedAlbumIds.value + album.id
        if (_selectedDiagnosticAlbumId.value == null) {
            _selectedDiagnosticAlbumId.value = album.id
        }
        processItem(album)
    }

    fun removeFromQueue(albumId: Long) {
        _queuedAlbumIds.value = _queuedAlbumIds.value - albumId
        if (_selectedDiagnosticAlbumId.value == albumId) {
            _selectedDiagnosticAlbumId.value = _queuedAlbumIds.value.firstOrNull()
        }
    }

    fun scanForMissingAnimatedArtwork() {
        if (_isScanning.value) return
        _isScanning.value = true
        viewModelScope.launch {
            try {
                val allAlbums = libraryRepository.getAllAlbums().first()
                val missing = allAlbums.filter { album ->
                    val existing = animatedArtworkRepository.observeAnimatedArtwork(album.id).first()
                    existing == null || (existing.squareFilePath == null && existing.tallFilePath == null && existing.status.name !in listOf("NO_MATCH", "NO_ANIMATION"))
                }

                missing.forEach { album ->
                    enqueueAlbum(album)
                }
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun setOrientation(orientation: AnimatedArtOrientation) {
        viewModelScope.launch {
            animatedArtworkRepository.setPreferredOrientation(orientation)
        }
    }

    fun selectDiagnosticItem(albumId: Long) {
        _selectedDiagnosticAlbumId.value = albumId
    }

    fun retryItem(album: Album) {
        processItem(album, forceRefresh = true)
    }

    private fun processItem(album: Album, forceRefresh: Boolean = false) {
        viewModelScope.launch {
            val orientation = preferredOrientation.value
            animatedArtworkRepository.fetchAndStoreArtwork(
                albumId = album.id,
                albumArtist = album.artist,
                album = album.title,
                trackCount = album.songCount,
                orientation = orientation,
                forceRefresh = forceRefresh
            )
            refreshStorageBytes()
        }
    }

    fun clearAllStorage() {
        viewModelScope.launch {
            animatedArtworkRepository.deleteAllArtwork()
            _queuedAlbumIds.value = emptySet()
            _selectedDiagnosticAlbumId.value = null
            refreshStorageBytes()
        }
    }

    private fun refreshStorageBytes() {
        viewModelScope.launch {
            _totalStorageBytes.value = animatedArtworkRepository.totalStorageBytes()
        }
    }
}
