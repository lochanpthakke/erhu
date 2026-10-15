package com.lochanpthakke.empee3.core.database.di

import android.content.Context
import androidx.room.Room
import com.lochanpthakke.empee3.core.database.EmPee3Database
import com.lochanpthakke.empee3.core.database.MIGRATION_10_11
import com.lochanpthakke.empee3.core.database.MIGRATION_11_12
import com.lochanpthakke.empee3.core.database.MIGRATION_12_13
import com.lochanpthakke.empee3.core.database.MIGRATION_5_6
import com.lochanpthakke.empee3.core.database.MIGRATION_6_7
import com.lochanpthakke.empee3.core.database.MIGRATION_7_8
import com.lochanpthakke.empee3.core.database.MIGRATION_8_9
import com.lochanpthakke.empee3.core.database.MIGRATION_9_10
import com.lochanpthakke.empee3.core.database.dao.AlbumDao
import com.lochanpthakke.empee3.core.database.dao.ArtistDao
import com.lochanpthakke.empee3.core.database.dao.LearnedWatermarkDao
import com.lochanpthakke.empee3.core.database.dao.LibraryModelDao
import com.lochanpthakke.empee3.core.database.dao.ListeningStatsDao
import com.lochanpthakke.empee3.core.database.dao.MetadataProcessingDao
import com.lochanpthakke.empee3.core.database.dao.PlaylistDao
import com.lochanpthakke.empee3.core.database.dao.RecentlyPlayedDao
import com.lochanpthakke.empee3.core.database.dao.SongDao
import com.lochanpthakke.empee3.core.database.dao.TrackBookmarkDao
import com.lochanpthakke.empee3.core.database.dao.WaveformCacheDao
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
    fun provideEmPee3Database(
        @ApplicationContext context: Context,
    ): EmPee3Database =
        Room
            .databaseBuilder(
                context,
                EmPee3Database::class.java,
                "empee3.db",
            ).addMigrations(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13)
            .fallbackToDestructiveMigrationFrom(true, 1, 2, 3, 4)
            .fallbackToDestructiveMigrationOnDowngrade(true)
            .build()

    @Provides
    fun provideSongDao(database: EmPee3Database): SongDao = database.songDao()

    @Provides
    fun provideAlbumDao(database: EmPee3Database): AlbumDao = database.albumDao()

    @Provides
    fun provideArtistDao(database: EmPee3Database): ArtistDao = database.artistDao()

    @Provides
    fun providePlaylistDao(database: EmPee3Database): PlaylistDao = database.playlistDao()

    @Provides
    fun provideLibraryModelDao(database: EmPee3Database): LibraryModelDao = database.libraryModelDao()

    @Provides
    fun provideRecentlyPlayedDao(database: EmPee3Database): RecentlyPlayedDao = database.recentlyPlayedDao()

    @Provides
    fun provideListeningStatsDao(database: EmPee3Database): ListeningStatsDao = database.listeningStatsDao()

    @Provides
    fun provideTrackBookmarkDao(database: EmPee3Database): TrackBookmarkDao = database.trackBookmarkDao()

    @Provides
    fun provideWaveformCacheDao(database: EmPee3Database): WaveformCacheDao = database.waveformCacheDao()

    @Provides
    fun provideMetadataProcessingDao(database: EmPee3Database): MetadataProcessingDao = database.metadataProcessingDao()

    @Provides
    fun provideLearnedWatermarkDao(database: EmPee3Database): LearnedWatermarkDao = database.learnedWatermarkDao()
}
