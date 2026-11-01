package com.erhu.erhu.core.network.di

import com.erhu.erhu.core.network.apple.AnimatedArtworkNetworkDataSource
import com.erhu.erhu.core.network.apple.AnimatedArtworkNetworkDataSourceImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppleNetworkModule {
    @Provides
    @Singleton
    fun provideAnimatedArtworkNetworkDataSource(impl: AnimatedArtworkNetworkDataSourceImpl): AnimatedArtworkNetworkDataSource {
        // Assume default enabled for now until BuildConfig logic is set up in app module
        return impl
    }
}
