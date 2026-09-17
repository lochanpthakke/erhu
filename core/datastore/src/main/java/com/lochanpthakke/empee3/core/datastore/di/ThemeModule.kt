package com.lochanpthakke.empee3.core.datastore.di

import com.lochanpthakke.empee3.core.datastore.repository.ThemeRepository
import com.lochanpthakke.empee3.core.datastore.repository.ThemeRepositoryImpl
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
