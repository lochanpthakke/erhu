@file:Suppress("detekt:All")

package com.lochanpthakke.empee3.core.database.repository

import androidx.paging.PagingData
import com.lochanpthakke.empee3.core.common.result.AppResult
import com.lochanpthakke.empee3.core.model.Album
import com.lochanpthakke.empee3.core.model.Artist
import com.lochanpthakke.empee3.core.model.Song
import kotlinx.coroutines.flow.Flow

@Suppress("TooManyFunctions")
interface LibraryRepository {
    fun getSongsPaged(): Flow<PagingData<Song>>

    fun getFavoriteSongsPaged(): Flow<PagingData<Song>>

    fun getAllSongs(): Flow<List<Song>>

    fun getSongById(id: Long): Flow<Song?>

    fun getSongsByAlbumId(albumId: Long): Flow<List<Song>>

    fun getSongsByArtistId(artistId: Long): Flow<List<Song>>

    fun getAllAlbums(): Flow<List<Album>>

    fun getAlbumsPaged(): Flow<PagingData<Album>>

    fun getAlbumById(id: Long): Flow<Album?>

    fun getAllArtists(): Flow<List<Artist>>

    fun getArtistsPaged(): Flow<PagingData<Artist>>

    fun searchSongs(query: String): Flow<List<Song>>

    suspend fun toggleFavorite(songId: Long): AppResult<Unit>

    suspend fun triggerLibraryScan(): AppResult<Unit>
}
