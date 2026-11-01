package com.erhu.erhu.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.erhu.erhu.core.database.dao.AlbumDao
import com.erhu.erhu.core.database.dao.AnimatedArtworkDao
import com.erhu.erhu.core.database.dao.ArtistDao
import com.erhu.erhu.core.database.dao.LearnedWatermarkDao
import com.erhu.erhu.core.database.dao.LibraryModelDao
import com.erhu.erhu.core.database.dao.ListeningStatsDao
import com.erhu.erhu.core.database.dao.MetadataProcessingDao
import com.erhu.erhu.core.database.dao.PlaylistDao
import com.erhu.erhu.core.database.dao.RecentlyPlayedDao
import com.erhu.erhu.core.database.dao.SongArtistCrossRefDao
import com.erhu.erhu.core.database.dao.SongDao
import com.erhu.erhu.core.database.dao.TrackBookmarkDao
import com.erhu.erhu.core.database.dao.WaveformCacheDao
import com.erhu.erhu.core.database.entity.AlbumArtistCrossRefEntity
import com.erhu.erhu.core.database.entity.AlbumEntity
import com.erhu.erhu.core.database.entity.AnimatedArtworkEntity
import com.erhu.erhu.core.database.entity.ArtistAliasEntity
import com.erhu.erhu.core.database.entity.ArtistArtworkEntity
import com.erhu.erhu.core.database.entity.ArtistEntity
import com.erhu.erhu.core.database.entity.LearnedWatermarkEntity
import com.erhu.erhu.core.database.entity.ListeningDayEntity
import com.erhu.erhu.core.database.entity.ListeningStatsEntity
import com.erhu.erhu.core.database.entity.MetadataProcessingEntity
import com.erhu.erhu.core.database.entity.PlaylistEntity
import com.erhu.erhu.core.database.entity.PlaylistSongCrossRefEntity
import com.erhu.erhu.core.database.entity.RecentlyPlayedEntity
import com.erhu.erhu.core.database.entity.SongArtistCrossRefEntity
import com.erhu.erhu.core.database.entity.SongEntity
import com.erhu.erhu.core.database.entity.TrackBookmarkEntity
import com.erhu.erhu.core.database.entity.UnverifiedCreditSegmentEntity
import com.erhu.erhu.core.database.entity.WaveformCacheEntity

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
        AnimatedArtworkEntity::class,
    ],
    version = 14,
    exportSchema = true,
)
abstract class ErhuDatabase : RoomDatabase() {
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

    abstract fun animatedArtworkDao(): AnimatedArtworkDao
}
