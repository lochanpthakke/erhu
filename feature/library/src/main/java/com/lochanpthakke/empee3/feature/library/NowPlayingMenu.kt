package com.lochanpthakke.empee3.feature.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lochanpthakke.empee3.core.designsystem.component.PressIconButton
import com.lochanpthakke.empee3.core.designsystem.foundation.IconSize
import com.lochanpthakke.empee3.core.designsystem.foundation.Radius
import com.lochanpthakke.empee3.core.designsystem.foundation.Spacing

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
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.widthIn(min = 240.dp, max = 320.dp),
            shape = Radius.largeShape,
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 6.dp,
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
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
    DropdownMenuItem(
        text = { Text(label, style = MaterialTheme.typography.bodyMedium) },
        leadingIcon = { Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(IconSize.small)) },
        onClick = onClick,
        modifier = Modifier.heightIn(min = 48.dp),
        contentPadding = PaddingValues(horizontal = Spacing.lg),
        colors =
            MenuDefaults.itemColors(
                textColor = MaterialTheme.colorScheme.onSurface,
                leadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
    )
}

@Composable
private fun Group(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm).semantics { heading() },
    )
}

@Composable
private fun GroupDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
    )
}
