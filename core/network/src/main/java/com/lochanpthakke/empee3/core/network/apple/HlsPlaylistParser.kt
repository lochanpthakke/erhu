package com.lochanpthakke.empee3.core.network.apple

import java.net.URI
import kotlin.math.min

data class HlsSegmentRef(
    val url: String,
    val byteLength: Long?,
    val byteOffset: Long?
)

data class HlsMediaPlaylist(
    val init: HlsSegmentRef?,
    val segments: List<HlsSegmentRef>,
    val isEncrypted: Boolean,
    val isTransportStream: Boolean
)

object HlsPlaylistParser {
    fun selectBestVariantUrl(masterUrl: String, masterContent: String, maxShortEdgePx: Int): String? {
        val lines = masterContent.split("\n").map { it.trim() }
        var bestUrl: String? = null
        var bestBandwidth = -1
        var minAboveCapResolution = Int.MAX_VALUE
        var minAboveCapUrl: String? = null
        
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (line.startsWith("#EXT-X-STREAM-INF:")) {
                val attrs = parseAttributes(line.substringAfter(":"))
                val urlLine = lines.getOrNull(i + 1)?.trim() ?: ""
                
                val codecs = attrs["CODECS"]?.replace("\"", "") ?: ""
                val resolution = attrs["RESOLUTION"]
                val bandwidth = attrs["BANDWIDTH"]?.toIntOrNull() ?: -1
                val videoRange = attrs["VIDEO-RANGE"]
                
                if (videoRange != null && videoRange != "SDR") {
                    i++
                    continue
                }
                if (codecs.startsWith("dvh1") || codecs.startsWith("dvhe")) {
                    i++
                    continue
                }
                
                if (urlLine.isNotEmpty()) {
                    var w = 0
                    var h = 0
                    if (resolution != null && resolution.contains("x")) {
                        val parts = resolution.split("x")
                        w = parts[0].toIntOrNull() ?: 0
                        h = parts[1].toIntOrNull() ?: 0
                    }
                    val shortEdge = if (w > 0 && h > 0) min(w, h) else 0
                    
                    val absoluteUrl = URI.create(masterUrl).resolve(urlLine).toString()
                    
                    if (shortEdge <= maxShortEdgePx) {
                        if (bandwidth > bestBandwidth) {
                            bestBandwidth = bandwidth
                            bestUrl = absoluteUrl
                        }
                    } else {
                        if (shortEdge < minAboveCapResolution && codecs.startsWith("avc1")) {
                            minAboveCapResolution = shortEdge
                            minAboveCapUrl = absoluteUrl
                        }
                    }
                }
            }
            i++
        }
        
        return bestUrl ?: minAboveCapUrl
    }
    
    fun parseMediaPlaylist(mediaUrl: String, mediaContent: String): HlsMediaPlaylist {
        val lines = mediaContent.split("\n").map { it.trim() }
        var initSegment: HlsSegmentRef? = null
        val segments = mutableListOf<HlsSegmentRef>()
        var isEncrypted = false
        var isTransportStream = false
        
        var currentLength: Long? = null
        var currentOffset: Long? = null
        var lastOffset: Long = 0
        
        for (line in lines) {
            if (line.startsWith("#EXT-X-KEY:")) {
                val attrs = parseAttributes(line.substringAfter(":"))
                if (attrs["METHOD"] != "NONE") {
                    isEncrypted = true
                }
            } else if (line.startsWith("#EXT-X-MAP:")) {
                val attrs = parseAttributes(line.substringAfter(":"))
                val uri = attrs["URI"]?.replace("\"", "")
                val byterange = attrs["BYTERANGE"]?.replace("\"", "")
                if (uri != null) {
                    val absoluteUrl = URI.create(mediaUrl).resolve(uri).toString()
                    var len: Long? = null
                    var off: Long? = null
                    if (byterange != null) {
                        val parts = byterange.split("@")
                        len = parts[0].toLongOrNull()
                        if (parts.size > 1) {
                            off = parts[1].toLongOrNull()
                        }
                    }
                    initSegment = HlsSegmentRef(absoluteUrl, len, off)
                    if (off != null && len != null) {
                        lastOffset = off + len
                    } else if (len != null) {
                        lastOffset = len
                    }
                }
            } else if (line.startsWith("#EXT-X-BYTERANGE:")) {
                val byterange = line.substringAfter(":")
                val parts = byterange.split("@")
                currentLength = parts[0].toLongOrNull()
                if (parts.size > 1) {
                    currentOffset = parts[1].toLongOrNull()
                } else {
                    currentOffset = lastOffset
                }
            } else if (line.startsWith("#EXTINF:")) {
                // segment follows
            } else if (line.isNotEmpty() && !line.startsWith("#")) {
                val absoluteUrl = URI.create(mediaUrl).resolve(line).toString()
                if (absoluteUrl.endsWith(".ts")) {
                    isTransportStream = true
                }
                
                segments.add(HlsSegmentRef(absoluteUrl, currentLength, currentOffset))
                
                if (currentOffset != null && currentLength != null) {
                    lastOffset = currentOffset + currentLength
                } else if (currentLength != null) {
                    lastOffset += currentLength
                }
                
                currentLength = null
                currentOffset = null
            }
        }
        
        return HlsMediaPlaylist(initSegment, segments, isEncrypted, isTransportStream)
    }
    
    private fun parseAttributes(attrString: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val regex = Regex("([A-Z0-9\\-]+)=(\"[^\"]*\"|[^,]*)")
        for (match in regex.findAll(attrString)) {
            map[match.groupValues[1]] = match.groupValues[2].replace("\"", "")
        }
        return map
    }
}
