package com.lochanpthakke.empee3.core.network.apple

import com.lochanpthakke.empee3.core.common.dispatcher.DispatcherProvider
import com.lochanpthakke.empee3.core.common.result.AppResult
import com.lochanpthakke.empee3.core.common.result.appResultOf
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HlsMp4Assembler
    @Inject
    constructor(
        @AppleWebClient private val client: OkHttpClient,
        private val dispatchers: DispatcherProvider,
    ) {
        suspend fun assemble(
            playlist: HlsMediaPlaylist,
            tmpFile: File,
            destFile: File,
        ): AppResult<Unit> =
            withContext(dispatchers.io) {
                appResultOf {
                    try {
                        assembleInto(playlist, tmpFile, destFile)
                    } catch (e: Throwable) {
                        // Cancelled or failed part way: no partial cover is left behind.
                        tmpFile.delete()
                        throw e
                    }
                }
            }

        private suspend fun assembleInto(
            playlist: HlsMediaPlaylist,
            tmpFile: File,
            destFile: File,
        ) {
            if (playlist.isEncrypted) throw Exception("Encrypted HLS not supported")
            if (playlist.isTransportStream) throw Exception("TS segments not supported")

            var totalBytes = 0L
            tmpFile.parentFile?.mkdirs()

            FileOutputStream(tmpFile).use { out ->
                val segments = mutableListOf<HlsSegmentRef>()
                if (playlist.init != null) {
                    segments.add(playlist.init)
                }
                segments.addAll(playlist.segments)

                val isSingleFile = segments.map { it.url }.distinct().size == 1
                if (isSingleFile) {
                    val url = segments.first().url
                    val req = Request.Builder().url(url).build()
                    val res = client.newCall(req).execute()
                    res.use {
                        if (!res.isSuccessful) throw Exception("Failed to fetch single fMP4: ${res.code}")
                        val body = res.body ?: throw Exception("Empty body")
                        totalBytes = copyLimited(body.byteStream(), out, totalBytes)
                    }
                } else {
                    for (seg in segments) {
                        val builder = Request.Builder().url(seg.url)
                        if (seg.byteLength != null && seg.byteOffset != null) {
                            val end = seg.byteOffset + seg.byteLength - 1
                            builder.header("Range", "bytes=${seg.byteOffset}-$end")
                        }
                        val req = builder.build()
                        val res = client.newCall(req).execute()
                        res.use {
                            if (!res.isSuccessful && res.code != 206) throw Exception("Failed to fetch segment: ${res.code}")
                            val body = res.body ?: throw Exception("Empty segment body")
                            totalBytes = copyLimited(body.byteStream(), out, totalBytes)
                        }
                    }
                }
            }

            if (totalBytes < 16384) throw Exception("File too small: $totalBytes")

            val header = ByteArray(8)
            tmpFile.inputStream().use { it.read(header) }
            val ftyp = String(header, 4, 4)
            if (ftyp != "ftyp") throw Exception("Invalid fMP4 header: $ftyp")
            // Apple's timestamps start at 10 s; the joined file must start at 0 or the cover waits 10 s.
            FragmentedMp4Timeline.startAtZero(tmpFile)

            if (destFile.exists()) destFile.delete()
            if (!tmpFile.renameTo(destFile)) {
                tmpFile.copyTo(destFile, overwrite = true)
                tmpFile.delete()
            }
        }

        /** Copies [input] to [out], stopping on cancellation or past the size limit; returns the new total. */
        private suspend fun copyLimited(
            input: InputStream,
            out: FileOutputStream,
            startBytes: Long,
        ): Long {
            var totalBytes = startBytes
            input.use {
                val buffer = ByteArray(8192)
                var read: Int
                while (it.read(buffer).also { count -> read = count } != -1) {
                    currentCoroutineContext().ensureActive()
                    out.write(buffer, 0, read)
                    totalBytes += read
                    if (totalBytes > AnimatedArtworkConfig.maxDownloadBytes) {
                        throw Exception("Download exceeded limit")
                    }
                }
            }
            return totalBytes
        }
    }
