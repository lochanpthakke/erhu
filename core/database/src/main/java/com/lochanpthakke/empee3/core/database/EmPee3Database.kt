package com.lochanpthakke.empee3.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.lochanpthakke.empee3.core.database.dao.AlbumDao
import com.lochanpthakke.empee3.core.database.dao.ArtistDao
import com.lochanpthakke.empee3.core.database.dao.PlaylistDao
import com.lochanpthakke.empee3.core.database.dao.SongDao
import com.lochanpthakke.empee3.core.database.entity.AlbumEntity
import com.lochanpthakke.empee3.core.database.entity.ArtistEntity
import com.lochanpthakke.empee3.core.database.entity.PlaylistEntity
import com.lochanpthakke.empee3.core.database.entity.PlaylistSongCrossRefEntity
import com.lochanpthakke.empee3.core.database.entity.SongEntity

@Database(
    entities = [
        SongEntity::class,
        AlbumEntity::class,
        ArtistEntity::class,
        PlaylistEntity::class,
        PlaylistSongCrossRefEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class EmPee3Database : RoomDatabase() {
    abstract fun songDao(): SongDao

    abstract fun albumDao(): AlbumDao

    abstract fun artistDao(): ArtistDao

    abstract fun playlistDao(): PlaylistDao
}
