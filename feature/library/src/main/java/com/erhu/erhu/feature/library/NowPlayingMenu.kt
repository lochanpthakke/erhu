package com.erhu.erhu.feature.library

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.erhu.erhu.core.common.R
import com.erhu.erhu.core.designsystem.component.GlassMenu
import com.erhu.erhu.core.designsystem.component.GlassMenuAnchor
import com.erhu.erhu.core.designsystem.component.GlassMenuDivider
import com.erhu.erhu.core.designsystem.component.GlassMenuItem
import com.erhu.erhu.core.designsystem.component.GlassMenuSection
import com.erhu.erhu.core.designsystem.component.PressIconButton
import com.erhu.erhu.core.designsystem.component.glassMenuTrigger

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
    GlassMenuAnchor {
        PressIconButton(modifier = Modifier.glassMenuTrigger(), onClick = { open = true }, enabled = enabled) {
            Icon(imageVector = Icons.Default.MoreVert, contentDescription = stringResource(R.string.player_cd_more_options))
        }
        GlassMenu(
            label = stringResource(R.string.player_menu_label),
            expanded = open,
            onDismissRequest = { open = false },
        ) {
            val close: (() -> Unit) -> () -> Unit = { action ->
                {
                    open = false
                    action()
                }
            }
            Group(stringResource(R.string.player_menu_group_track))
            MenuItem(stringResource(R.string.player_menu_music_info), Icons.Outlined.Info, close(actions.onMusicInfo))
            MenuItem(stringResource(R.string.player_menu_audio_details), Icons.Outlined.Tune, close(actions.onAudioDetails))
            GroupDivider()
            Group(stringResource(R.string.player_menu_group_library))
            MenuItem(stringResource(R.string.player_menu_bookmark_moment), Icons.Default.BookmarkAdd, close(actions.onAddBookmark))
            MenuItem(
                stringResource(if (loopOn) R.string.player_menu_bookmarks_ab_loop_on else R.string.player_menu_bookmarks_ab_loop),
                Icons.Default.Bookmarks,
                close(actions.onBookmarks),
            )
            MenuItem(
                stringResource(R.string.song_menu_add_to_playlist),
                Icons.AutoMirrored.Filled.PlaylistAdd,
                close(actions.onAddToPlaylist),
            )
            actions.onToggleHidden?.let { toggle ->
                if (isHidden) {
                    MenuItem(stringResource(R.string.player_menu_show_smart_playlists), Icons.Default.Visibility, close(toggle))
                } else {
                    MenuItem(stringResource(R.string.player_menu_hide_smart_playlists), Icons.Default.VisibilityOff, close(toggle))
                }
            }
            GroupDivider()
            Group(stringResource(R.string.player_menu_group_playback_display))
            actions.onStartRadio?.let { MenuItem(stringResource(R.string.song_menu_start_radio), Icons.Default.Radio, close(it)) }
            actions.onRefreshRadio?.let { MenuItem(stringResource(R.string.player_menu_new_radio_picks), Icons.Default.Refresh, close(it)) }
            MenuItem(stringResource(R.string.player_menu_queue), Icons.AutoMirrored.Filled.QueueMusic, close(actions.onQueue))
            MenuItem(stringResource(R.string.player_menu_visualizer), Icons.Default.GraphicEq, close(actions.onVisualizer))
            MenuItem(stringResource(R.string.nav_equalizer), Icons.Default.Equalizer, close(actions.onEqualizer))
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
