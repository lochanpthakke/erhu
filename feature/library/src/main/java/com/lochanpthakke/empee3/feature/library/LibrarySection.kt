package com.lochanpthakke.empee3.feature.library

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The primary destinations, switched by [LibraryNavigationBar] (Search is the
 * separate button beside it): Tracks (home feed), Albums, Library, VYBE (on-device discovery).
 */
enum class LibrarySection(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    TRACKS("Tracks", Icons.Outlined.MusicNote, Icons.Filled.MusicNote),
    ALBUMS("Albums", Icons.Outlined.Album, Icons.Filled.Album),
    LIBRARY("Library", Icons.Outlined.LibraryMusic, Icons.Filled.LibraryMusic),
    VYBE("VYBE", Icons.Outlined.AutoAwesome, Icons.Filled.AutoAwesome),
}

/** The full shelves the Library overview opens with "More". */
enum class LibraryShelf(
    val label: String,
    val icon: ImageVector,
) {
    PLAYLISTS("Playlists", Icons.AutoMirrored.Filled.QueueMusic),
    FAVORITES("Favorites", Icons.Filled.Favorite),
    ARTISTS("Artists", Icons.Filled.Person),
}
