package com.lochanpthakke.empee3.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lochanpthakke.empee3.core.designsystem.artwork.GlassArtworkBackdrop
import com.lochanpthakke.empee3.core.designsystem.artwork.SongArtwork
import com.lochanpthakke.empee3.core.designsystem.theme.EmPee3Glass
import com.lochanpthakke.empee3.core.designsystem.theme.glassPanel
import com.lochanpthakke.empee3.core.model.PlaybackState
import com.lochanpthakke.empee3.core.model.PlaybackStatus
import com.lochanpthakke.empee3.core.model.RepeatMode
import com.lochanpthakke.empee3.core.model.Song
import com.lochanpthakke.empee3.core.playback.repository.PlaybackRepository
import kotlinx.coroutines.launch
import java.util.Locale

private const val MS_PER_SECOND = 1000
private const val SECONDS_PER_MINUTE = 60
private const val INACTIVE_ALPHA = 0.6f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod", "LongParameterList")
fun NowPlayingScreen(
    playbackRepository: PlaybackRepository,
    isFavorite: Boolean,
    lyrics: String?,
    onBackClick: () -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    val playbackState by playbackRepository.playbackState.collectAsState()
    val song = playbackState.currentSong
    val isPlaying = playbackState.status == PlaybackStatus.PLAYING

    var showLyrics by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }

    var isDraggingSlider by remember { mutableStateOf(false) }
    var dragPositionMs by remember { mutableFloatStateOf(0f) }
    val glass = EmPee3Glass.isActive

    Box(modifier = modifier) {
        GlassArtworkBackdrop(song = song)
        Scaffold(
            topBar = {
                TopAppBar(
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = EmPee3Glass.containerColor(MaterialTheme.colorScheme.surface),
                        ),
                    title = { Text("Now Playing") },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                            )
                        }
                    },
                )
            },
        ) { innerPadding ->
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
            ) {
                if (song == null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Nothing is playing. Pick a song from your library.",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                } else {
                    val totalDurationMs =
                        (if (playbackState.durationMs > 0) playbackState.durationMs else song.durationMs).coerceAtLeast(1L)
                    val displayPositionMs =
                        if (isDraggingSlider) {
                            dragPositionMs.toLong()
                        } else {
                            playbackState.positionMs
                        }.coerceIn(0L, totalDurationMs)

                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(horizontal = 24.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        SongArtwork(
                            song = song,
                            contentDescription = "Album artwork",
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .glassPanel(RoundedCornerShape(16.dp))
                                    .clip(RoundedCornerShape(16.dp)),
                        )

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = song.title,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${song.artist} — ${song.album}",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )
                        }

                        Column(modifier = Modifier.fillMaxWidth()) {
                            Slider(
                                value = displayPositionMs.toFloat(),
                                onValueChange = {
                                    isDraggingSlider = true
                                    dragPositionMs = it
                                },
                                onValueChangeFinished = {
                                    playbackRepository.seekTo(dragPositionMs.toLong())
                                    isDraggingSlider = false
                                },
                                valueRange = 0f..totalDurationMs.toFloat(),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(text = formatMs(displayPositionMs), style = MaterialTheme.typography.bodySmall)
                                if (playbackState.queue.size > 1) {
                                    Text(
                                        text = "${playbackState.currentIndex + 1} / ${playbackState.queue.size}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(text = formatMs(totalDurationMs), style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (glass) {
                                            Modifier
                                                .glassPanel(MaterialTheme.shapes.large)
                                                .padding(vertical = 8.dp)
                                        } else {
                                            Modifier
                                        },
                                    ),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ToggleIconButton(
                                active = playbackState.shuffleModeEnabled,
                                onClick = { playbackRepository.setShuffleModeEnabled(!playbackState.shuffleModeEnabled) },
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shuffle,
                                    contentDescription = if (playbackState.shuffleModeEnabled) "Shuffle on" else "Shuffle off",
                                )
                            }

                            IconButton(onClick = { playbackRepository.skipPrevious() }) {
                                Icon(
                                    imageVector = Icons.Default.SkipPrevious,
                                    contentDescription = "Previous",
                                    modifier = Modifier.size(36.dp),
                                )
                            }

                            FilledIconButton(
                                onClick = { if (isPlaying) playbackRepository.pause() else playbackRepository.play() },
                                modifier = Modifier.size(64.dp),
                                shape = CircleShape,
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    modifier = Modifier.size(40.dp),
                                )
                            }

                            IconButton(
                                onClick = { playbackRepository.skipNext() },
                                enabled = hasNext(playbackState),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipNext,
                                    contentDescription = "Next",
                                    modifier = Modifier.size(36.dp),
                                )
                            }

                            ToggleIconButton(
                                active = playbackState.repeatMode != RepeatMode.OFF,
                                onClick = { playbackRepository.setRepeatMode(playbackState.repeatMode.next()) },
                            ) {
                                Icon(
                                    imageVector =
                                        if (playbackState.repeatMode ==
                                            RepeatMode.ONE
                                        ) {
                                            Icons.Default.RepeatOne
                                        } else {
                                            Icons.Default.Repeat
                                        },
                                    contentDescription =
                                        when (playbackState.repeatMode) {
                                            RepeatMode.OFF -> "Repeat off"
                                            RepeatMode.ALL -> "Repeat all"
                                            RepeatMode.ONE -> "Repeat one"
                                        },
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(onClick = { onToggleFavorite(song) }) {
                                Icon(
                                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            IconButton(onClick = { showLyrics = true }) {
                                Icon(imageVector = Icons.Default.Lyrics, contentDescription = "Lyrics")
                            }
                            IconButton(onClick = { showQueue = true }) {
                                Icon(imageVector = Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Queue")
                            }
                            IconButton(onClick = { onAddToPlaylist(song) }) {
                                Icon(imageVector = Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = "Add to playlist")
                            }
                        }
                    }
                }

                if (showLyrics && song != null) {
                    LyricsOverlay(lyrics = lyrics, onClose = { showLyrics = false })
                }
            }
        }
    }

    if (showQueue) {
        QueueSheet(
            playbackState = playbackState,
            playbackRepository = playbackRepository,
            onDismiss = { showQueue = false },
        )
    }
}

