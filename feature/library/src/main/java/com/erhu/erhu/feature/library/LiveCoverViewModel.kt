package com.erhu.erhu.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erhu.erhu.core.common.result.AppError
import com.erhu.erhu.core.common.result.AppResult
import com.erhu.erhu.core.database.repository.AnimatedArtworkRepository
import com.erhu.erhu.core.database.repository.LiveCoverCheck
import com.erhu.erhu.core.database.repository.LiveCoverVariant
import com.erhu.erhu.core.model.Album
import com.erhu.erhu.core.model.ArtworkCompressionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LiveCoverFailureKind {
    NO_LONGER_OFFERED,
    RATE_LIMITED,
    DOWNLOAD_FAILED,
}

/** What an album page shows for the album's live cover (see [LiveCoverViewModel]). */
sealed interface LiveCoverUi {
    /** Nothing: still checking, no live cover, or the check failed (tried again on the next visit). */
    data object Hidden : LiveCoverUi

    data class Offer(
        val offer: LiveCoverCheck.Downloadable,
    ) : LiveCoverUi

    data class Downloading(
        val offer: LiveCoverCheck.Downloadable,
    ) : LiveCoverUi

    data class Saved(
        val saved: LiveCoverCheck.Downloaded,
    ) : LiveCoverUi

    data class Failed(
        val offer: LiveCoverCheck.Downloadable,
        val message: String,
        val kind: LiveCoverFailureKind = LiveCoverFailureKind.DOWNLOAD_FAILED,
        val rateLimitMinutes: Int? = null,
        val error: AppError? = null,
    ) : LiveCoverUi

    data class RateLimited(
        val message: String,
        val minutes: Int? = null,
    ) : LiveCoverUi
}

/**
 * An album page's live cover: checks once whether the album has one (from the same Apple source as
 * the Animated Artwork fetcher), and downloads, cancels or removes it. The download itself runs in
 * the repository, one per album, so repeated taps or leaving the page never start a second one.
 * A saved cover reaches the album header and Now Playing through the repository's flow.
 */
