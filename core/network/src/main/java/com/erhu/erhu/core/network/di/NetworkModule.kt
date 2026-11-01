@file:Suppress("detekt:All")

package com.erhu.erhu.core.network.di

import com.erhu.erhu.core.network.api.AcousticBrainzApi
import com.erhu.erhu.core.network.api.DeezerApi
import com.erhu.erhu.core.network.api.LastFmApi
import com.erhu.erhu.core.network.api.LrclibApi
import com.erhu.erhu.core.network.api.MusicBrainzApi
import com.erhu.erhu.core.network.apple.AppleMusicCatalogApi
import com.erhu.erhu.core.network.apple.AppleWebClient
import com.erhu.erhu.core.network.apple.ItunesSearchApi
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
            .dns(ResilientDns())
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    @AppleWebClient
    fun provideAppleWebClient(baseClient: OkHttpClient): OkHttpClient =
        baseClient
            .newBuilder()
            .addInterceptor { chain ->
                val request =
                    chain
                        .request()
                        .newBuilder()
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .header("Origin", "https://music.apple.com")
                        .build()
                chain.proceed(request)
            }.build()

    @Provides
    @Singleton
    fun provideItunesSearchApi(
        @AppleWebClient okHttpClient: OkHttpClient,
        json: Json,
    ): ItunesSearchApi {
        val contentType = "application/json".toMediaType()
        return Retrofit
            .Builder()
            .baseUrl("https://itunes.apple.com/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(ItunesSearchApi::class.java)
    }

    @Provides
    @Singleton
    fun provideAppleMusicCatalogApi(
        @AppleWebClient okHttpClient: OkHttpClient,
        json: Json,
    ): AppleMusicCatalogApi {
        val contentType = "application/json".toMediaType()
        return Retrofit
            .Builder()
            .baseUrl("https://amp-api.music.apple.com/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(AppleMusicCatalogApi::class.java)
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
    ): com.erhu.erhu.core.network.api.GitHubUpdateApi {
        val contentType = "application/json".toMediaType()
        return Retrofit
            .Builder()
            .baseUrl("https://api.github.com/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(com.erhu.erhu.core.network.api.GitHubUpdateApi::class.java)
    }

    @Provides
    @Singleton
    fun provideITunesApi(
        okHttpClient: OkHttpClient,
        json: Json,
    ): com.erhu.erhu.core.network.api.ITunesApi {
        val contentType = "application/json".toMediaType()
        return Retrofit
            .Builder()
            .baseUrl("https://itunes.apple.com/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(com.erhu.erhu.core.network.api.ITunesApi::class.java)
    }

    @Provides
    @Singleton
    fun provideWikipediaApi(
        okHttpClient: OkHttpClient,
        json: Json,
    ): com.erhu.erhu.core.network.api.WikipediaApi {
        val contentType = "application/json".toMediaType()
        return Retrofit
            .Builder()
            .baseUrl("https://en.wikipedia.org/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(com.erhu.erhu.core.network.api.WikipediaApi::class.java)
    }

    @Provides
    @Singleton
    fun provideDiscogsApi(
        okHttpClient: OkHttpClient,
        json: Json,
    ): com.erhu.erhu.core.network.api.DiscogsApi {
        val contentType = "application/json".toMediaType()
        return Retrofit
            .Builder()
            .baseUrl("https://api.discogs.com/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(com.erhu.erhu.core.network.api.DiscogsApi::class.java)
    }

    @Provides
    @Singleton
    fun provideWikidataApi(
        okHttpClient: OkHttpClient,
        json: Json,
    ): com.erhu.erhu.core.network.api.WikidataApi {
        val contentType = "application/json".toMediaType()
        return Retrofit
            .Builder()
            .baseUrl("https://www.wikidata.org/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(com.erhu.erhu.core.network.api.WikidataApi::class.java)
    }

    @Provides
    @Singleton
    fun provideGeniusApi(
        okHttpClient: OkHttpClient,
        json: Json,
    ): com.erhu.erhu.core.network.api.GeniusApi {
        val contentType = "application/json".toMediaType()
        return Retrofit
            .Builder()
            .baseUrl("https://genius.com/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(com.erhu.erhu.core.network.api.GeniusApi::class.java)
    }

    @Provides
    @Singleton
    fun provideDuckDuckGoApi(
        okHttpClient: OkHttpClient,
        json: Json,
    ): com.erhu.erhu.core.network.api.DuckDuckGoApi {
        val contentType = "application/json".toMediaType()
        return Retrofit
            .Builder()
            .baseUrl("https://duckduckgo.com/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(com.erhu.erhu.core.network.api.DuckDuckGoApi::class.java)
    }

    @Provides
    @Singleton
    fun provideFanartTvApi(
        okHttpClient: OkHttpClient,
        json: Json,
    ): com.erhu.erhu.core.network.api.FanartTvApi {
        val contentType = "application/json".toMediaType()
        return Retrofit
            .Builder()
            .baseUrl("https://webservice.fanart.tv/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(com.erhu.erhu.core.network.api.FanartTvApi::class.java)
    }
}

private val SECRET_QUERY = Regex("""(?i)\b(api_key|apikey|token|secret)=[^&\s]*""")

/** [message] with the values of key-like query parameters replaced. */
internal fun redactSecrets(message: String): String = message.replace(SECRET_QUERY) { "${it.groupValues[1]}=<redacted>" }

class ResilientDns : okhttp3.Dns {
    override fun lookup(hostname: String): List<java.net.InetAddress> {
        return try {
            okhttp3.Dns.SYSTEM.lookup(hostname)
        } catch (e: java.net.UnknownHostException) {
            val fallbackIps =
                mapOf(
                    "musicbrainz.org" to listOf("138.201.225.10", "138.201.225.11"),
                    "api.deezer.com" to listOf("188.165.234.195"),
                    "ws.audioscrobbler.com" to listOf("151.101.1.193", "151.101.65.193"),
                    "lrclib.net" to listOf("172.67.181.189"),
                )
            val ips = fallbackIps[hostname.lowercase()]
            if (!ips.isNullOrEmpty()) {
                val resolved =
                    ips.mapNotNull { ip ->
                        try {
                            java.net.InetAddress.getByAddress(
                                hostname,
                                java.net.InetAddress
                                    .getByName(ip)
                                    .address,
                            )
                        } catch (_: Exception) {
                            null
                        }
                    }
                if (resolved.isNotEmpty()) return resolved
            }
            throw e
        }
    }
}
