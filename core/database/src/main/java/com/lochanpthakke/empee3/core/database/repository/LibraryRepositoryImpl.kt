@file:Suppress("detekt:All")

package com.lochanpthakke.empee3.core.database.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.lochanpthakke.empee3.core.common.result.AppResult
import com.lochanpthakke.empee3.core.common.result.appResultOf
import com.lochanpthakke.empee3.core.database.dao.AlbumDao
import com.lochanpthakke.empee3.core.database.dao.ArtistDao
import com.lochanpthakke.empee3.core.database.dao.SongDao
import com.lochanpthakke.empee3.core.database.mapper.toDomainModel
import com.lochanpthakke.empee3.core.database.scanner.LibraryScanWorker
import com.lochanpthakke.empee3.core.model.Album
import com.lochanpthakke.empee3.core.model.Artist
import com.lochanpthakke.empee3.core.model.Song
import dagger.Lazy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@Suppress("TooManyFunctions")
class LibraryRepositoryImpl
    @Inject
    constructor(
        private val songDao: SongDao,
        private val albumDao: AlbumDao,
        private val artistDao: ArtistDao,
        // Lazy: this repository is created during Application injection (widget
        // updater), before HiltWorkerFactory is ready for WorkManager's config.
        private val workManager: Lazy<WorkManager>,
    ) : LibraryRepository {
        override fun getSongsPaged(): Flow<PagingData<Song>> =
            Pager(config = PagingConfig(pageSize = DEFAULT_PAGE_SIZE)) {
                songDao.getSongsPaged()
            }.flow.map { pagingData ->
                pagingData.map { entity -> entity.toDomainModel() }
            }

        override fun getFavoriteSongsPaged(): Flow<PagingData<Song>> =
            Pager(config = PagingConfig(pageSize = DEFAULT_PAGE_SIZE)) {
                songDao.getFavoriteSongsPaged()
            }.flow.map { pagingData ->
                pagingData.map { entity -> entity.toDomainModel() }
            }

        override fun getAllSongs(): Flow<List<Song>> =
            songDao.getAllSongs().map { list ->
                list.map { it.toDomainModel() }
            }

        override fun getFavoriteSongs(): Flow<List<Song>> =
            songDao.getFavoriteSongs().map { list ->
                list.map { it.toDomainModel() }
            }

        override fun getSongById(id: Long): Flow<Song?> = songDao.observeSongById(id).map { it?.toDomainModel() }

        override fun getSongsByAlbumId(albumId: Long): Flow<List<Song>> =
            songDao.getSongsByAlbumId(albumId).map { list ->
                list.map { it.toDomainModel() }
            }

        override fun getSongsByArtistId(artistId: Long): Flow<List<Song>> =
            songDao.getSongsByArtistId(artistId).map { list ->
                list.map { it.toDomainModel() }
            }

        override fun getPrimarySongsByArtistId(artistId: Long): Flow<List<Song>> =
            songDao.getPrimarySongsByArtistId(artistId).map { list ->
                list.map { it.toDomainModel() }
            }

        override fun getFeaturedSongsByArtistId(artistId: Long): Flow<List<Song>> =
            songDao.getFeaturedSongsByArtistId(artistId).map { list ->
                list.map { it.toDomainModel() }
            }

        override fun getUnverifiedSongsByArtistKey(artistKey: String): Flow<List<Song>> =
            songDao.getUnverifiedSongsByArtistKey(artistKey).map { list ->
                list.map { it.toDomainModel() }
            }

        override fun getAllAlbums(): Flow<List<Album>> =
            albumDao.getAllAlbums().map { list ->
                list.map { it.toDomainModel() }
            }

        override fun getAlbumsByArtistId(artistId: Long): Flow<List<Album>> =
            albumDao.getAlbumsByArtistId(artistId).map { list ->
                list.map { it.toDomainModel() }
            }

        override fun getAlbumsPaged(): Flow<PagingData<Album>> =
            Pager(config = PagingConfig(pageSize = DEFAULT_PAGE_SIZE)) {
                albumDao.getAlbumsPaged()
            }.flow.map { pagingData ->
                pagingData.map { entity -> entity.toDomainModel() }
            }

        override fun getAlbumById(id: Long): Flow<Album?> = albumDao.observeAlbumById(id).map { it?.toDomainModel() }

        override fun getAllArtists(): Flow<List<Artist>> =
            artistDao.getAllArtists().map { list ->
                list.map { it.toDomainModel() }
            }

        override fun getArtistsPaged(): Flow<PagingData<Artist>> =
            Pager(config = PagingConfig(pageSize = DEFAULT_PAGE_SIZE)) {
                artistDao.getArtistsPaged()
            }.flow.map { pagingData ->
                pagingData.map { entity -> entity.toDomainModel() }
            }

        override fun getArtistById(id: Long): Flow<Artist?> = artistDao.observeArtistById(id).map { it?.toDomainModel() }

        override fun searchSongs(query: String): Flow<List<Song>> =
            songDao.searchSongs(query.trim()).map { list ->
                list.map { it.toDomainModel() }
            }

        override fun observeIsFavorite(songId: Long): Flow<Boolean> =
            songDao.observeSongById(songId).map { it?.isFavorite == true }.distinctUntilChanged()

        override fun observeEmbeddedLyrics(songId: Long): Flow<String?> =
            songDao.observeSongById(songId).map { it?.lyrics?.takeIf { lyrics -> lyrics.isNotBlank() } }.distinctUntilChanged()

        override suspend fun getSongFilePath(songId: Long): String? = songDao.getSongById(songId)?.filePath?.takeIf { it.isNotBlank() }

        override suspend fun toggleFavorite(songId: Long): AppResult<Unit> =
            appResultOf {
                val song = songDao.getSongById(songId)
                if (song != null) {
                    songDao.setFavorite(songId, !song.isFavorite)
                }
            }

        override suspend fun triggerLibraryScan(): AppResult<Unit> =
            appResultOf {
                val workRequest = OneTimeWorkRequestBuilder<LibraryScanWorker>().build()
                // KEEP: a scan already queued/running will pick up the current state;
                // REPLACE used to cancel it mid-way whenever the UI asked again.
                workManager.get().enqueueUniqueWork(
                    LibraryScanWorker.WORK_NAME,
                    ExistingWorkPolicy.KEEP,
                    workRequest,
                )
            }

        override suspend fun requestRescanAfterChange(): AppResult<Unit> =
            appResultOf {
                workManager.get().enqueueUniqueWork(
                    LibraryScanWorker.WORK_NAME,
                    ExistingWorkPolicy.APPEND_OR_REPLACE,
                    OneTimeWorkRequestBuilder<LibraryScanWorker>().build(),
                )
            }

        override suspend fun updateFilePath(songId: Long, newPath: String): AppResult<Unit> =
            appResultOf {
                songDao.updateFilePath(songId, newPath)
            }

        override suspend fun getSongsByIds(ids: List<Long>): List<Song> {
            // Chunked: SQLite limits bound variables (999 before API 30).
            val byId = ids.distinct().chunked(SQL_CHUNK).flatMap { songDao.getSongsByIds(it) }.associateBy { it.id }
            return ids.mapNotNull { byId[it]?.toDomainModel() }
        }

        override fun observeSongTags(): Flow<List<com.lochanpthakke.empee3.core.model.SongTags>> =
            songDao.observeSongTags().map { rows ->
                rows.map {
                    com.lochanpthakke.empee3.core.model.SongTags(
                        songId = it.id,
                        title = it.title,
                        artist = it.artist,
                        album = it.album,
                        genre = it.genre,
                        year = it.year,
                        bpm = it.bpm,
                        lyricsExcerpt = it.lyricsExcerpt,
                    )
                }
            }

        override suspend fun updateSongBpm(
            songId: Long,
            bpm: Int?,
        ): AppResult<Unit> =
            appResultOf {
                songDao.updateBpm(songId, bpm)
            }

        override fun observeIsScanning(): Flow<Boolean> =
            workManager
                .get()
                .getWorkInfosForUniqueWorkFlow(LibraryScanWorker.WORK_NAME)
                .map { infos -> infos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED } }
                .distinctUntilChanged()

        companion object {
            private const val DEFAULT_PAGE_SIZE = 30
        }
    }

private const val SQL_CHUNK = 900
