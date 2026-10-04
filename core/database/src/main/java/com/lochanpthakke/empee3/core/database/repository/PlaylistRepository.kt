package com.lochanpthakke.empee3.core.database.repository

import com.lochanpthakke.empee3.core.common.result.AppError
import com.lochanpthakke.empee3.core.common.result.AppResult
import com.lochanpthakke.empee3.core.common.result.appResultOf
import com.lochanpthakke.empee3.core.database.dao.PlaylistDao
import com.lochanpthakke.empee3.core.database.dao.PlaylistWithCount
import com.lochanpthakke.empee3.core.database.dao.RecentlyPlayedDao
import com.lochanpthakke.empee3.core.database.entity.PlaylistEntity
import com.lochanpthakke.empee3.core.database.entity.RecentlyPlayedEntity
import com.lochanpthakke.empee3.core.database.mapper.toDomainModel
import com.lochanpthakke.empee3.core.model.Playlist
import com.lochanpthakke.empee3.core.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("TooManyFunctions")
interface PlaylistRepository {
    fun observePlaylists(): Flow<List<Playlist>>

    fun observePlaylist(playlistId: Long): Flow<Playlist?>

    fun observePlaylistSongs(playlistId: Long): Flow<List<Song>>

    /** Playlists played most recently first (only ones actually played from a playlist). */
    fun observeRecentPlaylists(limit: Int): Flow<List<Playlist>>

    suspend fun createPlaylist(name: String): AppResult<Long>

    suspend fun renamePlaylist(
        playlistId: Long,
        name: String,
    ): AppResult<Unit>

    suspend fun deletePlaylist(playlistId: Long): AppResult<Unit>

    /** Sets the playlist's own cover from an encoded square image, replacing any previous one. */
    suspend fun setPlaylistArtwork(
        playlistId: Long,
        imageBytes: ByteArray,
    ): AppResult<Unit>

    /** Back to the default playlist cover. */
    suspend fun clearPlaylistArtwork(playlistId: Long): AppResult<Unit>

    /** Records that playback was started from this playlist. */
    suspend fun markPlaylistPlayed(playlistId: Long)

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
@Suppress("TooManyFunctions")
class PlaylistRepositoryImpl
    @Inject
    constructor(
        private val playlistDao: PlaylistDao,
        private val recentlyPlayedDao: RecentlyPlayedDao,
        private val artworkStore: PlaylistArtworkStore,
        private val timeSource: TimeSource,
    ) : PlaylistRepository {
        override fun observePlaylists(): Flow<List<Playlist>> =
            playlistDao.getPlaylistsWithCounts().map { rows -> rows.map { it.toPlaylist() } }

        override fun observePlaylist(playlistId: Long): Flow<Playlist?> =
            observePlaylists().map { playlists -> playlists.find { it.id == playlistId } }

        override fun observePlaylistSongs(playlistId: Long): Flow<List<Song>> =
            playlistDao.getSongsForPlaylist(playlistId).map { songs -> songs.map { it.toDomainModel() } }

        override fun observeRecentPlaylists(limit: Int): Flow<List<Playlist>> =
            recentlyPlayedDao.observeRecentPlaylists(limit).map { rows -> rows.map { it.toPlaylist() } }

        override suspend fun createPlaylist(name: String): AppResult<Long> {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) return emptyNameError()
            return appResultOf(mapError = { AppError.DatabaseError("Could not create playlist", it) }) {
                playlistDao.insertPlaylist(PlaylistEntity(name = trimmed))
            }
        }

        override suspend fun renamePlaylist(
            playlistId: Long,
            name: String,
        ): AppResult<Unit> {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) return emptyNameError()
            return appResultOf(mapError = { AppError.DatabaseError("Could not rename playlist", it) }) {
                playlistDao.renamePlaylist(playlistId, trimmed, timeSource.now())
            }
        }

        override suspend fun deletePlaylist(playlistId: Long): AppResult<Unit> =
            appResultOf(mapError = { AppError.DatabaseError("Could not delete playlist", it) }) {
                val artwork = playlistDao.getPlaylistById(playlistId)?.artworkUri
                playlistDao.deletePlaylist(playlistId)
                recentlyPlayedDao.delete(RecentlyPlayedEntity.KIND_PLAYLIST, playlistId)
                artworkStore.delete(artwork)
            }

        override suspend fun setPlaylistArtwork(
            playlistId: Long,
            imageBytes: ByteArray,
        ): AppResult<Unit> =
            appResultOf(mapError = { AppError.DatabaseError("Could not save playlist artwork", it) }) {
                val previous = playlistDao.getPlaylistById(playlistId)?.artworkUri
                val now = timeSource.now()
                val uri = artworkStore.save(playlistId, imageBytes, now)
                playlistDao.setArtwork(playlistId, uri, now)
                artworkStore.delete(previous)
            }

        override suspend fun clearPlaylistArtwork(playlistId: Long): AppResult<Unit> =
            appResultOf(mapError = { AppError.DatabaseError("Could not remove playlist artwork", it) }) {
                val previous = playlistDao.getPlaylistById(playlistId)?.artworkUri
                playlistDao.setArtwork(playlistId, null, timeSource.now())
                artworkStore.delete(previous)
            }

        override suspend fun markPlaylistPlayed(playlistId: Long) =
            recentlyPlayedDao.upsert(RecentlyPlayedEntity(RecentlyPlayedEntity.KIND_PLAYLIST, playlistId, timeSource.now()))

        override suspend fun addSong(
            playlistId: Long,
            songId: Long,
        ): AppResult<Boolean> =
            appResultOf(mapError = { AppError.DatabaseError("Could not add song to playlist", it) }) {
                playlistDao.appendSongToPlaylist(playlistId, songId, timeSource.now())
            }

        override suspend fun removeSong(
            playlistId: Long,
            songId: Long,
        ): AppResult<Unit> =
            appResultOf(mapError = { AppError.DatabaseError("Could not remove song from playlist", it) }) {
                playlistDao.removeSongFromPlaylist(playlistId, songId)
                playlistDao.touchPlaylist(playlistId, timeSource.now())
            }

        private fun emptyNameError(): AppResult<Nothing> = AppResult.Error(AppError.ValidationError("Playlist name cannot be empty"))

        private fun PlaylistWithCount.toPlaylist() = Playlist(id = id, name = name, songCount = songCount, artworkUri = artworkUri)
    }