@HiltViewModel
class LiveCoverViewModel
    @Inject
    constructor(
        private val repository: AnimatedArtworkRepository,
    ) : ViewModel() {
        private val _state = MutableStateFlow<LiveCoverUi>(LiveCoverUi.Hidden)
        val state: StateFlow<LiveCoverUi> = _state.asStateFlow()
        private val _compression = MutableStateFlow(ArtworkCompressionState())
        val compression: StateFlow<ArtworkCompressionState> = _compression.asStateFlow()
        private var compressionObservation: Job? = null

        private var album: Album? = null
        private var download: Job? = null

        fun bind(album: Album) {
            if (this.album?.id == album.id) return
            this.album = album
            compressionObservation?.cancel()
            compressionObservation =
                viewModelScope.launch {
                    repository.compression.observe(album.id).collect {
                        _compression.value = it
                        if (it.compressedBytes != null) _state.value = check(album)
                    }
                }
            viewModelScope.launch {
                _state.value = check(album)
                if (_state.value is LiveCoverUi.Saved) repository.compression.prepare(album.id)
            }
        }

        fun selectVariant(variant: LiveCoverVariant) {
            val current = _state.value
            val offer = offerOf(current) ?: return
            val updatedOffer =
                offer.copy(
                    selectedVariant = variant,
                    width = variant.width,
                    height = variant.height,
                    bytes = variant.bytes,
                )
            _state.value =
                when (current) {
                    is LiveCoverUi.Offer -> LiveCoverUi.Offer(updatedOffer)
                    is LiveCoverUi.Downloading -> LiveCoverUi.Downloading(updatedOffer)
                    is LiveCoverUi.Failed -> current.copy(offer = updatedOffer)
                    else -> current
                }
        }

        fun download() {
            val album = album
            val offer = offerOf(_state.value)
            if (album == null || offer == null || download?.isActive == true) return
            _state.value = LiveCoverUi.Downloading(offer)
            download =
                viewModelScope.launch {
                    val variantUrl = offer.selectedVariant?.variantUrl
                    val result =
                        try {
                            if (!variantUrl.isNullOrBlank()) {
                                repository.downloadAndSaveVariant(
                                    albumId = album.id,
                                    albumArtist = album.artist,
                                    album = album.title,
                                    catalogAlbumId = "",
                                    storefront = "",
                                    orientation = offer.orientation,
                                    variantUrl = variantUrl,
                                )
                            } else {
                                repository.fetchAndStoreArtwork(album.id, album.artist, album.title, album.songCount, offer.orientation)
                            }
                        } catch (e: CancellationException) {
                            // Cancelled (here or in the repository): the cover is offered again.
                            _state.value = LiveCoverUi.Offer(offer)
                            throw e
                        }
                    _state.value =
                        when (result) {
                            is AppResult.Error -> {
                                val minutes = rateLimitMinutes(result.error.cause)
                                LiveCoverUi.Failed(
                                    offer = offer,
                                    message = liveCoverErrorMessage(result.error.cause, result.error.message),
                                    kind =
                                        if (minutes != null) {
                                            LiveCoverFailureKind.RATE_LIMITED
                                        } else {
                                            LiveCoverFailureKind.DOWNLOAD_FAILED
                                        },
                                    rateLimitMinutes = minutes,
                                    error = result.error,
                                )
                            }
                            is AppResult.Success ->
                                (check(album) as? LiveCoverUi.Saved)
                                    ?: LiveCoverUi.Failed(
                                        offer = offer,
                                        message = "Apple Music no longer offers a live cover for this album.",
                                        kind = LiveCoverFailureKind.NO_LONGER_OFFERED,
                                    )
                        }
                    if (_state.value is LiveCoverUi.Saved) repository.compression.prepare(album.id)
                }
        }

        fun cancel() {
            val album = album ?: return
            val offer = offerOf(_state.value) ?: return
            repository.cancelDownload(album.id)
            download?.cancel()
            _state.value = LiveCoverUi.Offer(offer)
        }

        fun remove() {
            val album = album ?: return
            if (_state.value !is LiveCoverUi.Saved) return
            viewModelScope.launch {
                repository.deleteArtwork(album.id)
                _state.value = check(album)
            }
        }

        fun compress() {
            val current = album ?: return
            if (_state.value !is LiveCoverUi.Saved || !_compression.value.available) return
            viewModelScope.launch { repository.compression.start(current.id) }
        }

        fun cancelCompression() {
            album?.let { repository.compression.cancel(it.id) }
        }

        private suspend fun check(album: Album): LiveCoverUi =
            when (val result = repository.checkLiveCover(album.id, album.artist, album.title, album.songCount)) {
                is AppResult.Error -> {
                    val minutes = rateLimitMinutes(result.error.cause)
                    if (minutes != null) {
                        LiveCoverUi.RateLimited(
                            message =
                                if (minutes == 1) {
                                    "Apple Music is limiting requests right now. Try again in 1 minute."
                                } else {
                                    "Apple Music is limiting requests right now. Try again in $minutes minutes."
                                },
                            minutes = minutes,
                        )
                    } else {
                        LiveCoverUi.Hidden
                    }
                }
                is AppResult.Success ->
                    when (val check = result.data) {
                        LiveCoverCheck.None -> LiveCoverUi.Hidden
                        is LiveCoverCheck.Downloadable -> LiveCoverUi.Offer(check)
                        is LiveCoverCheck.Downloaded -> LiveCoverUi.Saved(check)
                    }
            }

        private fun offerOf(state: LiveCoverUi): LiveCoverCheck.Downloadable? =
            when (state) {
                is LiveCoverUi.Offer -> state.offer
                is LiveCoverUi.Downloading -> state.offer
                is LiveCoverUi.Failed -> state.offer
                LiveCoverUi.Hidden, is LiveCoverUi.Saved, is LiveCoverUi.RateLimited -> null
            }
    }

/** What to tell the user when a live cover download failed. */
private fun liveCoverErrorMessage(
    cause: Throwable?,
    message: String?,
): String {
    val minutes = rateLimitMinutes(cause)
    if (minutes != null) {
        return if (minutes == 1) {
            "Apple Music is limiting requests right now. Try again in 1 minute."
        } else {
            "Apple Music is limiting requests right now. Try again in $minutes minutes."
        }
    }
    return if (message.isNullOrBlank()) "The live cover couldn't be downloaded." else "The live cover couldn't be downloaded: $message"
}
