package com.erhu.erhu.feature.library.components

import android.view.TextureView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/** Preview-only player. Owns no audio focus, media session, music queue, or persisted playback state. */
@Composable
fun OnboardingPreviewVideo(
    uri: String,
    active: Boolean,
    loop: Boolean,
    onEnded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val ended by rememberUpdatedState(onEnded)
    val playing by rememberUpdatedState(active)
    val player =
        remember(uri) {
            ExoPlayer.Builder(context.applicationContext).build().apply {
                volume = 0f
                setAudioAttributes(AudioAttributes.DEFAULT, false)
                trackSelectionParameters = trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true).build()
                setMediaItem(MediaItem.fromUri(uri))
                prepare()
            }
        }
    var firstFrame by remember(player) { mutableStateOf(false) }
    DisposableEffect(player) {
        val listener =
            object : Player.Listener {
                override fun onRenderedFirstFrame() {
                    firstFrame = true
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED && playing) ended()
                }
            }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    LaunchedEffect(player, active, loop) {
        player.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        if (active) {
            player.seekTo(0)
            player.play()
        } else {
            player.pause()
        }
    }
    AndroidView(
        factory = { ctx -> TextureView(ctx) },
        update = { view -> player.setVideoTextureView(view) },
        onRelease = { view -> player.clearVideoTextureView(view) },
        modifier = modifier.fillMaxSize().alpha(if (firstFrame) 1f else 0.01f),
    )
}
