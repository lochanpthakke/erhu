package com.erhu.erhu.core.network.di

import android.content.Context
import com.erhu.erhu.core.common.dispatcher.DispatcherProvider
import com.erhu.erhu.core.network.BuildConfig
import com.erhu.erhu.core.network.api.MusixmatchApi
import com.erhu.erhu.core.network.lyrics.FileOnlineLyricsCache
import com.erhu.erhu.core.network.lyrics.OnlineLyricsRepository
import com.erhu.erhu.core.network.provider.LrclibProvider
import com.erhu.erhu.core.network.provider.MusixmatchProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LyricsModule {
    @Provides
    @Singleton
    fun provideMusixmatch(
        client: OkHttpClient,
        json: Json,
    ): MusixmatchProvider {
        val api =
            Retrofit
                .Builder()
                .baseUrl("https://api.musixmatch.com/ws/1.1/")
                .client(client.newBuilder().callTimeout(12, TimeUnit.SECONDS).build())
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(MusixmatchApi::class.java)
        return MusixmatchProvider(api, BuildConfig.MUSIXMATCH_API_KEY)
    }

    /** Existing downloads stay separate from evictable automatic response caching. */
    @Provides
    @Singleton
    fun provideOnlineLyricsRepository(
        lrclib: LrclibProvider,
        musixmatch: MusixmatchProvider,
        @ApplicationContext context: Context,
        cache: FileOnlineLyricsCache,
        dispatchers: DispatcherProvider,
    ): OnlineLyricsRepository =
        OnlineLyricsRepository(
            listOf(lrclib, musixmatch),
            cache,
            dispatchers,
            responseCache = FileOnlineLyricsCache(File(context.cacheDir, "online-lyrics")),
        )
}
