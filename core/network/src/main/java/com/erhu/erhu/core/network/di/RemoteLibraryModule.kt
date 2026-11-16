package com.erhu.erhu.core.network.di

import com.erhu.erhu.core.model.RemoteArtworkSource
import com.erhu.erhu.core.model.RemoteStreamResolver
import com.erhu.erhu.core.network.remote.subsonic.SubsonicArtworkSource
import com.erhu.erhu.core.network.remote.subsonic.SubsonicClient
import com.erhu.erhu.core.network.remote.subsonic.SubsonicStreamResolver
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RemoteLibraryModule {
    @Binds
    @IntoSet
    abstract fun subsonicStreams(impl: SubsonicStreamResolver): RemoteStreamResolver

    @Binds
    @IntoSet
    abstract fun subsonicArtwork(impl: SubsonicArtworkSource): RemoteArtworkSource

    @Binds
    @IntoSet
    abstract fun driveStreams(impl: com.erhu.erhu.core.network.remote.drive.DriveStreamResolver): RemoteStreamResolver

    @Binds
    @IntoSet
    abstract fun driveArtwork(impl: com.erhu.erhu.core.network.remote.drive.DriveArtworkSource): RemoteArtworkSource

    companion object {
        /** No request logging here either: Drive requests carry the access token in a header. */
        @Provides
        @Singleton
        fun provideDriveClient(base: OkHttpClient): com.erhu.erhu.core.network.remote.drive.DriveClient =
            com.erhu.erhu.core.network.remote.drive.DriveClient(
                base
                    .newBuilder()
                    .apply { interceptors().removeAll { it is HttpLoggingInterceptor } }
                    .followSslRedirects(false)
                    .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .build(),
            )

        /**
         * The app's client without its logging interceptor: Subsonic request URLs carry the user
         * name and token. HTTPS is never allowed to redirect down to HTTP.
         */
        @Provides
        @Singleton
        fun provideSubsonicClient(base: OkHttpClient): SubsonicClient =
            SubsonicClient(
                base
                    .newBuilder()
                    .apply { interceptors().removeAll { it is HttpLoggingInterceptor } }
                    .followSslRedirects(false)
                    .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .build(),
            )

        private const val READ_TIMEOUT_SECONDS = 30L
    }
}
