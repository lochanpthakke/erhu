@file:Suppress("detekt:All")

package com.erhu.erhu.core.database.di

import android.content.Context
import androidx.work.WorkManager
import com.erhu.erhu.core.database.lookup.RoomSongLookup
import com.erhu.erhu.core.database.repository.AnimatedArtworkRepository
import com.erhu.erhu.core.database.repository.AnimatedArtworkRepositoryImpl
import com.erhu.erhu.core.database.repository.ArtistArtworkRepository
import com.erhu.erhu.core.database.repository.ArtistArtworkRepositoryImpl
import com.erhu.erhu.core.database.repository.ArtistArtworkStore
import com.erhu.erhu.core.database.repository.LibraryRepository
import com.erhu.erhu.core.database.repository.LibraryRepositoryImpl
import com.erhu.erhu.core.database.repository.PlaylistArtworkStore
import com.erhu.erhu.core.database.repository.PlaylistRepository
import com.erhu.erhu.core.database.repository.PlaylistRepositoryImpl
import com.erhu.erhu.core.database.repository.TimeSource
import com.erhu.erhu.core.database.repository.ZoneSource
import com.erhu.erhu.core.model.SongLookup
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.time.ZoneId
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface RepositoryModule {
    @Binds
    @Singleton
    fun bindLibraryRepository(impl: LibraryRepositoryImpl): LibraryRepository

    @Binds
    @Singleton
    fun bindAnimatedArtworkRepository(impl: AnimatedArtworkRepositoryImpl): AnimatedArtworkRepository

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
        @Named("artistArtworkDir")
        fun provideArtistArtworkDir(
            @ApplicationContext context: Context,
        ): File = File(context.filesDir, "artist_artwork")

        @Provides
        @Singleton
        fun provideWorkManager(
            @ApplicationContext context: Context,
        ): WorkManager = WorkManager.getInstance(context)
    }
}
