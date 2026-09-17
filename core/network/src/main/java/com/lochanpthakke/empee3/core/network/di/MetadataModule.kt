@file:Suppress("detekt:All")

package com.lochanpthakke.empee3.core.network.di

import com.lochanpthakke.empee3.core.network.repository.MetadataRepository
import com.lochanpthakke.empee3.core.network.repository.MetadataRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface MetadataModule {
    @Binds
    @Singleton
    fun bindMetadataRepository(impl: MetadataRepositoryImpl): MetadataRepository
}
