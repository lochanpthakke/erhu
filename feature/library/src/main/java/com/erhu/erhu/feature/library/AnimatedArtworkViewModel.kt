package com.erhu.erhu.feature.library

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erhu.erhu.core.common.locale.AppLocaleManager
import com.erhu.erhu.core.common.locale.LocaleFormatters
import com.erhu.erhu.core.common.locale.localizedMessage
import com.erhu.erhu.core.common.result.AppResult
import com.erhu.erhu.core.database.repository.AnimatedArtworkRepository
import com.erhu.erhu.core.database.repository.LibraryRepository
import com.erhu.erhu.core.model.Album
import com.erhu.erhu.core.model.AnimatedArtOrientation
import com.erhu.erhu.core.model.LocalAnimatedArtwork
import com.erhu.erhu.core.network.apple.HlsPlaylistParser
import com.erhu.erhu.core.network.apple.HlsVariantStream
import com.erhu.erhu.core.network.apple.RateLimitException
import com.erhu.erhu.core.network.apple.ResolveOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.erhu.erhu.core.common.R as CommonR

data class FetchedMasterDetails(
    val catalogAlbumId: String,
    val storefront: String,
    val squareMasterUrl: String?,
    val tallMasterUrl: String?,
)

data class AnimatedArtworkQueueItem(
    val album: Album,
    val artwork: LocalAnimatedArtwork?,
)

