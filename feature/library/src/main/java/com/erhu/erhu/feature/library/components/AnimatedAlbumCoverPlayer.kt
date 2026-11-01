package com.erhu.erhu.feature.library.components

import android.content.Context
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.TextureView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import com.erhu.erhu.core.database.repository.AnimatedArtworkRepository
import com.erhu.erhu.core.model.AnimatedArtOrientation
import com.erhu.erhu.core.model.AnimatedArtStatus
import com.erhu.erhu.core.network.apple.FragmentedMp4Timeline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/*
 * Buffering. Covers are fragmented MP4s (Apple's: ~3 s fragments), for which ExoPlayer's buffered
 * position runs ahead of what the decoder can actually read. A read-ahead time limit shorter than
 * the file (tried 3-5 s) then stops loading while the decoder starves: the cover played a few
 * seconds, or up to the loop point, and stayed buffering for good. So time never stops loading
 * (ExoPlayer's 50 s, longer than any cover) and memory is bounded by bytes instead, which are freed
 * as frames play. The original out-of-memory crash came from three players each buffering a 58 MB
 * cover; now there is one cover player at a time (CoverVideoHost, the album header lets go while
 * Now Playing covers it) and covers are downloaded at phone size (~1080 px).
 */
private const val TARGET_BUFFER_BYTES = 64 * 1024 * 1024

/** The lowest alpha the video is drawn at (see LoopingCoverVideo): invisible over the still cover, but drawn. */
private const val MIN_DRAWN_ALPHA = 0.02f

/**
 * One looping, muted cover video and the views that show it in turn. Now Playing keeps one for its
 * current card, so moving to another track of the same album hands the running video to the new
 * card instead of restarting it; a new file is loaded only when the album changes. The player is
 * built on first use and paused when no view shows it.
 */
class CoverVideoHost(
    private val context: Context,
) {
    private var built: ExoPlayer? = null
    private var path: String? = null
    private var views = 0
    private val main = Handler(Looper.getMainLooper())

    val player: ExoPlayer
        get() = built ?: buildCoverPlayer(context).also { built = it }

    /** Loads [filePath] unless it is already the one playing. */
    fun show(filePath: String) {
        if (filePath == path) return
        path = filePath
        player.setMediaItem(MediaItem.fromUri(filePath))
        player.prepare()
    }

    internal fun attached() {
        views++
    }

    /** Pauses once no view is left (checked after the frame, when a new card may have taken over). */
    internal fun detached() {
        views--
        main.post { if (views == 0) built?.pause() }
    }

    fun release() {
        main.removeCallbacksAndMessages(null)
        built?.release()
        built = null
        path = null
    }
}

@Composable
fun rememberCoverVideoHost(): CoverVideoHost {
    val context = LocalContext.current
    val host = remember { CoverVideoHost(context.applicationContext) }
    DisposableEffect(host) { onDispose { host.release() } }
    return host
}

private fun buildCoverPlayer(context: Context): ExoPlayer =
    ExoPlayer
        .Builder(context)
        .setLoadControl(
            DefaultLoadControl
                .Builder()
                .setTargetBufferBytes(TARGET_BUFFER_BYTES)
                .setPrioritizeTimeOverSizeThresholds(false)
                .build(),
        ).build()
        .apply {
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ONE
            setAudioAttributes(AudioAttributes.DEFAULT, false)
            trackSelectionParameters =
                trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                    .build()
        }

/**
 * The album's still cover ([staticCover]) with its animated cover, a muted looping video, faded in
 * over it once the first frame is drawn. No player exists at all when there is no video file.
 * [host]: a shared player (see [CoverVideoHost]); without one this cover has its own.
 */
@Composable
fun AnimatedAlbumCoverPlayer(
    filePath: String?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    host: CoverVideoHost? = null,
    staticCover: @Composable () -> Unit,
) {
    val video = filePath?.takeIf { it.isNotBlank() && File(it).exists() }
    Box(modifier = modifier) {
        staticCover()
        if (video != null) LoopingCoverVideo(host ?: rememberCoverVideoHost(), video, isPlaying)
    }
}

