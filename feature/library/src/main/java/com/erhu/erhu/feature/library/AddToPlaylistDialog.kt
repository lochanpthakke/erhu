package com.erhu.erhu.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.erhu.erhu.core.common.locale.AppLocaleManager
import com.erhu.erhu.core.common.locale.LocaleFormatters
import com.erhu.erhu.core.designsystem.component.GlassAlertDialog
import com.erhu.erhu.core.designsystem.foundation.Spacing
import com.erhu.erhu.core.model.Song
import com.erhu.erhu.core.common.R as CommonR

/** Picks an existing playlist for [song], or creates a new one containing it. */
@Composable
fun AddToPlaylistDialog(
    song: Song,
    viewModel: PlaylistsViewModel,
    onDismiss: () -> Unit,
    onResult: ((String) -> Unit)? = null,
) {
    AddToPlaylistDialog(songs = listOf(song), viewModel = viewModel, onDismiss = onDismiss, onResult = onResult)
}

/**
 * Adds all of [songs] (e.g. a whole album) to an existing or a new playlist. [onResult], when given,
 * receives the outcome message ("Added to …", "Already in …") instead of the view model's user message.
 */
@Composable
fun AddToPlaylistDialog(
    songs: List<Song>,
    viewModel: PlaylistsViewModel,
    onDismiss: () -> Unit,
    onResult: ((String) -> Unit)? = null,
) {
    val playlists by viewModel.playlists.collectAsState()
    var creating by remember { mutableStateOf(false) }

    if (creating) {
        PlaylistNameDialog(
            title = stringResource(CommonR.string.playlist_new),
            onDismiss = onDismiss,
            onConfirm = { name ->
                viewModel.createPlaylist(name, thenAddSongs = songs, onResult = onResult)
                onDismiss()
            },
        )
        return
    }

    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(CommonR.string.playlist_add_to_title)) },
        text = {
            AddToPlaylistContent(
                playlists = playlists,
                songs = songs,
                viewModel = viewModel,
                onCreateNew = { creating = true },
                onDismiss = onDismiss,
                onResult = onResult,
            )
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(CommonR.string.action_cancel)) } },
    )
}

@Composable
private fun AddToPlaylistContent(
    playlists: List<com.erhu.erhu.core.model.Playlist>,
    songs: List<Song>,
    viewModel: PlaylistsViewModel,
    onCreateNew: () -> Unit,
    onDismiss: () -> Unit,
    onResult: ((String) -> Unit)?,
) {
    val locale = AppLocaleManager.activeLocale(LocalContext.current)
    Column {
        Text(
            text = stringResource(CommonR.string.playlist_new_ellipsis),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onCreateNew)
                    .padding(vertical = Spacing.md),
        )
        HorizontalDivider()
        if (playlists.isEmpty()) {
            Text(
                text = stringResource(CommonR.string.playlist_none_yet),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = Spacing.md),
            )
        } else {
            LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                items(playlists, key = { it.id }) { playlist ->
                    Text(
                        text =
                            stringResource(
                                CommonR.string.playlist_name_count,
                                playlist.name,
                                LocaleFormatters.formatInteger(playlist.songCount, locale),
                            ),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.addSongsToPlaylist(playlist, songs, onResult)
                                    onDismiss()
                                }.padding(vertical = Spacing.md),
                    )
                }
            }
        }
    }
}

@Composable
fun PlaylistNameDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(CommonR.string.playlist_name_label)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text(stringResource(CommonR.string.action_create)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(CommonR.string.action_cancel)) } },
    )
}
