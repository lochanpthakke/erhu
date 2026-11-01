@file:Suppress("detekt:All")

package com.erhu.erhu.core.network.di

import com.erhu.erhu.core.network.repository.MetadataRepository
import com.erhu.erhu.core.network.repository.MetadataRepositoryImpl
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

    @Binds
    @Singleton
    fun bindNetworkStatusSource(
        impl: com.erhu.erhu.core.network.connectivity.AndroidNetworkStatusMonitor,
    ): com.erhu.erhu.core.network.connectivity.NetworkStatusSource

    @Binds
    @Singleton
    fun bindUpdateCheckerRepository(
        impl: com.erhu.erhu.core.network.repository.UpdateCheckerRepositoryImpl,
    ): com.erhu.erhu.core.network.repository.UpdateCheckerRepository
}
