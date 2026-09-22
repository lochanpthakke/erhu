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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
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
    }
