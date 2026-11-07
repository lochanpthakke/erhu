package com.erhu.erhu.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erhu.erhu.core.database.repository.LibraryRepository
import com.erhu.erhu.core.model.Album
import com.erhu.erhu.core.model.ArtistOnSongs
import com.erhu.erhu.core.model.Song
import com.erhu.erhu.feature.library.mix.RadioPlan
import com.erhu.erhu.feature.library.mix.RadioRepository
import com.erhu.erhu.feature.library.mix.RadioSeed
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import javax.inject.Inject

/** An immutable generation snapshot. Library changes update rows, never regenerate its queue. */
data class RadioStationContent(
    val album: Album,
    val plan: RadioPlan?,
    val songs: List<Song> = emptyList(),
    val artists: List<ArtistOnSongs> = emptyList(),
)

internal data class RadioPortrait(
    val artistId: Long?,
    val name: String,
    val imageUri: String?,
    val song: Song?,
)

internal class RadioStationLoader(
    private val album: suspend (Long) -> Album?,
    private val build: suspend (RadioSeed) -> RadioPlan?,
) {
    suspend fun load(albumId: Long): RadioStationContent? {
        val record = album(albumId) ?: return null
        return RadioStationContent(record, build(RadioSeed.Album(albumId)))
    }
}

@HiltViewModel
class RadioStationViewModel internal constructor(
    private val loader: RadioStationLoader,
    private val observeArtists: (List<Long>, Long) -> Flow<List<ArtistOnSongs>> = { _, _ -> flowOf(emptyList()) },
    private val observeSongs: (List<Long>) -> Flow<List<Song>>,
) : ViewModel() {
    @Inject
    constructor(library: LibraryRepository, radio: RadioRepository) : this(
        RadioStationLoader({ library.getAlbumById(it).first() }, { radio.build(it) }),
        library::observeArtistsForSongs,
        library::observeSongsByIds,
    )

    private val _content = MutableStateFlow<RadioStationContent?>(null)
    val content = _content.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private var openAlbum: Long? = null
    private var loading: Job? = null

    fun open(albumId: Long) {
        if (openAlbum == albumId && (loading?.isActive == true || _content.value != null)) return
        openAlbum = albumId
        loading?.cancel()
        _content.value = null
        _error.value = null
        loading =
            viewModelScope.launch {
                try {
                    val snapshot = loader.load(albumId)
                    if (snapshot == null) {
                        _error.value = "This station's album is no longer available."
                        return@launch
                    }
                    val plan = snapshot.plan
                    if (plan == null) {
                        _content.value = snapshot
                        return@launch
                    }
                    val ids = (listOf(plan.startSongId) + plan.songIds).distinct()
                    combine(observeSongs(ids), observeArtists(ids, plan.startSongId)) { songs, artists ->
                        snapshot.copy(songs = songs, artists = artists)
                    }.collect { _content.value = it }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    _error.value = "Could not load this radio station. Try again."
                }
            }
    }
}
