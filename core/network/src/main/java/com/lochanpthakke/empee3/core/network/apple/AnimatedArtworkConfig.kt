package com.lochanpthakke.empee3.core.network.apple

object AnimatedArtworkConfig {
    const val MAX_SHORT_EDGE_PX = 720
    const val MAX_DOWNLOAD_BYTES = 64 * 1024 * 1024L
    const val ITUNES_MIN_INTERVAL_MS = 3_500L
    const val BREAKER_COOLDOWN_MS = 30 * 60 * 1000L
    const val TTL_NO_ANIMATION_MS = 60L * 24 * 60 * 60 * 1000
    const val TTL_NO_MATCH_MS = 14L * 24 * 60 * 60 * 1000
}
