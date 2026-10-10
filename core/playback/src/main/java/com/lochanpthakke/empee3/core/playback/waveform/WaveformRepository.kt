package com.lochanpthakke.empee3.core.playback.waveform

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.lochanpthakke.empee3.core.common.dispatcher.DispatcherProvider
import com.lochanpthakke.empee3.core.common.logging.Logger
import com.lochanpthakke.empee3.core.database.repository.WaveformCacheStore
import com.lochanpthakke.empee3.core.model.Song
import com.lochanpthakke.empee3.core.model.Waveform
import com.lochanpthakke.empee3.core.model.WaveformLoad
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
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
        }
    }