@HiltViewModel
class AnimatedArtworkViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val appContext: Context,
        private val libraryRepository: LibraryRepository,
        private val animatedArtworkRepository: AnimatedArtworkRepository,
    ) : ViewModel() {
        private val _searchQuery = MutableStateFlow("")
        val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

        private val _searchAlbums = MutableStateFlow<List<Album>>(emptyList())
        val searchAlbums: StateFlow<List<Album>> = _searchAlbums.asStateFlow()

        private val _selectedAlbum = MutableStateFlow<Album?>(null)
        val selectedAlbum: StateFlow<Album?> = _selectedAlbum.asStateFlow()

        private val queuedAlbumIds = MutableStateFlow<Set<Long>>(emptySet())

        private val _isFetchingMaster = MutableStateFlow(false)
        val isFetchingMaster: StateFlow<Boolean> = _isFetchingMaster.asStateFlow()

        private val _isDownloadingFile = MutableStateFlow(false)
        val isDownloadingFile: StateFlow<Boolean> = _isDownloadingFile.asStateFlow()

        private val _selectedDiagnosticAlbumId = MutableStateFlow<Long?>(null)
        val selectedDiagnosticAlbumId: StateFlow<Long?> = _selectedDiagnosticAlbumId.asStateFlow()

        private val _availableVariants = MutableStateFlow<List<HlsVariantStream>>(emptyList())
        val availableVariants: StateFlow<List<HlsVariantStream>> = _availableVariants.asStateFlow()

        private val _selectedVariant = MutableStateFlow<HlsVariantStream?>(null)
        val selectedVariant: StateFlow<HlsVariantStream?> = _selectedVariant.asStateFlow()

        private val _fetchedMasterDetails = MutableStateFlow<FetchedMasterDetails?>(null)
        val fetchedMasterDetails: StateFlow<FetchedMasterDetails?> = _fetchedMasterDetails.asStateFlow()

        private val _previewStreamUrl = MutableStateFlow<String?>(null)
        val previewStreamUrl: StateFlow<String?> = _previewStreamUrl.asStateFlow()

        private val _successDialogArtwork = MutableStateFlow<LocalAnimatedArtwork?>(null)
        val successDialogArtwork: StateFlow<LocalAnimatedArtwork?> = _successDialogArtwork.asStateFlow()

        private val _errorMessage = MutableStateFlow<String?>(null)
        val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

        val preferredOrientation: StateFlow<AnimatedArtOrientation> =
            animatedArtworkRepository
                .observePreferredOrientation()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AnimatedArtOrientation.SQUARE_1_1)

        private val _totalStorageBytes = MutableStateFlow(0L)
        val totalStorageBytes: StateFlow<Long> = _totalStorageBytes.asStateFlow()

        val queueItems: StateFlow<List<AnimatedArtworkQueueItem>> =
            combine(
                queuedAlbumIds,
                libraryRepository.getAllAlbums(),
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
                    _searchAlbums.value =
                        albums
                            .filter {
                                it.title.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true)
                            }.take(10)
                }
            }
        }

        fun selectAlbumFromSearch(album: Album) {
            _selectedAlbum.value = album
            queuedAlbumIds.value = setOf(album.id)
            _selectedDiagnosticAlbumId.value = album.id
            _previewStreamUrl.value = null
            _availableVariants.value = emptyList()
            _selectedVariant.value = null
            _fetchedMasterDetails.value = null
        }

        fun selectVariant(variant: HlsVariantStream) {
            _selectedVariant.value = variant
            _previewStreamUrl.value = variant.variantUrl
        }

        fun dismissSuccessDialog() {
            _successDialogArtwork.value = null
        }

        fun dismissErrorMessage() {
            _errorMessage.value = null
        }

        fun resetSearchAndQueue() {
            _searchQuery.value = ""
            _searchAlbums.value = emptyList()
            _selectedAlbum.value = null
            queuedAlbumIds.value = emptySet()
            _selectedDiagnosticAlbumId.value = null
            _availableVariants.value = emptyList()
            _selectedVariant.value = null
            _fetchedMasterDetails.value = null
            _previewStreamUrl.value = null
            _successDialogArtwork.value = null
            _errorMessage.value = null
        }

        fun triggerFetchMotionArtwork() {
            val localized = AppLocaleManager.localizedContext(appContext)
            val album = _selectedAlbum.value
            if (album == null) {
                _errorMessage.value = localized.getString(CommonR.string.anim_art_err_select_album)
                return
            }

            if (_isFetchingMaster.value || _isDownloadingFile.value) return

            _isFetchingMaster.value = true
            _previewStreamUrl.value = null
            _availableVariants.value = emptyList()
            _selectedVariant.value = null
            _fetchedMasterDetails.value = null

            viewModelScope.launch {
                try {
                    val resolveResult = animatedArtworkRepository.resolveAlbum(album.artist, album.title, album.songCount)
                    when (resolveResult) {
                        is AppResult.Success -> {
                            when (val outcome = resolveResult.data) {
                                is ResolveOutcome.NoMatch -> {
                                    _errorMessage.value =
                                        localized.getString(CommonR.string.anim_art_err_no_match, album.title)
                                }
                                is ResolveOutcome.Matched -> {
                                    handleMasterUrlsFetched(album, outcome.catalogAlbumId, outcome.storefront)
                                }
                            }
                        }
                        is AppResult.Error -> {
                            _errorMessage.value =
                                rateLimitMessage(localized, resolveResult.error.cause)
                                    ?: localized.getString(
                                        CommonR.string.anim_art_err_resolve_failed,
                                        resolveResult.error.localizedMessage(localized),
                                    )
                        }
                    }
                } finally {
                    _isFetchingMaster.value = false
                }
            }
        }

        private suspend fun handleMasterUrlsFetched(
            album: Album,
            catalogAlbumId: String,
            storefront: String,
        ) {
            val localized = AppLocaleManager.localizedContext(appContext)
            val masterUrlsResult = animatedArtworkRepository.fetchMasterUrls(catalogAlbumId, storefront)
            if (masterUrlsResult !is AppResult.Success) {
                _errorMessage.value =
                    (masterUrlsResult as? AppResult.Error)?.let { rateLimitMessage(localized, it.error.cause) }
                        ?: localized.getString(CommonR.string.anim_art_err_catalog_failed)
                return
            }

            val editorial = masterUrlsResult.data
            val masterUrl =
                if (preferredOrientation.value == AnimatedArtOrientation.SQUARE_1_1) {
                    editorial.squareMasterUrl ?: editorial.tallMasterUrl
                } else {
                    editorial.tallMasterUrl ?: editorial.squareMasterUrl
                }

            if (masterUrl == null) {
                _errorMessage.value = localized.getString(CommonR.string.anim_art_err_no_video)
                return
            }

            _fetchedMasterDetails.value =
                FetchedMasterDetails(
                    catalogAlbumId = catalogAlbumId,
                    storefront = storefront,
                    squareMasterUrl = editorial.squareMasterUrl,
                    tallMasterUrl = editorial.tallMasterUrl,
                )

            val variantsResult = animatedArtworkRepository.fetchVariantsForMasterUrl(masterUrl)
            if (variantsResult is AppResult.Success && variantsResult.data.isNotEmpty()) {
                val variants = variantsResult.data
                _availableVariants.value = variants
                // Sized for the phone, not the top variant (2048x2732 at 17 Mbit/s stalled the decoder).
                val defaultVariant = HlsPlaylistParser.displayVariant(variants) ?: variants.first()
                _selectedVariant.value = defaultVariant
                _previewStreamUrl.value = defaultVariant.variantUrl
            } else {
                _previewStreamUrl.value = masterUrl
            }
        }

        fun downloadAndSaveArtwork() {
            val localized = AppLocaleManager.localizedContext(appContext)
            val album = _selectedAlbum.value
            val variant = _selectedVariant.value
            val details = _fetchedMasterDetails.value

            if (album == null || details == null) {
                _errorMessage.value = localized.getString(CommonR.string.anim_art_err_no_stream)
                return
            }

            val targetUrl = variant?.variantUrl ?: details.squareMasterUrl ?: details.tallMasterUrl ?: return

            if (_isDownloadingFile.value) return
            _isDownloadingFile.value = true

            viewModelScope.launch {
                try {
                    val result =
                        animatedArtworkRepository.downloadAndSaveVariant(
                            albumId = album.id,
                            albumArtist = album.artist,
                            album = album.title,
                            catalogAlbumId = details.catalogAlbumId,
                            storefront = details.storefront,
                            orientation = preferredOrientation.value,
                            variantUrl = targetUrl,
                        )

                    if (result is AppResult.Success) {
                        _successDialogArtwork.value = result.data
                        refreshStorageBytes()
                    } else if (result is AppResult.Error) {
                        _errorMessage.value =
                            localized.getString(
                                CommonR.string.anim_art_err_download_failed,
                                result.error.localizedMessage(localized),
                            )
                    }
                } finally {
                    _isDownloadingFile.value = false
                }
            }
        }

        fun removeFromQueue(albumId: Long) {
            queuedAlbumIds.value = queuedAlbumIds.value - albumId
            if (_selectedDiagnosticAlbumId.value == albumId) {
                _selectedDiagnosticAlbumId.value = queuedAlbumIds.value.firstOrNull()
            }
            if (_selectedAlbum.value?.id == albumId) {
                _selectedAlbum.value = null
                _previewStreamUrl.value = null
                _availableVariants.value = emptyList()
                _selectedVariant.value = null
                _fetchedMasterDetails.value = null
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
            _selectedAlbum.value = album
            triggerFetchMotionArtwork()
        }

        private fun refreshStorageBytes() {
            viewModelScope.launch {
                _totalStorageBytes.value = animatedArtworkRepository.totalStorageBytes()
            }
        }
    }

/** When [cause] (or what caused it) is Apple's rate limit (HTTP 429): minutes remaining, else null. */
internal fun rateLimitMinutes(cause: Throwable?): Int? {
    val limit = generateSequence(cause) { it.cause }.filterIsInstance<RateLimitException>().firstOrNull() ?: return null
    return limit.minutesLeft()
}

/** When [cause] (or what caused it) is Apple's rate limit (HTTP 429): localized message to tell the user. */
internal fun rateLimitMessage(
    context: Context,
    cause: Throwable?,
): String? {
    val minutes = rateLimitMinutes(cause) ?: return null
    val localized = AppLocaleManager.localizedContext(context)
    val locale = AppLocaleManager.activeLocale(localized)
    return localized.resources.getQuantityString(
        CommonR.plurals.apple_rate_limit_wait,
        minutes,
        LocaleFormatters.formatInteger(minutes, locale),
    )
}
