package com.erhu.erhu.core.database.di

import android.content.Context
import androidx.room.Room
import com.erhu.erhu.core.database.ErhuDatabase
import com.erhu.erhu.core.database.MIGRATION_10_11
import com.erhu.erhu.core.database.MIGRATION_11_12
import com.erhu.erhu.core.database.MIGRATION_12_13
import com.erhu.erhu.core.database.MIGRATION_13_14
import com.erhu.erhu.core.database.MIGRATION_5_6
import com.erhu.erhu.core.database.MIGRATION_6_7
import com.erhu.erhu.core.database.MIGRATION_7_8
import com.erhu.erhu.core.database.MIGRATION_8_9
import com.erhu.erhu.core.database.MIGRATION_9_10
import com.erhu.erhu.core.database.dao.AlbumDao
import com.erhu.erhu.core.database.dao.AnimatedArtworkDao
import com.erhu.erhu.core.database.dao.ArtistDao
import com.erhu.erhu.core.database.dao.LearnedWatermarkDao
import com.erhu.erhu.core.database.dao.LibraryModelDao
import com.erhu.erhu.core.database.dao.ListeningStatsDao
import com.erhu.erhu.core.database.dao.MetadataProcessingDao
import com.erhu.erhu.core.database.dao.PlaylistDao
import com.erhu.erhu.core.database.dao.RecentlyPlayedDao
import com.erhu.erhu.core.database.dao.SongDao
import com.erhu.erhu.core.database.dao.TrackBookmarkDao
import com.erhu.erhu.core.database.dao.WaveformCacheDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    @Suppress("MagicNumber") // schema version numbers
    fun provideErhuDatabase(
        @ApplicationContext context: Context,
    ): ErhuDatabase =
        Room
            .databaseBuilder(
                context,
                ErhuDatabase::class.java,
                "empee3.db",
            ).addMigrations(
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9,
                MIGRATION_9_10,
                MIGRATION_10_11,
                MIGRATION_11_12,
                MIGRATION_12_13,
                MIGRATION_13_14,
            ).fallbackToDestructiveMigrationFrom(true, 1, 2, 3, 4)
            .fallbackToDestructiveMigrationOnDowngrade(true)
            .build()

    @Provides
    fun provideSongDao(database: ErhuDatabase): SongDao = database.songDao()

    @Provides
    fun provideAlbumDao(database: ErhuDatabase): AlbumDao = database.albumDao()

    @Provides
    fun provideArtistDao(database: ErhuDatabase): ArtistDao = database.artistDao()

    @Provides
    fun providePlaylistDao(database: ErhuDatabase): PlaylistDao = database.playlistDao()

    @Provides
    fun provideLibraryModelDao(database: ErhuDatabase): LibraryModelDao = database.libraryModelDao()

    @Provides
    fun provideRecentlyPlayedDao(database: ErhuDatabase): RecentlyPlayedDao = database.recentlyPlayedDao()

    @Provides
    fun provideListeningStatsDao(database: ErhuDatabase): ListeningStatsDao = database.listeningStatsDao()

    @Provides
    fun provideTrackBookmarkDao(database: ErhuDatabase): TrackBookmarkDao = database.trackBookmarkDao()

    @Provides
    fun provideWaveformCacheDao(database: ErhuDatabase): WaveformCacheDao = database.waveformCacheDao()

    @Provides
    fun provideMetadataProcessingDao(database: ErhuDatabase): MetadataProcessingDao = database.metadataProcessingDao()

    @Provides
    fun provideLearnedWatermarkDao(database: ErhuDatabase): LearnedWatermarkDao = database.learnedWatermarkDao()

    @Provides
    fun provideAnimatedArtworkDao(database: ErhuDatabase): AnimatedArtworkDao = database.animatedArtworkDao()
}
