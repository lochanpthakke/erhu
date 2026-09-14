package com.lochanpthakke.empee3.core.database.di

import android.content.Context
import androidx.room.Room
import com.lochanpthakke.empee3.core.database.EmPee3Database
import com.lochanpthakke.empee3.core.database.dao.AlbumDao
import com.lochanpthakke.empee3.core.database.dao.ArtistDao
import com.lochanpthakke.empee3.core.database.dao.PlaylistDao
import com.lochanpthakke.empee3.core.database.dao.SongDao
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
    fun provideEmPee3Database(
        @ApplicationContext context: Context,
    ): EmPee3Database =
        Room
            .databaseBuilder(
                context,
                EmPee3Database::class.java,
                "empee3.db",
            ).build()

    @Provides
    fun provideSongDao(database: EmPee3Database): SongDao = database.songDao()

    @Provides
    fun provideAlbumDao(database: EmPee3Database): AlbumDao = database.albumDao()

    @Provides
    fun provideArtistDao(database: EmPee3Database): ArtistDao = database.artistDao()

    @Provides
    fun providePlaylistDao(database: EmPee3Database): PlaylistDao = database.playlistDao()
}
