package com.erhu.erhu.feature.library

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.erhu.erhu.core.designsystem.R

/**
 * The primary destinations, switched by [LibraryNavigationBar] (Search is the
 * separate button beside it): Home (the home feed and every track; still TRACKS in code), Albums, Library, VYBE (on-device discovery).
 */
enum class LibrarySection(
    val label: String,
    /** The page's own title in its header; the navigation keeps [label]. */
    val pageTitle: String = label,
) {
    TRACKS("Home"),
    ALBUMS("Albums"),
    LIBRARY("Library"),
    VYBE("VYBE"),
    ;

    val icon: ImageVector
        @Composable get() =
            when (this) {
                TRACKS -> Icons.Outlined.Home
                ALBUMS -> Icons.Outlined.Album
                LIBRARY -> Icons.Outlined.LibraryMusic
                VYBE -> ImageVector.vectorResource(R.drawable.ic_boombox)
            }

    val selectedIcon: ImageVector
        @Composable get() =
            when (this) {
                TRACKS -> Icons.Filled.Home
                ALBUMS -> Icons.Filled.Album
                LIBRARY -> Icons.Filled.LibraryMusic
                VYBE -> ImageVector.vectorResource(R.drawable.ic_boombox)
            }
}

/** The full shelves the Library overview opens with "More". */
enum class LibraryShelf(
    val label: String,
) {
    PLAYLISTS("Playlists"),
    FAVORITES("Favorites"),
    ARTISTS("Artists"),
    ;

    val icon: ImageVector
        @Composable get() =
            when (this) {
                PLAYLISTS -> Icons.AutoMirrored.Filled.QueueMusic
                FAVORITES -> Icons.Filled.Favorite
                ARTISTS -> Icons.Filled.Person
            }
}
