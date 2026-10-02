package com.lochanpthakke.empee3.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import com.lochanpthakke.empee3.core.designsystem.foundation.Motion

/*
 * Screen transitions, by the spatial relationship between destinations:
 * details slide in from the end edge over a receding parent, Now Playing rises
 * like a sheet over a held screen, top-level destinations fade through.
 */

/** Destinations pushed on top of another screen (they slide in from the end edge). */
private fun NavDestination.isDetail(): Boolean =
    hasRoute(AlbumDetail::class) || hasRoute(PlaylistDetail::class) || hasRoute(SettingsCategory::class)

/** Now Playing rises from the bottom chrome like a sheet. */
private fun NavDestination.isPlayer(): Boolean = hasRoute(NowPlaying::class)

internal fun navEnter(target: NavDestination): EnterTransition =
    when {
        target.isPlayer() -> Motion.sheetEnter
        target.isDetail() -> Motion.detailEnter
        else -> Motion.fadeThroughEnter
    }

internal fun navExit(target: NavDestination): ExitTransition =
    when {
        target.isPlayer() -> Motion.hold
        target.isDetail() -> Motion.detailExit
        else -> Motion.fadeThroughExit
    }

internal fun navPopEnter(leaving: NavDestination): EnterTransition =
    when {
        leaving.isPlayer() -> Motion.unhold
        leaving.isDetail() -> Motion.detailPopEnter
        else -> Motion.fadeThroughEnter
    }

internal fun navPopExit(leaving: NavDestination): ExitTransition =
    when {
        leaving.isPlayer() -> Motion.sheetExit
        leaving.isDetail() -> Motion.detailPopExit
        else -> Motion.fadeThroughExit
    }
