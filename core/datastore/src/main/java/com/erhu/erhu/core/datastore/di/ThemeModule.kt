package com.erhu.erhu.core.datastore.di

import com.erhu.erhu.core.datastore.repository.ThemeRepository
import com.erhu.erhu.core.datastore.repository.ThemeRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface ThemeModule {
    @Binds
    @Singleton
    fun bindThemeRepository(impl: ThemeRepositoryImpl): ThemeRepository
}
