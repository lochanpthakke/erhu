package com.erhu.erhu.core.playback

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import com.erhu.erhu.core.model.RemoteAccessException
import com.erhu.erhu.core.model.RemoteRef
import com.erhu.erhu.core.model.RemoteStreamResolver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Media sources for every erhu player (main, transition tail, transition preview). Device files
 * open exactly as before. An `erhu-remote://` entry is turned into its real request by the
 * provider's [RemoteStreamResolver] each time the stream is opened, so the address, credentials
 * and tokens exist only inside that one request: never in the queue, the saved state or the
 * media session.
 */
@Singleton
@OptIn(UnstableApi::class)
class RemoteMediaSources
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        resolvers: Set<@JvmSuppressWildcards RemoteStreamResolver>,
        artworkSources: Set<@JvmSuppressWildcards com.erhu.erhu.core.model.RemoteArtworkSource>,
    ) {
        private val byProvider = resolvers.associateBy { it.provider }

        init {
            // This object exists as soon as the player does, so remote covers work everywhere artwork is loaded
            // (lists, Now Playing, the notification) without each caller knowing about providers.
            val artwork = artworkSources.associateBy { it.provider }
            com.erhu.erhu.core.common.artwork.ArtworkLoader.remoteArtworkFetcher =
                com.erhu.erhu.core.common.artwork.ArtworkLoader.RemoteArtworkFetcher { uri, sizePx ->
                    val ref = RemoteRef.parseArtwork(uri) ?: return@RemoteArtworkFetcher null
                    artwork[ref.provider]?.fetch(ref, sizePx)
                }
        }

        fun dataSourceFactory(): DataSource.Factory =
            ResolvingDataSource.Factory(
                DefaultDataSource.Factory(
                    context,
                    DefaultHttpDataSource
                        .Factory()
                        .setConnectTimeoutMs(CONNECT_TIMEOUT_MS)
                        .setReadTimeoutMs(READ_TIMEOUT_MS),
                ),
                ::resolve,
            )

        fun mediaSourceFactory(): MediaSource.Factory = DefaultMediaSourceFactory(context).setDataSourceFactory(dataSourceFactory())

        private fun resolve(dataSpec: DataSpec): DataSpec {
            val ref = RemoteRef.parseStream(dataSpec.uri.toString()) ?: return dataSpec
            val resolver =
                byProvider[ref.provider]
                    ?: throw RemoteAccessException(RemoteAccessException.Reason.SOURCE_MISSING, "No remote library for this track")
            val stream = resolver.resolve(ref)
            return dataSpec
                .buildUpon()
                .setUri(Uri.parse(stream.url))
                .setHttpRequestHeaders(stream.headers)
                .build()
        }

        private companion object {
            const val CONNECT_TIMEOUT_MS = 15_000
            const val READ_TIMEOUT_MS = 20_000
        }
    }
