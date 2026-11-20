package com.erhu.erhu.core.network.remote.drive

import android.content.Context
import android.media.MediaMetadataRetriever
import com.erhu.erhu.core.common.artwork.ArtworkLoader
import com.erhu.erhu.core.model.RemoteArtworkSource
import com.erhu.erhu.core.model.RemoteProvider
import com.erhu.erhu.core.model.RemoteRef
import com.erhu.erhu.core.model.RemoteStreamResolver
import com.erhu.erhu.core.model.ResolvedStream
import com.erhu.erhu.core.network.remote.subsonic.md5Hex
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.concurrent.Semaphore
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
    val genre: String? = null,
    val year: Int = 0,
)

/**
 * Reads a Drive file's own tags and embedded cover by asking the platform's metadata reader to
 * open the authorized stream. It fetches only the parts of the file it needs (byte ranges), never
 * the whole file, and never writes to Drive. A few files at a time ([PARALLEL_READS]), so a long
 * folder neither floods the connection nor makes a cover shown on screen wait behind the whole
 * folder. The cover is kept in the app's cache folder (an empty file means "has no cover").
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

        private val reads = Semaphore(PARALLEL_READS)

        /** Blocking. Null when the file could not be read (offline, unsupported, sign-in needed). */
        fun read(
            sourceId: Long,
            fileId: String,
        ): DriveTags? {
            reads.acquireUninterruptibly()
            return try {
                readFile(sourceId, fileId)
            } finally {
                reads.release()
            }
        }

        @Suppress("TooGenericExceptionCaught", "SwallowedException")
        private fun readFile(
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
                        genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)?.let(::readableGenre),
                        year =
                            (
                                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
                                    ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
                            )?.let(::yearOf) ?: 0,
                    )
                covers.mkdirs()
                val picture = retriever.embeddedPicture
                coverFile(sourceId, fileId).writeBytes(picture ?: ByteArray(0))
                // Covers already on screen as placeholders try again now, not on the next scroll.
                if (picture != null && picture.isNotEmpty()) ArtworkLoader.notifyRemoteCoversChanged()
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

        private companion object {
            /** Each read is a few small range requests, so it waits on latency, not bandwidth. */
            const val PARALLEL_READS = 3
        }
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

/** A plausible year from a YEAR or DATE tag ("2018", "2018-05-04", "20180504T..."); null otherwise. */
@Suppress("MagicNumber")
internal fun yearOf(text: String): Int? =
    Regex("""(19|20)\d{2}""")
        .find(text)
        ?.value
        ?.toIntOrNull()
        ?.takeIf { it in 1900..2099 }

/**
 * A genre tag as text. Old ID3 files store a number ("(17)" or "17"): those, and empty or
 * placeholder values, count as no genre so the online lookup can fill it in.
 */
internal fun readableGenre(text: String): String? =
    text
        .trim()
        .takeUnless { it.isEmpty() || it.matches(Regex("""\(?\d+\)?""")) || it.equals("other", true) || it.equals("unknown", true) }