@Composable
private fun ToggleIconButton(
    active: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    IconButton(onClick = onClick) {
        Box(
            modifier =
                if (active) {
                    Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(6.dp)
                } else {
                    Modifier.padding(6.dp)
                },
        ) {
            CompositionLocalProvider(
                LocalContentColor provides
                    if (active) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = INACTIVE_ALPHA)
                    },
            ) {
                content()
            }
        }
    }
}

@Composable
private fun LyricsOverlay(
    lyrics: String?,
    onClose: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Lyrics", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) {
                Icon(imageVector = Icons.Default.Close, contentDescription = "Close lyrics")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = lyrics ?: "This file has no embedded lyrics.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = if (lyrics == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongMethod")
private fun QueueSheet(
    playbackState: PlaybackState,
    playbackRepository: PlaybackRepository,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val queue = playbackState.queue
    LaunchedEffect(Unit) {
        if (playbackState.currentIndex > 0) listState.scrollToItem(playbackState.currentIndex)
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = "Queue (${queue.size})",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (queue.isEmpty()) {
            Text(
                text = "The queue is empty.",
                modifier = Modifier.padding(16.dp),
            )
        } else {
            LazyColumn(state = listState) {
                // Keys must be unique even if the same song is queued twice.
                itemsIndexed(queue, key = { index, song -> "${song.id}_$index" }) { index, queuedSong ->
                    SongRow(
                        song = queuedSong,
                        isHighlighted = index == playbackState.currentIndex,
                        onClick = { playbackRepository.skipToQueueItem(index) },
                    ) {
                        IconButton(
                            onClick = { scope.launch { playbackRepository.reorder(index, index - 1) } },
                            enabled = index > 0,
                        ) {
                            Icon(imageVector = Icons.Default.KeyboardArrowUp, contentDescription = "Move up")
                        }
                        IconButton(
                            onClick = { scope.launch { playbackRepository.reorder(index, index + 1) } },
                            enabled = index < queue.lastIndex,
                        ) {
                            Icon(imageVector = Icons.Default.KeyboardArrowDown, contentDescription = "Move down")
                        }
                        IconButton(onClick = { scope.launch { playbackRepository.removeFromQueue(index) } }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Remove from queue")
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

private fun hasNext(state: PlaybackState): Boolean =
    state.queue.size > 1 && (state.shuffleModeEnabled || state.repeatMode == RepeatMode.ALL || state.currentIndex < state.queue.lastIndex)

private fun RepeatMode.next(): RepeatMode =
    when (this) {
        RepeatMode.OFF -> RepeatMode.ALL
        RepeatMode.ALL -> RepeatMode.ONE
        RepeatMode.ONE -> RepeatMode.OFF
    }

private fun formatMs(ms: Long): String {
    val totalSeconds = ms / MS_PER_SECOND
    val minutes = totalSeconds / SECONDS_PER_MINUTE
    val seconds = totalSeconds % SECONDS_PER_MINUTE
    return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
}
