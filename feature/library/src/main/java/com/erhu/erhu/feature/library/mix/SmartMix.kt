package com.erhu.erhu.feature.library.mix

/**
 * The kinds of smart mix erhu can build. [personal] mixes come from the listener's own play
 * history and are only offered once there is enough of it; the others come from the library's
 * tags (and are ordered by taste when there is history). Shelf order is declaration order.
 */
enum class MixKind(
    val key: String,
    val title: String,
    val personal: Boolean,
) {
    ROTATION("rotation", "In Your Rotation", personal = true),
    YOUR_SOUND("your-sound", "Your Kind of Sound", personal = true),
    FORGOTTEN("forgotten", "Forgotten Favourites", personal = true),
    DEEP_CUTS("deep-cuts", "Deep Cuts", personal = true),
    HIGH_ENERGY("high-energy", "High Energy", personal = false),
    CHILL("chill", "Chill", personal = false),
    LATE_NIGHT("late-night", "Late Night", personal = false),
    A_LITTLE_DIFFERENT("a-little-different", "A Little Different", personal = true),

    /** Library collections: each needs only tags the library really has (one artist, real years, lengths). */
    SPOTLIGHT("spotlight", "Spotlight", personal = false),
    THROWBACKS("throwbacks", "Throwbacks", personal = false),
    QUICK_HITS("quick-hits", "Quick Hits", personal = false),
    LONG_LISTENS("long-listens", "Long Listens", personal = false),

    /** Unplayed songs: needs some history, or it would just be the whole library. */
    YET_TO_HEAR("yet-to-hear", "Yet to Be Heard", personal = false),

    /** Fallbacks while there is little history: honest, non-personalised collections. */
    FAVOURITES("favourites", "Favourites", personal = false),
    FRESH("fresh", "Fresh in Your Library", personal = false),
    ;

    companion object {
        fun fromKey(key: String): MixKind? = entries.firstOrNull { it.key == key }
    }
}

/**
 * One built mix: its songs in play order (no duplicates), a truthful line about how it was made
 * and its title (the kind's, or a specific one such as "Spotlight: Drake").
 */
data class SmartMix(
    val kind: MixKind,
    val songIds: List<Long>,
    val description: String,
    val title: String = kind.title,
)

/**
 * Every mix worth showing right now. [needsMoreListening]: the play history is too short for the
 * personal mixes yet, so only library-based ones (if any) are offered.
 */
data class SmartMixes(
    val mixes: List<SmartMix>,
    val needsMoreListening: Boolean,
) {
    fun mix(kind: MixKind): SmartMix? = mixes.firstOrNull { it.kind == kind }
}
