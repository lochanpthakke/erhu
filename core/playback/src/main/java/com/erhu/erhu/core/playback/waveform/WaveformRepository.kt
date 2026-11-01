package com.erhu.erhu.core.playback.waveform

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.erhu.erhu.core.common.dispatcher.DispatcherProvider
import com.erhu.erhu.core.common.logging.Logger
import com.erhu.erhu.core.database.repository.WaveformCacheStore
import com.erhu.erhu.core.model.BandLevels
import com.erhu.erhu.core.model.BandWaveformLoad
import com.erhu.erhu.core.model.Song
import com.erhu.erhu.core.model.Waveform
import com.erhu.erhu.core.model.WaveformLoad
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Waveforms of tracks, made from their decoded audio and cached in the library database.
 *
 * Only a requested track is ever processed (never the library), one decode at a time on a single
 * background thread so it cannot compete with playback; concurrent requests for the same file share
 * one decode, which is cancelled when nobody waits for it any more. A cached waveform is used while
 * the file's URI, size and modification time are unchanged, and regenerated otherwise.
 */
@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class WaveformRepository
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val cache: WaveformCacheStore,
        private val logger: Logger,
        dispatcherProvider: DispatcherProvider,
    ) {
        private val io = dispatcherProvider.io
        private val decodeDispatcher = dispatcherProvider.io.limitedParallelism(1)
        private val decodes = SingleFlight<SourceFingerprint, Waveform?>(CoroutineScope(SupervisorJob() + decodeDispatcher))
        private val decoder = WaveformDecoder(context)

        /** Recent windows (few hundred floats each), so reopening an editor does not decode again. */
        private val segments =
            object : LinkedHashMap<String, BandLevels>(SEGMENT_CACHE_SIZE, LOAD_FACTOR, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, BandLevels>?): Boolean = size > SEGMENT_CACHE_SIZE
            }

        /**
         * The window [startMs, endMs) of [song] at [bins] resolution, in three frequency bands, as
         * absolute levels (full scale = 1) so two tracks' windows compare honestly. Decoded from the
         * file on the waveform thread (only that window), kept in a small memory cache.
         */
        fun segment(
            song: Song,
            startMs: Long,
            endMs: Long,
            bins: Int,
        ): Flow<BandWaveformLoad> =
            flow {
                emit(BandWaveformLoad.Loading)
                emit(loadSegment(song, startMs.coerceAtLeast(0L), endMs, bins))
            }.flowOn(io)

        @Suppress("TooGenericExceptionCaught", "ReturnCount") // early answers: invalid range, unreadable file, cached
        private suspend fun loadSegment(
            song: Song,
            startMs: Long,
            endMs: Long,
            bins: Int,
        ): BandWaveformLoad {
            if (endMs <= startMs || bins <= 0) return BandWaveformLoad.Unavailable
            val source = fingerprint(song) ?: return BandWaveformLoad.Unavailable
            val key = "${source.uri}|${source.sizeBytes}|${source.dateModified}|$startMs|$endMs|$bins"
            synchronized(segments) { segments[key] }?.let { return BandWaveformLoad.Ready(it) }
            return try {
                val levels = withContext(decodeDispatcher) { decoder.decodeBands(Uri.parse(source.uri), startMs, endMs, bins) }
                levels?.also { synchronized(segments) { segments[key] = it } }?.let { BandWaveformLoad.Ready(it) }
                    ?: BandWaveformLoad.Unavailable
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.w(TAG, "No waveform window for song ${song.id}: ${e.message}")
                BandWaveformLoad.Unavailable
            }
        }

        /** Loading, then Ready or Unavailable. Never throws; cancel by stopping collection. */
        fun waveform(song: Song): Flow<WaveformLoad> =
            flow {
                emit(WaveformLoad.Loading)
                emit(load(song))
            }.flowOn(io)

        @Suppress("TooGenericExceptionCaught")
        private suspend fun load(song: Song): WaveformLoad {
            val source = fingerprint(song) ?: return WaveformLoad.Unavailable
            return try {
                val cached = cache.get(song.id)?.toWaveformIfValid(source)
                val waveform = cached ?: decodes.run(source) { decodeAndCache(song.id, source, song.durationMs) }
                waveform?.let { WaveformLoad.Ready(it) } ?: WaveformLoad.Unavailable
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Undecodable audio (WaveformDecodeException) or a database failure: no waveform, playback unaffected.
                logger.w(TAG, "No waveform for song ${song.id}: ${e.message}")
                WaveformLoad.Unavailable
            }
        }

        @Suppress("TooGenericExceptionCaught")
        private suspend fun decodeAndCache(
            songId: Long,
            source: SourceFingerprint,
            durationMs: Long,
        ): Waveform? {
            val waveform = decoder.decode(Uri.parse(source.uri), WAVEFORM_BINS, durationMs)?.let(::Waveform) ?: return null
            try {
                cache.put(waveform.toCache(songId, source))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.w(TAG, "Could not cache the waveform of song $songId: ${e.message}")
            }
            return waveform
        }

        /**
         * Size and modification time of the audio behind [song]: from MediaStore for content URIs,
         * from the file itself otherwise. Null when the file is gone or cannot be read.
         */
        @Suppress("TooGenericExceptionCaught")
        private fun fingerprint(song: Song): SourceFingerprint? {
            val uri = song.uri.takeIf { it.isNotBlank() }?.let(Uri::parse)
            return try {
                when {
                    uri == null -> null
                    uri.scheme == "content" -> contentFingerprint(song.uri, uri)
                    else -> fileFingerprint(song.uri, uri.path ?: song.filePath)
                }
            } catch (e: Exception) {
                // SecurityException (no permission), IllegalArgumentException (bad URI), provider failures.
                logger.w(TAG, "Cannot read song ${song.id}: ${e.message}")
                null
            }
        }

        private fun fileFingerprint(
            raw: String,
            path: String?,
        ): SourceFingerprint? = path?.let(::File)?.takeIf { it.isFile }?.let { SourceFingerprint(raw, it.length(), it.lastModified()) }

        private fun contentFingerprint(
            raw: String,
            uri: Uri,
        ): SourceFingerprint? {
            context.contentResolver
                .query(uri, arrayOf(MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATE_MODIFIED), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) return SourceFingerprint(raw, cursor.getLong(0), cursor.getLong(1))
                }
            // Not a MediaStore item: the open file's size is still a usable fingerprint.
            return context.contentResolver.openFileDescriptor(uri, "r")?.use { SourceFingerprint(raw, it.statSize, 0L) }
        }

        private companion object {
            const val TAG = "Waveform"
            const val SEGMENT_CACHE_SIZE = 16
            const val LOAD_FACTOR = 0.75f
        }
    }
