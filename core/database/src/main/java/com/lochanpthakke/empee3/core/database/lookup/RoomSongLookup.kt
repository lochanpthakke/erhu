@file:Suppress("detekt:All")

package com.lochanpthakke.empee3.core.database.lookup

import com.lochanpthakke.empee3.core.database.dao.SongDao
import com.lochanpthakke.empee3.core.database.mapper.toDomainModel
import com.lochanpthakke.empee3.core.model.Song
import com.lochanpthakke.empee3.core.model.SongLookup
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
