@file:Suppress("detekt:All")

package com.lochanpthakke.empee3.core.network.di

import com.lochanpthakke.empee3.core.network.api.AcousticBrainzApi
import com.lochanpthakke.empee3.core.network.api.DeezerApi
import com.lochanpthakke.empee3.core.network.api.LastFmApi
import com.lochanpthakke.empee3.core.network.api.LrclibApi
import com.lochanpthakke.empee3.core.network.api.MusicBrainzApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideJson(): Json =
        Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        // Request lines carry query strings: keep API keys out of logcat.
        val logging =
            HttpLoggingInterceptor { message -> HttpLoggingInterceptor.Logger.DEFAULT.log(redactSecrets(message)) }.apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
        return OkHttpClient
            .Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    fun provideMusicBrainzApi(
        okHttpClient: OkHttpClient,
        json: Json,
    ): MusicBrainzApi {
        val contentType = "application/json".toMediaType()
        return Retrofit
            .Builder()
            .baseUrl("https://musicbrainz.org/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(MusicBrainzApi::class.java)
    }

    @Provides
    @Singleton
    fun provideAcousticBrainzApi(
        okHttpClient: OkHttpClient,
        json: Json,
    ): AcousticBrainzApi {
        val contentType = "application/json".toMediaType()
        return Retrofit
            .Builder()
            .baseUrl("https://acousticbrainz.org/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(AcousticBrainzApi::class.java)
    }

    @Provides
    @Singleton
    fun provideDeezerApi(
        okHttpClient: OkHttpClient,
        json: Json,
    ): DeezerApi {
        val contentType = "application/json".toMediaType()
        return Retrofit
            .Builder()
            .baseUrl("https://api.deezer.com/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(DeezerApi::class.java)
    }

    @Provides
    @Singleton
    fun provideLastFmApi(
        okHttpClient: OkHttpClient,
        json: Json,
    ): LastFmApi {
        val contentType = "application/json".toMediaType()
        return Retrofit
            .Builder()
            .baseUrl("https://ws.audioscrobbler.com/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(LastFmApi::class.java)
    }

    @Provides
    @Singleton
    fun provideLrclibApi(
        okHttpClient: OkHttpClient,
        json: Json,
    ): LrclibApi {
        val contentType = "application/json".toMediaType()
        return Retrofit
            .Builder()
            .baseUrl("https://lrclib.net/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(LrclibApi::class.java)
    }

    @Provides
    @Singleton
    fun provideGitHubUpdateApi(
        okHttpClient: OkHttpClient,
        json: Json,
    ): com.lochanpthakke.empee3.core.network.api.GitHubUpdateApi {
        val contentType = "application/json".toMediaType()
        return Retrofit
            .Builder()
            .baseUrl("https://api.github.com/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(com.lochanpthakke.empee3.core.network.api.GitHubUpdateApi::class.java)
    }
}

private val SECRET_QUERY = Regex("""(?i)\b(api_key|apikey|token|secret)=[^&\s]*""")

/** [message] with the values of key-like query parameters replaced. */
internal fun redactSecrets(message: String): String = message.replace(SECRET_QUERY) { "${it.groupValues[1]}=<redacted>" }
