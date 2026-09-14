package com.lochanpthakke.empee3.core.sound.di

import com.lochanpthakke.empee3.core.sound.repository.SoundRepository
import com.lochanpthakke.empee3.core.sound.repository.SoundRepositoryImpl
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
