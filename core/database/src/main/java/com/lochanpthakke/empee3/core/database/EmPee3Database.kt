package com.lochanpthakke.empee3.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.lochanpthakke.empee3.core.database.dao.AlbumDao
import com.lochanpthakke.empee3.core.database.dao.ArtistDao
import com.lochanpthakke.empee3.core.database.dao.LearnedWatermarkDao
import com.lochanpthakke.empee3.core.database.dao.LibraryModelDao
import com.lochanpthakke.empee3.core.database.dao.ListeningStatsDao
import com.lochanpthakke.empee3.core.database.dao.MetadataProcessingDao
import com.lochanpthakke.empee3.core.database.dao.PlaylistDao
import com.lochanpthakke.empee3.core.database.dao.RecentlyPlayedDao
import com.lochanpthakke.empee3.core.database.dao.SongArtistCrossRefDao
import com.lochanpthakke.empee3.core.database.dao.SongDao
import com.lochanpthakke.empee3.core.database.dao.TrackBookmarkDao
import com.lochanpthakke.empee3.core.database.dao.WaveformCacheDao
import com.lochanpthakke.empee3.core.database.entity.AlbumArtistCrossRefEntity
import com.lochanpthakke.empee3.core.database.entity.AlbumEntity
import com.lochanpthakke.empee3.core.database.entity.ArtistAliasEntity
import com.lochanpthakke.empee3.core.database.entity.ArtistArtworkEntity
import com.lochanpthakke.empee3.core.database.entity.ArtistEntity
import com.lochanpthakke.empee3.core.database.entity.LearnedWatermarkEntity
import com.lochanpthakke.empee3.core.database.entity.ListeningDayEntity
import com.lochanpthakke.empee3.core.database.entity.ListeningStatsEntity
import com.lochanpthakke.empee3.core.database.entity.MetadataProcessingEntity
import com.lochanpthakke.empee3.core.database.entity.PlaylistEntity
import com.lochanpthakke.empee3.core.database.entity.PlaylistSongCrossRefEntity
import com.lochanpthakke.empee3.core.database.entity.RecentlyPlayedEntity
import com.lochanpthakke.empee3.core.database.entity.SongArtistCrossRefEntity
import com.lochanpthakke.empee3.core.database.entity.SongEntity
import com.lochanpthakke.empee3.core.database.entity.TrackBookmarkEntity
import com.lochanpthakke.empee3.core.database.entity.UnverifiedCreditSegmentEntity
import com.lochanpthakke.empee3.core.database.entity.WaveformCacheEntity

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
        ListeningStatsEntity::class,
        ListeningDayEntity::class,
        TrackBookmarkEntity::class,
        WaveformCacheEntity::class,
        LearnedWatermarkEntity::class,
    ],
    version = 13,
    exportSchema = true,
)
abstract class EmPee3Database : RoomDatabase() {
    abstract fun songDao(): SongDao

    abstract fun trackBookmarkDao(): TrackBookmarkDao

    abstract fun waveformCacheDao(): WaveformCacheDao

    abstract fun albumDao(): AlbumDao

    abstract fun artistDao(): ArtistDao

    abstract fun playlistDao(): PlaylistDao

    abstract fun metadataProcessingDao(): MetadataProcessingDao

    abstract fun learnedWatermarkDao(): LearnedWatermarkDao

    abstract fun songArtistCrossRefDao(): SongArtistCrossRefDao

    abstract fun libraryModelDao(): LibraryModelDao

    abstract fun recentlyPlayedDao(): RecentlyPlayedDao

    abstract fun listeningStatsDao(): ListeningStatsDao
}
