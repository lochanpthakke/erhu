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

    fun getFavoriteSongs(): Flow<List<Song>>

    fun getSongById(id: Long): Flow<Song?>

    fun getAlbumIdForSong(songId: Long): Flow<Long?>

    fun getSongsByAlbumId(albumId: Long): Flow<List<Song>>

    fun getSongsByArtistId(artistId: Long): Flow<List<Song>>

    fun getPrimarySongsByArtistId(artistId: Long): Flow<List<Song>>

    fun getFeaturedSongsByArtistId(artistId: Long): Flow<List<Song>>

    /** Songs whose unresolved credit contains this artist's exact name; shown as "unverified". */
    fun getUnverifiedSongsByArtistKey(artistKey: String): Flow<List<Song>>

    fun getAllAlbums(): Flow<List<Album>>

    fun getAlbumsByArtistId(artistId: Long): Flow<List<Album>>

    fun getAlbumsPaged(): Flow<PagingData<Album>>

    fun getAlbumById(id: Long): Flow<Album?>

    fun getAllArtists(): Flow<List<Artist>>

    fun getArtistsPaged(): Flow<PagingData<Artist>>

    fun getArtistById(id: Long): Flow<Artist?>

    fun searchSongs(query: String): Flow<List<Song>>

    suspend fun updateFilePath(
        songId: Long,
        newPath: String,
    ): AppResult<Unit>

    /** The songs with these ids, in the order of [ids]; missing ones are left out. */
    suspend fun getSongsByIds(ids: List<Long>): List<Song>

    /** Every song's discovery text (title, artist, album, genres, year, BPM, lyrics excerpt); emits on changes. */
    fun observeSongTags(): Flow<List<com.lochanpthakke.empee3.core.model.SongTags>>

    /** Stores a BPM just written to the song's file, so the library shows it before the rescan. */
    suspend fun updateSongBpm(
        songId: Long,
        bpm: Int?,
    ): AppResult<Unit>

    suspend fun updateSongTags(
        songId: Long,
        title: String,
        artist: String,
        album: String,
    ): AppResult<Unit>

    /** Emits false when the song is not a favorite or no longer exists. */
    fun observeIsFavorite(songId: Long): Flow<Boolean>

    /** Lyrics embedded in the file's tags (read during scan enrichment), if any. */
    fun observeEmbeddedLyrics(songId: Long): Flow<String?>

    /** Absolute file path from MediaStore, needed by tag writers. */
    suspend fun getSongFilePath(songId: Long): String?

    suspend fun toggleFavorite(songId: Long): AppResult<Unit>

    suspend fun triggerLibraryScan(): AppResult<Unit>

    /**
     * Rescans after the app itself changed files (e.g. tag edits). Unlike
     * [triggerLibraryScan] it queues behind a scan already in progress, so the
     * change is never missed because an older scan was running.
     */
    suspend fun requestRescanAfterChange(): AppResult<Unit>

    /** True while a library scan is queued or running. */
    fun observeIsScanning(): Flow<Boolean>
}
