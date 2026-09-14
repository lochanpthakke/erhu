package com.lochanpthakke.empee3.core.database.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryRepositoryImpl
    @Inject
    constructor(
        private val songDao: SongDao,
        private val albumDao: AlbumDao,
        private val artistDao: ArtistDao,
        private val workManager: WorkManager,
    ) : LibraryRepository {
        override fun getSongsPaged(): Flow<PagingData<Song>> =
            Pager(config = PagingConfig(pageSize = DEFAULT_PAGE_SIZE)) {
                songDao.getSongsPaged()
            }.flow.map { pagingData ->
                pagingData.map { entity -> entity.toDomainModel() }
            }

        override fun getAllSongs(): Flow<List<Song>> =
            songDao.getAllSongs().map { list ->
                list.map { it.toDomainModel() }
            }

        override fun getSongById(id: Long): Flow<Song?> =
            songDao.getAllSongs().map { list ->
                list.find { it.id == id }?.toDomainModel()
            }

        override fun getSongsByAlbumId(albumId: Long): Flow<List<Song>> =
            songDao.getSongsByAlbumId(albumId).map { list ->
                list.map { it.toDomainModel() }
            }

        override fun getSongsByArtistId(artistId: Long): Flow<List<Song>> =
            songDao.getSongsByArtistId(artistId).map { list ->
                list.map { it.toDomainModel() }
            }

        override fun getAllAlbums(): Flow<List<Album>> =
            albumDao.getAllAlbums().map { list ->
                list.map { it.toDomainModel() }
            }

        override fun getAlbumsPaged(): Flow<PagingData<Album>> =
            Pager(config = PagingConfig(pageSize = DEFAULT_PAGE_SIZE)) {
                albumDao.getAlbumsPaged()
            }.flow.map { pagingData ->
                pagingData.map { entity -> entity.toDomainModel() }
            }

        override fun getAlbumById(id: Long): Flow<Album?> =
            albumDao.getAllAlbums().map { list ->
                list.find { it.id == id }?.toDomainModel()
            }

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

        override fun searchSongs(query: String): Flow<List<Song>> =
            songDao.searchSongs(query).map { list ->
                list.map { it.toDomainModel() }
            }

        override suspend fun triggerLibraryScan(): AppResult<Unit> =
            appResultOf {
                val workRequest = OneTimeWorkRequestBuilder<LibraryScanWorker>().build()
                workManager.enqueueUniqueWork(
                    LibraryScanWorker.WORK_NAME,
                    ExistingWorkPolicy.KEEP,
                    workRequest,
                )
            }

        companion object {
            private const val DEFAULT_PAGE_SIZE = 30
        }
    }
