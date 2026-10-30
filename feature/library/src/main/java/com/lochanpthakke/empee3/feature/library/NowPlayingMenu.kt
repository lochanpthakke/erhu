package com.lochanpthakke.empee3.feature.library

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import com.lochanpthakke.empee3.core.designsystem.component.GlassMenu
import com.lochanpthakke.empee3.core.designsystem.component.GlassMenuDivider
import com.lochanpthakke.empee3.core.designsystem.component.GlassMenuItem
import com.lochanpthakke.empee3.core.designsystem.component.GlassMenuSection
import com.lochanpthakke.empee3.core.designsystem.component.PressIconButton

/**
 * The ⋮ menu of Now Playing: the secondary actions, grouped (the track, the library, playback and
 * display). A popup window: touches on it never reach the carousel or controls underneath, and
 * every item closes it before acting.
 */
@Composable
internal fun NowPlayingMenu(
    actions: NowPlayingMenuActions,
    isHidden: Boolean,
    loopOn: Boolean,
    enabled: Boolean,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        PressIconButton(onClick = { open = true }, enabled = enabled) {
            Icon(imageVector = Icons.Default.MoreVert, contentDescription = "More options")
        }
        GlassMenu(
            label = "Now Playing options",
            expanded = open,
            onDismissRequest = { open = false },
        ) {
            val close: (() -> Unit) -> () -> Unit = { action ->
                {
                    open = false
                    action()
                }
            }
            Group("Track")
            MenuItem("Music information", Icons.Outlined.Info, close(actions.onMusicInfo))
            MenuItem("Audio details", Icons.Outlined.Tune, close(actions.onAudioDetails))
            GroupDivider()
            Group("Library")
            MenuItem("Bookmark this moment", Icons.Default.BookmarkAdd, close(actions.onAddBookmark))
            MenuItem(
                if (loopOn) "Bookmarks & A-B loop (on)" else "Bookmarks & A-B loop",
                Icons.Default.Bookmarks,
                close(actions.onBookmarks),
            )
            MenuItem("Add to playlist", Icons.AutoMirrored.Filled.PlaylistAdd, close(actions.onAddToPlaylist))
            actions.onToggleHidden?.let { toggle ->
                if (isHidden) {
                    MenuItem("Show in Smart Playlists again", Icons.Default.Visibility, close(toggle))
                } else {
                    MenuItem("Hide from Smart Playlists", Icons.Default.VisibilityOff, close(toggle))
                }
            }
            GroupDivider()
            Group("Playback & display")
            actions.onStartRadio?.let { MenuItem("Start radio", Icons.Default.Radio, close(it)) }
            actions.onRefreshRadio?.let { MenuItem("New radio picks", Icons.Default.Refresh, close(it)) }
            MenuItem("Queue", Icons.AutoMirrored.Filled.QueueMusic, close(actions.onQueue))
            MenuItem("Visualizer", Icons.Default.GraphicEq, close(actions.onVisualizer))
            MenuItem("Equalizer", Icons.Default.Equalizer, close(actions.onEqualizer))
        }
    }
}

@Composable
private fun MenuItem(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    GlassMenuItem(text = { Text(label) }, icon = icon, onClick = onClick)
}

@Composable
private fun Group(title: String) = GlassMenuSection(title)

@Composable
private fun GroupDivider() = GlassMenuDivider()
