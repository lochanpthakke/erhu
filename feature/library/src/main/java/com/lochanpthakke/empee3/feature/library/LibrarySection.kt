package com.lochanpthakke.empee3.feature.library

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Person
import androidx.compose.ui.graphics.vector.ImageVector

/** The library's local sections, switched by [LibraryNavigationBar]. */
enum class LibrarySection(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    TRACKS("Tracks", Icons.Outlined.MusicNote, Icons.Filled.MusicNote),
    FAVORITES("Favorites", Icons.Outlined.FavoriteBorder, Icons.Filled.Favorite),
    ALBUMS("Albums", Icons.Outlined.Album, Icons.Filled.Album),
    ARTISTS("Artists", Icons.Outlined.Person, Icons.Filled.Person),
}