@Composable
private fun LoopingCoverVideo(
    host: CoverVideoHost,
    filePath: String,
    isPlaying: Boolean,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val playing by rememberUpdatedState(isPlaying)
    var isFirstFrameRendered by remember { mutableStateOf(false) }
    var videoSize by remember { mutableStateOf(VideoSize.UNKNOWN) }
    val exoPlayer = host.player

    DisposableEffect(host) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_START) {
                    if (playing) exoPlayer.play()
                } else if (event == Lifecycle.Event.ON_STOP) {
                    exoPlayer.pause()
                }
            }
        val listener =
            object : Player.Listener {
                override fun onRenderedFirstFrame() {
                    isFirstFrameRendered = true
                }

                override fun onVideoSizeChanged(size: VideoSize) {
                    videoSize = size
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        exoPlayer.addListener(listener)
        if (exoPlayer.videoSize != VideoSize.UNKNOWN) videoSize = exoPlayer.videoSize
        host.attached()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.removeListener(listener)
            host.detached()
        }
    }

    LaunchedEffect(filePath) {
        // Covers downloaded before the fetcher did this still start at 10 s: fixed once, in place.
        withContext(Dispatchers.IO) { runCatching { FragmentedMp4Timeline.startAtZero(File(filePath)) } }
        host.show(filePath)
        if (playing) exoPlayer.play()
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) exoPlayer.play() else exoPlayer.pause()
    }

    val videoAlpha by animateFloatAsState(
        targetValue = if (isFirstFrameRendered) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "VideoAlpha",
    )

    // A TextureView, not PlayerView's default SurfaceView: a SurfaceView is a separate window layer
    // that Compose cannot clip (rounded corners), fade or move with the card while the player opens,
    // closes or swipes. The video fills the view centre-cropped, never stretched.
    AndroidView(
        factory = { ctx ->
            TextureView(ctx).apply {
                // Transparent until the first frame: the still cover shows through.
                isOpaque = false
                // The newest view takes the shared video once its surface exists; the old one lets go.
                surfaceTextureListener = CoverSurface(exoPlayer)
                // Re-crop when the view's size changes (attached once, here).
                addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ -> (view as TextureView).centerCrop() }
            }
        },
        update = { view ->
            view.tag = videoSize
            view.centerCrop()
        },
        onRelease = { view -> (view.surfaceTextureListener as? CoverSurface)?.detach() },
        // Never exactly 0: a layer at alpha 0 is not drawn, and a TextureView creates its surface and
        // takes the decoder's frames only when drawn. The still cover underneath shows meanwhile.
        modifier = Modifier.fillMaxSize().alpha(videoAlpha.coerceAtLeast(MIN_DRAWN_ALPHA)),
    )
}

/**
 * Hands a TextureView's surface to the cover player when it exists, and takes it back when it goes.
 * Only the surface that is still current is cleared, so a card taking over keeps the video.
 */
private class CoverSurface(
    private val player: ExoPlayer,
) : TextureView.SurfaceTextureListener {
    private var surface: Surface? = null

    override fun onSurfaceTextureAvailable(
        texture: SurfaceTexture,
        width: Int,
        height: Int,
    ) {
        val created = Surface(texture)
        surface = created
        player.setVideoSurface(created)
    }

    override fun onSurfaceTextureSizeChanged(
        texture: SurfaceTexture,
        width: Int,
        height: Int,
    ) = Unit

    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        detach()
        return true
    }

    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit

    /** Only clears the player's surface if it is still this one (another card may have taken over). */
    fun detach() {
        val current = surface ?: return
        surface = null
        player.clearVideoSurface(current)
        current.release()
    }
}

/** Scales the video ([TextureView.getTag], a [VideoSize]) to cover the whole view, cropping the overflow equally. */
private fun TextureView.centerCrop() {
    val video = tag as? VideoSize ?: return
    val viewW = width.toFloat()
    val viewH = height.toFloat()
    if (video.width == 0 || video.height == 0 || viewW == 0f || viewH == 0f) return
    val videoW = video.width * video.pixelWidthHeightRatio
    val videoH = video.height.toFloat()
    // TextureView stretches the frame to the view; undo that, then scale up to cover.
    val cover = maxOf(viewW / videoW, viewH / videoH)
    setTransform(Matrix().apply { setScale(videoW * cover / viewW, videoH * cover / viewH, viewW / 2f, viewH / 2f) })
}

/**
 * The downloaded animated cover of album [albumId] in the preferred orientation (the other one if
 * only that exists), or null. Flows are remembered: callers recompose during motion.
 */
@Composable
fun rememberAnimatedArtworkPath(
    albumId: Long?,
    repository: AnimatedArtworkRepository,
): String? {
    if (albumId == null) return null
    val artwork by remember(repository, albumId) { repository.observeAnimatedArtwork(albumId) }.collectAsState(initial = null)
    val orientation by remember(repository) { repository.observePreferredOrientation() }
        .collectAsState(initial = AnimatedArtOrientation.SQUARE_1_1)
    val current = artwork ?: return null
    if (current.status != AnimatedArtStatus.AVAILABLE) return null
    return when (orientation) {
        AnimatedArtOrientation.SQUARE_1_1 -> current.squareFilePath ?: current.tallFilePath
        AnimatedArtOrientation.TALL_3_4 -> current.tallFilePath ?: current.squareFilePath
    }
}
