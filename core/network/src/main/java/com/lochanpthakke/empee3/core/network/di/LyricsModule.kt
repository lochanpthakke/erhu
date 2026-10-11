package com.lochanpthakke.empee3.core.network.di

import com.lochanpthakke.empee3.core.common.dispatcher.DispatcherProvider
import com.lochanpthakke.empee3.core.network.lyrics.FileOnlineLyricsCache
import com.lochanpthakke.empee3.core.network.lyrics.OnlineLyricsRepository
import com.lochanpthakke.empee3.core.network.provider.LrclibProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LyricsModule {
    /** Online lyrics: LRCLIB for now; further legitimate providers are added to this list. */
    @Provides
    @Singleton
    fun provideOnlineLyricsRepository(
        lrclib: LrclibProvider,
        cache: FileOnlineLyricsCache,
        dispatchers: DispatcherProvider,
    ): OnlineLyricsRepository = OnlineLyricsRepository(listOf(lrclib), cache, dispatchers)
}
