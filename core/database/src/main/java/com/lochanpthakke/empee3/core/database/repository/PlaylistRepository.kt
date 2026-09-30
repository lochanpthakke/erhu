package com.lochanpthakke.empee3.core.database.repository

import com.lochanpthakke.empee3.core.common.result.AppError
import com.lochanpthakke.empee3.core.common.result.AppResult
import com.lochanpthakke.empee3.core.common.result.appResultOf
import com.lochanpthakke.empee3.core.database.dao.PlaylistDao
import com.lochanpthakke.empee3.core.database.entity.PlaylistEntity
import com.lochanpthakke.empee3.core.database.mapper.toDomainModel
import com.lochanpthakke.empee3.core.model.Playlist
import com.lochanpthakke.empee3.core.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

interface PlaylistRepository {
    fun observePlaylists(): Flow<List<Playlist>>

    fun observePlaylist(playlistId: Long): Flow<Playlist?>

    fun observePlaylistSongs(playlistId: Long): Flow<List<Song>>

    suspend fun createPlaylist(name: String): AppResult<Long>

    suspend fun deletePlaylist(playlistId: Long): AppResult<Unit>

    /** Success(false) when the song was already in the playlist. */
    suspend fun addSong(
        playlistId: Long,
        songId: Long,
    ): AppResult<Boolean>

    suspend fun removeSong(
        playlistId: Long,
        songId: Long,
    ): AppResult<Unit>
}

@Singleton
class PlaylistRepositoryImpl
    @Inject
    constructor(
        private val playlistDao: PlaylistDao,
    ) : PlaylistRepository {
        override fun observePlaylists(): Flow<List<Playlist>> =
            playlistDao.getPlaylistsWithCounts().map { rows ->
                rows.map { Playlist(id = it.id, name = it.name, songCount = it.songCount) }
            }

        override fun observePlaylist(playlistId: Long): Flow<Playlist?> =
            observePlaylists().map { playlists -> playlists.find { it.id == playlistId } }

        override fun observePlaylistSongs(playlistId: Long): Flow<List<Song>> =
            playlistDao.getSongsForPlaylist(playlistId).map { songs -> songs.map { it.toDomainModel() } }

        override suspend fun createPlaylist(name: String): AppResult<Long> {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) return AppResult.Error(AppError.ValidationError("Playlist name cannot be empty"))
            return appResultOf(mapError = { AppError.DatabaseError("Could not create playlist", it) }) {
                playlistDao.insertPlaylist(PlaylistEntity(name = trimmed))
            }
        }

        override suspend fun deletePlaylist(playlistId: Long): AppResult<Unit> =
            appResultOf(mapError = { AppError.DatabaseError("Could not delete playlist", it) }) {
                playlistDao.deletePlaylist(playlistId)
            }

        override suspend fun addSong(
            playlistId: Long,
            songId: Long,
        ): AppResult<Boolean> =
            appResultOf(mapError = { AppError.DatabaseError("Could not add song to playlist", it) }) {
                playlistDao.appendSongToPlaylist(playlistId, songId, System.currentTimeMillis())
            }

        override suspend fun removeSong(
            playlistId: Long,
            songId: Long,
        ): AppResult<Unit> =
            appResultOf(mapError = { AppError.DatabaseError("Could not remove song from playlist", it) }) {
                playlistDao.removeSongFromPlaylist(playlistId, songId)
                playlistDao.touchPlaylist(playlistId, System.currentTimeMillis())
            }
    }
