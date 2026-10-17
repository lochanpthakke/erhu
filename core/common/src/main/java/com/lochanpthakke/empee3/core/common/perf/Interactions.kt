package com.lochanpthakke.empee3.core.common.perf

import android.os.Trace

/**
 * What the app is doing right now, for frame diagnostics: each slow frame is reported with the
 * interactions active while it was drawn (a list scrolling, the player moving, artwork decoding,
 * VYBE indexing...), so a stutter can be traced to its journey.
 *
 * - Values are fixed labels from the app's own code ("albums", "moving"), never song titles,
 *   file names, ids or other library contents.
 * - Without a [sink] (every build except debug, where the app installs one) this does nothing.
 */
object Interactions {
    /** Receives interaction changes; null [value] = that interaction ended. Any thread. */
    fun interface Sink {
        fun put(
            key: String,
            value: String?,
        )
    }

    @Volatile
    var sink: Sink? = null

    /** Visible screen or page (navigation route pattern or section). */
    const val SCREEN = "screen"

    /** A list or grid being dragged or flung: which one. */
    const val SCROLL = "scroll"

    /** The player between mini and full ("moving"). */
    const val PLAYER = "player"

    /** Now Playing's track strip being swiped or settling ("swipe"). */
    const val TRACK = "track"

    /** Artwork decodes in progress ("decoding"). */
    const val ARTWORK = "artwork"

    /** VYBE's model being loaded ("loading"). */
    const val VYBE_MODEL = "vybe-model"

    /** VYBE's index being built ("indexing"). */
    const val VYBE_INDEX = "vybe-index"

    /** A VYBE query being answered ("searching"). */
    const val VYBE_SEARCH = "vybe-search"

    /** Library-wide background work ("scanning"). */
    const val LIBRARY = "library"

    fun put(
        key: String,
        value: String?,
    ) {
        sink?.put(key, value)
    }
}

/**
 * Runs [block] inside a named trace section, visible in Perfetto and Macrobenchmark traces.
 * Nearly free when nothing is tracing. Begins and ends on the calling thread: keep [block]
 * free of suspension points that could resume elsewhere.
 */
inline fun <T> traced(
    name: String,
    block: () -> T,
): T {
    Trace.beginSection(name)
    try {
        return block()
    } finally {
        Trace.endSection()
    }
}
