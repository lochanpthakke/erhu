package com.erhu.erhu.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.erhu.erhu.core.common.artwork.ArtworkLoader
import com.erhu.erhu.core.model.PlaybackState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext

/** Stable state container: palette updates invalidate only readers, not the navigation tree. */
val LocalPlaybackArtworkColors = staticCompositionLocalOf<State<ArtworkColors?>> { mutableStateOf(null) }

internal fun playbackArtworkIdentity(state: PlaybackState): Pair<String, String?>? = state.currentSong?.let { it.uri to it.artworkUri }

/** One playback-only observer; uses the player's existing loader and dominant/accent extractor. */
@Composable
fun rememberPlaybackArtworkColors(
    playback: State<PlaybackState>,
    enabled: Boolean,
): State<ArtworkColors?> {
    val context = LocalContext.current.applicationContext
    return produceState<ArtworkColors?>(null, playback, enabled, context) {
        value = null
        if (enabled) {
            snapshotFlow { playbackArtworkIdentity(playback.value) }.distinctUntilChanged().collectLatest { identity ->
                // Never present a previous track's palette as the new track's artwork.
                value = null
                value =
                    identity?.let { (songUri, artUri) ->
                        val bitmap = ArtworkLoader.peek(songUri, artUri) ?: ArtworkLoader.load(context, songUri, artUri, 64)
                        bitmap?.let { withContext(Dispatchers.Default) { DominantColorExtractor.extractColors(it) } }
                    }
            }
        }
    }
}
