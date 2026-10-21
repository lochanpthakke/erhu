package com.lochanpthakke.empee3.core.network.apple

import com.lochanpthakke.empee3.core.common.result.AppError
import com.lochanpthakke.empee3.core.common.result.AppResult
import com.lochanpthakke.empee3.core.common.result.appResultOf
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.internal.closeQuietly
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HlsMp4Assembler @Inject constructor(
    private val client: OkHttpClient
) {
    suspend fun assemble(playlist: HlsMediaPlaylist, tmpFile: File, destFile: File): AppResult<Unit> = appResultOf {
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
            
            // Check for single file
            val isSingleFile = segments.map { it.url }.distinct().size == 1
            if (isSingleFile) {
                val url = segments.first().url
                val req = Request.Builder().url(url).build()
                val res = client.newCall(req).execute()
                if (!res.isSuccessful) throw Exception("Failed to fetch single fMP4: ${res.code}")
                
                val body = res.body ?: throw Exception("Empty body")
                body.byteStream().use { input ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        out.write(buffer, 0, read)
                        totalBytes += read
                        if (totalBytes > AnimatedArtworkConfig.MAX_DOWNLOAD_BYTES) {
                            throw Exception("Download exceeded limit")
                        }
                    }
                }
                res.closeQuietly()
            } else {
                for (seg in segments) {
                    val builder = Request.Builder().url(seg.url)
                    if (seg.byteLength != null && seg.byteOffset != null) {
                        val end = seg.byteOffset + seg.byteLength - 1
                        builder.header("Range", "bytes=${seg.byteOffset}-$end")
                    }
                    val req = builder.build()
                    val res = client.newCall(req).execute()
                    if (!res.isSuccessful && res.code != 206) throw Exception("Failed to fetch segment: ${res.code}")
                    
                    val body = res.body ?: throw Exception("Empty segment body")
                    body.byteStream().use { input ->
                        val buffer = ByteArray(8192)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            out.write(buffer, 0, read)
                            totalBytes += read
                            if (totalBytes > AnimatedArtworkConfig.MAX_DOWNLOAD_BYTES) {
                                throw Exception("Download exceeded limit")
                            }
                        }
                    }
                    res.closeQuietly()
                }
            }
        }
        
        if (totalBytes < 16384) throw Exception("File too small: $totalBytes")
        
        // Validate ftyp
        val header = ByteArray(8)
        tmpFile.inputStream().use { it.read(header) }
        val ftyp = String(header, 4, 4)
        if (ftyp != "ftyp") throw Exception("Invalid fMP4 header: $ftyp")
        
        if (destFile.exists()) destFile.delete()
        if (!tmpFile.renameTo(destFile)) {
            // fallback to copy
            tmpFile.copyTo(destFile, overwrite = true)
            tmpFile.delete()
        }
    }
}
