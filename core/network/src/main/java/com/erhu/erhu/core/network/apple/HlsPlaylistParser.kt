package com.erhu.erhu.core.network.apple

import java.net.URI
import kotlin.math.max
import kotlin.math.min

data class HlsSegmentRef(
    val url: String,
    val byteLength: Long?,
    val byteOffset: Long?,
)

data class HlsMediaPlaylist(
    val init: HlsSegmentRef?,
    val segments: List<HlsSegmentRef>,
    val isEncrypted: Boolean,
    val isTransportStream: Boolean,
) {
    /** The bytes the playlist's parts add up to, or null unless every part states its length. */
    val statedBytes: Long?
        get() {
            val parts = listOfNotNull(init) + segments
            if (segments.isEmpty() || parts.any { it.byteLength == null }) return null
            return parts.sumOf { it.byteLength!! }
        }
}

data class HlsVariantStream(
    val resolutionText: String,
    val width: Int,
    val height: Int,
    val bandwidth: Int,
    val variantUrl: String,
    val estimatedSizeMb: Float,
)

object HlsPlaylistParser {
    fun parseMasterPlaylistVariants(
        masterUrl: String,
        masterContent: String,
    ): List<HlsVariantStream> {
        val lines = masterContent.split("\n").map { it.trim() }
        val variants = mutableListOf<HlsVariantStream>()

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

                    val absoluteUrl = URI.create(masterUrl).resolve(urlLine).toString()
                    val resText = if (w > 0 && h > 0) "${w}x$h" else "Default"

                    // Estimate size for ~5 sec video clip at given bandwidth
                    val estimatedMb = if (bandwidth > 0) (bandwidth * 5f) / (8 * 1024 * 1024) else 1.0f

                    variants.add(
                        HlsVariantStream(
                            resolutionText = resText,
                            width = w,
                            height = h,
                            bandwidth = bandwidth,
                            variantUrl = absoluteUrl,
                            estimatedSizeMb = estimatedMb,
                        ),
                    )
                }
            }
            i++
        }

        return variants.sortedByDescending { it.bandwidth }
    }

    /**
     * The variant to download for display on the phone: the largest whose short edge is at most
     * [maxShortEdgePx] (a Now Playing card is ~800 px, the album header 1080 px), else the smallest.
     * Apple's top variants (e.g. 2048x2732 HEVC Main 10, 17 Mbit/s) are far beyond what a cover
     * needs and stall the phone's decoder. [variants] may be in any order.
     */
    fun displayVariant(
        variants: List<HlsVariantStream>,
        maxShortEdgePx: Int = AnimatedArtworkConfig.maxShortEdgePx,
    ): HlsVariantStream? {
        val bySize = variants.sortedByDescending { min(it.width, it.height) * 1L * max(it.width, it.height) + it.bandwidth }
        return bySize.firstOrNull { min(it.width, it.height) in 1..maxShortEdgePx } ?: bySize.lastOrNull()
    }

    fun selectBestVariantUrl(
        masterUrl: String,
        masterContent: String,
        maxShortEdgePx: Int,
    ): String? {
        val variants = parseMasterPlaylistVariants(masterUrl, masterContent)
        val candidate = displayVariant(variants, maxShortEdgePx)
        return candidate?.variantUrl
    }

    fun parseMediaPlaylist(
        mediaUrl: String,
        mediaContent: String,
    ): HlsMediaPlaylist {
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
