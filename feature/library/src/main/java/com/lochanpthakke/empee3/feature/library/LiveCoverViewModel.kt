package com.lochanpthakke.empee3.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lochanpthakke.empee3.core.common.result.AppResult
import com.lochanpthakke.empee3.core.database.repository.AnimatedArtworkRepository
import com.lochanpthakke.empee3.core.database.repository.LiveCoverCheck
import com.lochanpthakke.empee3.core.database.repository.LiveCoverVariant
import com.lochanpthakke.empee3.core.model.Album
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

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
    ) : LiveCoverUi

    data class RateLimited(
        val message: String,
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

        private var album: Album? = null
        private var download: Job? = null

        fun bind(album: Album) {
            if (this.album?.id == album.id) return
            this.album = album
            viewModelScope.launch { _state.value = check(album) }
        }

        fun selectVariant(variant: LiveCoverVariant) {
            val current = _state.value
            val offer = offerOf(current) ?: return
            val updatedOffer = offer.copy(
                selectedVariant = variant,
                width = variant.width,
                height = variant.height,
                bytes = variant.bytes,
            )
            _state.value = when (current) {
                is LiveCoverUi.Offer -> LiveCoverUi.Offer(updatedOffer)
                is LiveCoverUi.Downloading -> LiveCoverUi.Downloading(updatedOffer)
                is LiveCoverUi.Failed -> LiveCoverUi.Failed(updatedOffer, current.message)
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
                            is AppResult.Error ->
                                LiveCoverUi.Failed(offer, liveCoverErrorMessage(result.error.cause, result.error.message))
                            is AppResult.Success ->
                                (check(album) as? LiveCoverUi.Saved)
                                    ?: LiveCoverUi.Failed(offer, "Apple Music no longer offers a live cover for this album.")
                        }
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

        private suspend fun check(album: Album): LiveCoverUi =
            when (val result = repository.checkLiveCover(album.id, album.artist, album.title, album.songCount)) {
                is AppResult.Error -> {
                    val limitMessage = rateLimitMessage(result.error.cause)
                    if (limitMessage != null) {
                        LiveCoverUi.RateLimited(limitMessage)
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
): String =
    rateLimitMessage(cause)
        ?: if (message.isNullOrBlank()) "The live cover couldn't be downloaded." else "The live cover couldn't be downloaded: $message"
