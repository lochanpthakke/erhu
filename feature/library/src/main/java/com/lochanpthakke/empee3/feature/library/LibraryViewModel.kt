package com.lochanpthakke.empee3.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.lochanpthakke.empee3.core.database.repository.LibraryRepository
import com.lochanpthakke.empee3.core.model.Album
import com.lochanpthakke.empee3.core.model.Artist
import com.lochanpthakke.empee3.core.model.Song
import com.lochanpthakke.empee3.core.playback.repository.PlaybackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel
    @Inject
    constructor(
        private val libraryRepository: LibraryRepository,
        private val playbackRepository: PlaybackRepository,
    ) : ViewModel() {
        val songsPaged: Flow<PagingData<Song>> =
            libraryRepository
                .getSongsPaged()
                .cachedIn(viewModelScope)

        val albumsPaged: Flow<PagingData<Album>> =
            libraryRepository
                .getAlbumsPaged()
                .cachedIn(viewModelScope)

        val artistsPaged: Flow<PagingData<Artist>> =
            libraryRepository
                .getArtistsPaged()
                .cachedIn(viewModelScope)

        fun playSong(song: Song) {
            viewModelScope.launch {
                playbackRepository.addToQueue(song)
                playbackRepository.play()
            }
        }

        fun playAlbum(album: Album) {
            viewModelScope.launch {
                val songs = libraryRepository.getSongsByAlbumId(album.id).first()
                if (songs.isNotEmpty()) {
                    for (song in songs) {
                        playbackRepository.addToQueue(song)
                    }
                    playbackRepository.play()
                }
            }
        }

        fun playArtist(artist: Artist) {
            viewModelScope.launch {
                val songs = libraryRepository.getSongsByArtistId(artist.id).first()
                if (songs.isNotEmpty()) {
                    for (song in songs) {
                        playbackRepository.addToQueue(song)
                    }
                    playbackRepository.play()
                }
            }
        }

        fun triggerScan() {
            viewModelScope.launch {
                libraryRepository.triggerLibraryScan()
            }
        }
    }
