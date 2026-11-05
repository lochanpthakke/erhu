package com.erhu.erhu.core.network.artwork

import com.erhu.erhu.core.model.ProviderOutcome
import kotlinx.serialization.Serializable

@Serializable
enum class ArtworkReleaseKind { ALBUM, SINGLE, EP, UNKNOWN }

@Serializable
data class AlbumArtworkQuery(
    val album: String,
    val artist: String,
    val year: Int = 0,
    val kind: ArtworkReleaseKind = ArtworkReleaseKind.UNKNOWN,
    val releaseId: String? = null,
    val releaseGroupId: String? = null,
    val recordingId: String? = null,
    val sourcePath: String? = null,
)

@Serializable
data class AlbumArtworkCandidate(
    val imageUrl: String,
    val source: String,
    val album: String,
    val artist: String,
    val year: Int = 0,
    val edition: String = "",
    val kind: ArtworkReleaseKind = ArtworkReleaseKind.UNKNOWN,
    val releaseId: String? = null,
    val releaseGroupId: String? = null,
    val catalogId: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val score: Double = 0.0,
    val reviewReasons: List<String> = emptyList(),
    val verifiedImage: Boolean = false,
    val contentHash: String = "",
    val visualHash: String = "",
    val otherSources: List<String> = emptyList(),
) {
    val canRecommend: Boolean get() = verifiedImage && score >= 0.94 && reviewReasons.isEmpty() && minOf(width, height) >= 500
}

data class AlbumArtworkResult(
    val candidates: List<AlbumArtworkCandidate>,
    val outcomes: List<ProviderOutcome>,
    val elapsedMs: Long,
    val fromCache: Boolean = false,
) {
    val recommended: AlbumArtworkCandidate? get() = candidates.firstOrNull { it.canRecommend }
}

data class ArtworkImageFacts(
    val width: Int,
    val height: Int,
    val visualHash: String,
)

fun interface ArtworkImageInspector {
    fun inspect(bytes: ByteArray): ArtworkImageFacts?
}

/** The same small pixel fingerprint is used by Android and the JVM accuracy harness. */
fun artworkVisualHash(pixels: IntArray): String {
    require(pixels.size == 72)
    var hash = 0L

    fun luma(c: Int) = ((c shr 16 and 255) * 299 + (c shr 8 and 255) * 587 + (c and 255) * 114)
    for (y in 0..7) for (x in 0..7) if (luma(pixels[y * 9 + x]) > luma(pixels[y * 9 + x + 1])) hash = hash or (1L shl (y * 8 + x))
    val rgb = listOf(16, 8, 0).map { shift -> pixels.sumOf { it shr shift and 255 } / pixels.size }
    return java.lang.Long.toUnsignedString(hash, 16) + ":" + rgb.joinToString(",")
}
