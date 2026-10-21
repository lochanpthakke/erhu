package com.lochanpthakke.empee3.core.network.di

import com.lochanpthakke.empee3.core.network.apple.AnimatedArtworkNetworkDataSource
import com.lochanpthakke.empee3.core.network.apple.AnimatedArtworkNetworkDataSourceImpl
import com.lochanpthakke.empee3.core.network.apple.NoOpAnimatedArtworkNetworkDataSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import dagger.Provides

@Module
@InstallIn(SingletonComponent::class)
object AppleNetworkModule {

    @Provides
    @Singleton
    fun provideAnimatedArtworkNetworkDataSource(
        impl: AnimatedArtworkNetworkDataSourceImpl
    ): AnimatedArtworkNetworkDataSource {
        // Assume default enabled for now until BuildConfig logic is set up in app module
        return impl
    }
}
