package com.erhu.erhu.core.network.artwork

/**
 * Conservative whole-image comparison; a different portrait/edition is not an upgrade.
 * Follows the spatial-colour and edge checks in MeezaUni/Erhu-iOS ArtworkUpgradeMatch.swift.
 */
object ArtworkUpgradeMatch {
    const val MIN_GAIN = 1.25
    const val MAX_SAVED_PIXELS = 1440
    const val SIGNATURE_SIZE = 16

    fun isLarger(
        width: Int,
        height: Int,
        originalWidth: Int,
        originalHeight: Int,
    ): Boolean {
        if (minOf(width, height, originalWidth, originalHeight) <= 0) return false
        val scale = minOf(1.0, MAX_SAVED_PIXELS.toDouble() / maxOf(width, height))
        return width * scale >= originalWidth * MIN_GAIN &&
            height * scale >= originalHeight * MIN_GAIN &&
            kotlin.math.abs(width.toDouble() / height - originalWidth.toDouble() / originalHeight) < 0.025
    }

    @Suppress("ReturnCount") // Shape, colour and edge checks reject independently.
    fun resembles(
        original: IntArray,
        candidate: IntArray,
    ): Boolean {
        if (original.size != SIGNATURE_SIZE * SIGNATURE_SIZE || candidate.size != original.size) return false
        val difference =
            original.indices
                .sumOf { index ->
                    listOf(16, 8, 0).sumOf { shift ->
                        kotlin.math.abs((original[index] shr shift and 255) - (candidate[index] shr shift and 255))
                    }
                }.toDouble() / (original.size * 3 * 255)
        if (difference >= 0.065) return false
        var differentEdges = 0

        fun light(pixel: Int) = (pixel shr 16 and 255) * 3 + (pixel shr 8 and 255) * 6 + (pixel and 255)
        for (y in 0 until SIGNATURE_SIZE) {
            for (x in 0 until SIGNATURE_SIZE - 1) {
                val index = y * SIGNATURE_SIZE + x
                val a = light(original[index + 1]) - light(original[index])
                val b = light(candidate[index + 1]) - light(candidate[index])
                if (kotlin.math.abs(a) > 40 && kotlin.math.abs(b) > 40 && (a > 0) != (b > 0)) differentEdges++
            }
        }
        return differentEdges <= 5
    }
}
