@file:Suppress("detekt:All")

package com.erhu.erhu.core.network.di

import com.erhu.erhu.core.network.api.AcousticBrainzApi
import com.erhu.erhu.core.network.api.DeezerApi
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
    fun provideAlbumArtworkFinder(
        @dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context,
        client: OkHttpClient,
        json: Json,
        images: com.erhu.erhu.core.network.artwork.AndroidArtworkImageInspector,
        musicBrainz: com.erhu.erhu.core.network.provider.MusicBrainzProvider,
    ): com.erhu.erhu.core.network.artwork.AlbumArtworkFinder =
        com.erhu.erhu.core.network.artwork.AlbumArtworkFinder(
            client,
            json,
            com.erhu.erhu.core.network.artwork
                .AlbumArtworkCache(java.io.File(context.filesDir, "album-artwork-v1")),
            images,
            audioDbKey =
                com.erhu.erhu.core.network.BuildConfig.THEAUDIODB_API_KEY.ifBlank {
                    if (com.erhu.erhu.core.network.BuildConfig.DEBUG) "123" else ""
                },
            musicBrainzRequest = { request -> musicBrainz.throttled(request) },
        )

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
            .client(okHttpClient.newBuilder().callTimeout(20, TimeUnit.SECONDS).build())
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
}

private val SECRET_QUERY = Regex("""(?i)\b(api_key|apikey|token|secret)=[^&\s]*""")

/** [message] with the values of key-like query parameters replaced. */
internal fun redactSecrets(message: String): String =
    message
        .replace(SECRET_QUERY) { "${it.groupValues[1]}=<redacted>" }
        .replace(Regex("(?i)(/api/v1/json/)[^/\\s?]+")) { "${it.groupValues[1]}<redacted>" }

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
