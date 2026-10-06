@file:Suppress("detekt:All")

package com.lochanpthakke.empee3.core.database.di

import android.content.Context
import androidx.work.WorkManager
import com.lochanpthakke.empee3.core.database.lookup.RoomSongLookup
import com.lochanpthakke.empee3.core.database.repository.ArtistArtworkRepository
import com.lochanpthakke.empee3.core.database.repository.ArtistArtworkRepositoryImpl
import com.lochanpthakke.empee3.core.database.repository.ArtistArtworkStore
import com.lochanpthakke.empee3.core.database.repository.LibraryRepository
import com.lochanpthakke.empee3.core.database.repository.LibraryRepositoryImpl
import com.lochanpthakke.empee3.core.database.repository.PlaylistArtworkStore
import com.lochanpthakke.empee3.core.database.repository.PlaylistRepository
import com.lochanpthakke.empee3.core.database.repository.PlaylistRepositoryImpl
import com.lochanpthakke.empee3.core.database.repository.TimeSource
import com.lochanpthakke.empee3.core.database.repository.ZoneSource
import com.lochanpthakke.empee3.core.model.SongLookup
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.time.ZoneId
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface RepositoryModule {
    @Binds
    @Singleton
    fun bindLibraryRepository(impl: LibraryRepositoryImpl): LibraryRepository

    @Binds
    @Singleton
    fun bindSongLookup(impl: RoomSongLookup): SongLookup

    @Binds
    @Singleton
    fun bindPlaylistRepository(impl: PlaylistRepositoryImpl): PlaylistRepository

    @Binds
    @Singleton
    fun bindArtistArtworkRepository(impl: ArtistArtworkRepositoryImpl): ArtistArtworkRepository

    companion object {
        @Provides
        @Singleton
        fun provideTimeSource(): TimeSource = TimeSource(System::currentTimeMillis)

        @Provides
        @Singleton
        fun provideZoneSource(): ZoneSource = ZoneSource(ZoneId::systemDefault)

        @Provides
        @Singleton
        fun providePlaylistArtworkStore(
            @ApplicationContext context: Context,
        ): PlaylistArtworkStore = PlaylistArtworkStore(File(context.filesDir, "playlist_artwork"))

        @Provides
        @Singleton
        fun provideArtistArtworkStore(
            @ApplicationContext context: Context,
        ): ArtistArtworkStore = ArtistArtworkStore(File(context.filesDir, "artist_artwork"))

        @Provides
        @Singleton
        fun provideWorkManager(
            @ApplicationContext context: Context,
        ): WorkManager = WorkManager.getInstance(context)
    }
}
