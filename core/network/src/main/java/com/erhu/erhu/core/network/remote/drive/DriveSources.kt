package com.erhu.erhu.core.network.remote.drive

import android.content.Context
import android.media.MediaMetadataRetriever
import com.erhu.erhu.core.model.RemoteArtworkSource
import com.erhu.erhu.core.model.RemoteProvider
import com.erhu.erhu.core.model.RemoteRef
import com.erhu.erhu.core.model.RemoteStreamResolver
import com.erhu.erhu.core.model.ResolvedStream
import com.erhu.erhu.core.network.remote.subsonic.md5Hex
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Streams a Drive file with a current token in the Authorization header; the URL itself holds no secret. */
@Singleton
class DriveStreamResolver
    @Inject
    constructor(
        private val tokens: DriveTokenProvider,
        private val client: DriveClient,
    ) : RemoteStreamResolver {
        override val provider = RemoteProvider.GOOGLE_DRIVE

        override fun resolve(ref: RemoteRef): ResolvedStream =
            ResolvedStream(client.mediaUrl(ref.remoteId), mapOf("Authorization" to "Bearer ${tokens.token(ref.sourceId)}"))
    }

/** What a Drive file says about itself. Null fields were simply not in the file. */
class DriveTags(
    val title: String?,
    val artist: String?,
    val album: String?,
    val durationMs: Long,
)

/**
 * Reads a Drive file's own tags and embedded cover by asking the platform's metadata reader to
 * open the authorized stream. It fetches only the parts of the file it needs (byte ranges), never
 * the whole file, and never writes to Drive. One file at a time, so a long folder cannot flood the
 * connection. The cover is kept in the app's cache folder (an empty file means "has no cover").
 */
@Singleton
class DriveMetadataReader
    @Inject
    constructor(
        @ApplicationContext context: Context,
        private val tokens: DriveTokenProvider,
        private val client: DriveClient,
    ) {
        private val covers = File(context.cacheDir, "remote-art")

        /** Blocking. Null when the file could not be read (offline, unsupported, sign-in needed). */
        @Synchronized
        @Suppress("TooGenericExceptionCaught", "SwallowedException")
        fun read(
            sourceId: Long,
            fileId: String,
        ): DriveTags? {
            val retriever = MediaMetadataRetriever()
            return try {
                retriever.setDataSource(client.mediaUrl(fileId), mapOf("Authorization" to "Bearer ${tokens.token(sourceId)}"))
                val tags =
                    DriveTags(
                        title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
                        artist =
                            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
                        album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                        durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                    )
                covers.mkdirs()
                coverFile(sourceId, fileId).writeBytes(retriever.embeddedPicture ?: ByteArray(0))
                tags
            } catch (e: Exception) {
                null
            } finally {
                try {
                    retriever.release()
                } catch (e: Exception) {
                    // Nothing useful to do if release fails.
                }
            }
        }

        /** The cached cover; an empty array when the file is known to have none; null when not read yet. */
        fun cachedCover(
            sourceId: Long,
            fileId: String,
        ): ByteArray? = coverFile(sourceId, fileId).takeIf { it.isFile }?.readBytes()

        fun forget(sourceId: Long) {
            covers.listFiles { file -> file.name.startsWith("drive-$sourceId-") }?.forEach { it.delete() }
        }

        private fun coverFile(
            sourceId: Long,
            fileId: String,
        ) = File(covers, "drive-$sourceId-${md5Hex(fileId)}")
    }

@Singleton
class DriveArtworkSource
    @Inject
    constructor(
        private val reader: DriveMetadataReader,
    ) : RemoteArtworkSource {
        override val provider = RemoteProvider.GOOGLE_DRIVE

        override fun fetch(
            ref: RemoteRef,
            sizePx: Int,
        ): ByteArray? {
            reader.cachedCover(ref.sourceId, ref.remoteId)?.let { return it.takeIf(ByteArray::isNotEmpty) }
            // List rows wait for the folder's own tag pass; only a large request (Now Playing, the
            // notification) reads the file on the spot. "Not yet" is an error, so it is retried later.
            if (sizePx < ON_DEMAND_MIN_PX || reader.read(ref.sourceId, ref.remoteId) == null) throw IOException("Cover not read yet")
            return reader.cachedCover(ref.sourceId, ref.remoteId)?.takeIf(ByteArray::isNotEmpty)
        }

        private companion object {
            const val ON_DEMAND_MIN_PX = 256
        }
    }
