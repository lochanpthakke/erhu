package com.lochanpthakke.empee3.core.network.apple

object AnimatedArtworkConfig {
    /** Largest short edge downloaded for a cover: Now Playing's card is ~800 px, the album header 1080 px. */
    @Volatile
    var maxShortEdgePx: Int = 1080

    @Volatile
    var maxDownloadBytes: Long = 64 * 1024 * 1024L

    const val ITUNES_MIN_INTERVAL_MS = 3_500L
    const val BREAKER_COOLDOWN_MS = 30 * 60 * 1000L

    /** After an HTTP 429 without a Retry-After, Apple requests pause this long (at most BREAKER_COOLDOWN_MS). */
    const val RATE_LIMIT_PAUSE_MS = 5 * 60 * 1000L
    const val TTL_NO_ANIMATION_MS = 60L * 24 * 60 * 60 * 1000
    const val TTL_NO_MATCH_MS = 14L * 24 * 60 * 60 * 1000

    /**
     * Storefronts checked for a motion cover after the first one has none, in this order after
     * the phone's country and the search storefront. Motion covers are per storefront (e.g. one
     * album has its cover in India's catalogue but not the US one).
     */
    private val MOTION_STOREFRONTS = listOf("us", "in", "ca", "gb", "au", "de", "fr", "jp", "br", "mx")

    /** Where to look for an album's motion cover, in order, without duplicates. */
    fun motionStorefronts(
        deviceCountry: String,
        searchStorefront: String,
    ): List<String> =
        (listOf(deviceCountry, searchStorefront) + MOTION_STOREFRONTS)
            .map { it.trim().lowercase() }
            .filter { it.length == 2 && it.all(Char::isLetter) }
            .distinct()
}
