package com.lochanpthakke.empee3.core.common.logging

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class LoggerModule {
    @Binds
    abstract fun bindLogger(impl: TimberLogger): Logger
}
