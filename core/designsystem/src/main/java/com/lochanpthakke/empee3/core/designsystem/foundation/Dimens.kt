package com.lochanpthakke.empee3.core.designsystem.foundation

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Spacing scale. Values match the spacing the app already used most
 * (4/8/12/16/24/32 dp), so adopting the tokens does not change layouts.
 */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp

    /** Default horizontal gutter between screen edge and content. */
    val screenGutter = lg

    /**
     * Wider gutter for hero layouts (Now Playing): the large artwork reads
     * better with more breathing room. The one intentional exception to
     * [screenGutter].
     */
    val heroGutter = xl
}

/** Corner radii for components that do not use the theme's Material shapes. */
object Radius {
    val small = 8.dp
    val medium = 12.dp
    val large = 16.dp

    val smallShape = RoundedCornerShape(small)
    val mediumShape = RoundedCornerShape(medium)
    val largeShape = RoundedCornerShape(large)
    val pillShape = RoundedCornerShape(percent = 50)
}

object IconSize {
    val small = 20.dp
    val standard = 24.dp
    val large = 36.dp

    /** Icon inside the primary (play/pause) button. */
    val hero = 40.dp
}

object ArtworkSize {
    /** Small thumbnail in dense rows (metadata editor queue). */
    val compact = 40.dp

    /** Song rows in lists, search, playlists and the queue. */
    val list = 48.dp

    /** Mini player thumbnail. */
    val miniPlayer = 44.dp

    /**
     * Minimum cell width for artwork grids. Two columns on a ~360 dp phone,
     * more on wider screens.
     */
    val gridMin = 140.dp

    /** Upper bound for full-width hero artwork on large screens. */
    val heroMax = 480.dp
}

/** Fixed component heights shared across screens. */
object ComponentSize {
    /** Primary round play/pause button. */
    val primaryButton = 64.dp

    /** Height of the playback progress line in the mini player. */
    val miniProgress = 3.dp
}
