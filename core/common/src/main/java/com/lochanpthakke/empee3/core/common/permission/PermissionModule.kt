package com.lochanpthakke.empee3.core.common.permission

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class PermissionModule {
    @Binds
    abstract fun bindPermissionManager(impl: AndroidPermissionManager): PermissionManager
}
