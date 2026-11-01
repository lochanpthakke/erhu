@file:Suppress("detekt:All")

package com.erhu.erhu.core.database.lookup

import com.erhu.erhu.core.database.dao.SongDao
import com.erhu.erhu.core.database.mapper.toDomainModel
import com.erhu.erhu.core.model.Song
import com.erhu.erhu.core.model.SongLookup
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomSongLookup
    @Inject
    constructor(
        private val songDao: SongDao,
    ) : SongLookup {
        override suspend fun getSongsByIds(ids: List<Long>): List<Song> = songDao.getSongsByIds(ids).map { it.toDomainModel() }
    }
