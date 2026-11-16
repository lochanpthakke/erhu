package com.erhu.erhu.core.database.lookup

import com.erhu.erhu.core.database.dao.RemoteLibraryDao
import com.erhu.erhu.core.database.dao.SongDao
import com.erhu.erhu.core.database.mapper.toDomainModel
import com.erhu.erhu.core.database.repository.toSong
import com.erhu.erhu.core.model.RemoteIds
import com.erhu.erhu.core.model.Song
import com.erhu.erhu.core.model.SongLookup
import javax.inject.Inject
import javax.inject.Singleton

/** Device songs come from `songs`, remote ones from `remote_tracks`; the id range says which. */
@Singleton
class RoomSongLookup
    @Inject
    constructor(
        private val songDao: SongDao,
        private val remoteDao: RemoteLibraryDao,
    ) : SongLookup {
        override suspend fun getSongsByIds(ids: List<Long>): List<Song> {
            val (remote, local) = ids.partition(RemoteIds::isRemote)
            val localSongs = if (local.isEmpty()) emptyList() else songDao.getSongsByIds(local).map { it.toDomainModel() }
            val remoteSongs = if (remote.isEmpty()) emptyList() else remoteDao.getTracksByIds(remote).mapNotNull { it.toSong() }
            return localSongs + remoteSongs
        }
    }
