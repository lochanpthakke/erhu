package com.erhu.erhu.core.sound.di

import com.erhu.erhu.core.sound.repository.SoundRepository
import com.erhu.erhu.core.sound.repository.SoundRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface SoundModule {
    @Binds
    @Singleton
    fun bindSoundRepository(impl: SoundRepositoryImpl): SoundRepository
}
