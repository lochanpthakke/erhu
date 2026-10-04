package com.lochanpthakke.empee3.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.lochanpthakke.empee3.core.database.dao.AlbumDao
import com.lochanpthakke.empee3.core.database.dao.ArtistDao
import com.lochanpthakke.empee3.core.database.dao.LibraryModelDao
import com.lochanpthakke.empee3.core.database.dao.MetadataProcessingDao
import com.lochanpthakke.empee3.core.database.dao.PlaylistDao
import com.lochanpthakke.empee3.core.database.dao.RecentlyPlayedDao
import com.lochanpthakke.empee3.core.database.dao.SongArtistCrossRefDao
import com.lochanpthakke.empee3.core.database.dao.SongDao
import com.lochanpthakke.empee3.core.database.entity.AlbumArtistCrossRefEntity
import com.lochanpthakke.empee3.core.database.entity.AlbumEntity
import com.lochanpthakke.empee3.core.database.entity.ArtistAliasEntity
import com.lochanpthakke.empee3.core.database.entity.ArtistArtworkEntity
import com.lochanpthakke.empee3.core.database.entity.ArtistEntity
import com.lochanpthakke.empee3.core.database.entity.MetadataProcessingEntity
import com.lochanpthakke.empee3.core.database.entity.PlaylistEntity
import com.lochanpthakke.empee3.core.database.entity.PlaylistSongCrossRefEntity
import com.lochanpthakke.empee3.core.database.entity.RecentlyPlayedEntity
import com.lochanpthakke.empee3.core.database.entity.SongArtistCrossRefEntity
import com.lochanpthakke.empee3.core.database.entity.SongEntity
import com.lochanpthakke.empee3.core.database.entity.UnverifiedCreditSegmentEntity

@Database(
    entities = [
        SongEntity::class,
        AlbumEntity::class,
        ArtistEntity::class,
        PlaylistEntity::class,
        PlaylistSongCrossRefEntity::class,
        MetadataProcessingEntity::class,
        SongArtistCrossRefEntity::class,
        AlbumArtistCrossRefEntity::class,
        ArtistArtworkEntity::class,
        ArtistAliasEntity::class,
        UnverifiedCreditSegmentEntity::class,
        RecentlyPlayedEntity::class,
    ],
    version = 8,
    exportSchema = true,
)
abstract class EmPee3Database : RoomDatabase() {
    abstract fun songDao(): SongDao

    abstract fun albumDao(): AlbumDao

    abstract fun artistDao(): ArtistDao

    abstract fun playlistDao(): PlaylistDao

    abstract fun metadataProcessingDao(): MetadataProcessingDao

    abstract fun songArtistCrossRefDao(): SongArtistCrossRefDao

    abstract fun libraryModelDao(): LibraryModelDao

    abstract fun recentlyPlayedDao(): RecentlyPlayedDao
}
