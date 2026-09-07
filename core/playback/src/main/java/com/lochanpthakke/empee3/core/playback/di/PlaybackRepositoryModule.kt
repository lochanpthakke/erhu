package com.lochanpthakke.empee3.core.playback.di

import com.lochanpthakke.empee3.core.playback.repository.PlaybackRepository
import com.lochanpthakke.empee3.core.playback.repository.PlaybackRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds [PlaybackRepository] to its implementation. Singleton-scoped to
 * match PlaybackController's own lifecycle — PlaybackRepository is a thin
 * wrapper with no state of its own, but sharing scope avoids redundant
 * instantiation across every ViewModel that injects it.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PlaybackRepositoryModule {
    @Binds
    @Singleton
    abstract fun bindPlaybackRepository(impl: PlaybackRepositoryImpl): PlaybackRepository
}
