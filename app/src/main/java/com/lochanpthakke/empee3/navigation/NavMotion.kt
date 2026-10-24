package com.lochanpthakke.empee3.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import com.lochanpthakke.empee3.core.designsystem.foundation.Motion

/*
 * Screen transitions (motion level 3), by the spatial relationship between
 * destinations: details slide in from the end edge over a receding parent and
 * slide back out on Back; top-level destinations fade through. The whole screen
 * moves; nothing inside it adds its own entrance on top.
 */

/** Destinations pushed on top of another screen (they slide in from the end edge). */
private fun NavDestination.isDetail(): Boolean =
    hasRoute(AlbumDetail::class) ||
        hasRoute(ArtistDetail::class) ||
        hasRoute(PlaylistDetail::class) ||
        hasRoute(SmartPlaylistDetail::class) ||
        hasRoute(SettingsCategory::class) ||
        hasRoute(LibraryBuilder::class)

internal fun navEnter(target: NavDestination): EnterTransition = if (target.isDetail()) Motion.detailEnter else Motion.fadeThroughEnter

internal fun navExit(target: NavDestination): ExitTransition = if (target.isDetail()) Motion.detailExit else Motion.fadeThroughExit

internal fun navPopEnter(leaving: NavDestination): EnterTransition =
    if (leaving.isDetail()) Motion.detailPopEnter else Motion.fadeThroughEnter

internal fun navPopExit(leaving: NavDestination): ExitTransition = if (leaving.isDetail()) Motion.detailPopExit else Motion.fadeThroughExit
