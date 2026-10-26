package com.lochanpthakke.empee3.core.network.di

import com.lochanpthakke.empee3.core.network.apple.AnimatedArtworkNetworkDataSource
import com.lochanpthakke.empee3.core.network.apple.AnimatedArtworkNetworkDataSourceImpl
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
