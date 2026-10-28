package com.lochanpthakke.empee3.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.lochanpthakke.empee3.MainActivity
import com.lochanpthakke.empee3.R
import com.lochanpthakke.empee3.core.designsystem.artwork.ArtworkLoader
import com.lochanpthakke.empee3.core.model.PlaybackStatus
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.Locale

private const val ALBUM_ART_SIZE_PX = 400
private const val CONTROL_ICON_SIZE_DP = 26
private const val CONTROL_ICON_SIZE_LARGE_DP = 36

class GlassMusicWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<Preferences> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        provideContent {
            // Throttle position updates to 3-second buckets to eliminate high-frequency SystemUI re-inflation (flickering)
            val throttledPlaybackFlow =
                remember(context) {
                    context
                        .playbackController()
                        .playbackState
                        .map { state ->
                            val bucketedPosition = (state.positionMs / 3000) * 3000
                            state.copy(positionMs = bucketedPosition)
                        }.distinctUntilChanged()
                }
            val initialState = remember(context) { context.playbackController().playbackState.value }
            val playbackState by throttledPlaybackFlow.collectAsState(initial = initialState)
            val song = playbackState.currentSong
            val isFavorite by produceState(initialValue = false, song?.id) {
                if (song != null) {
                    context.libraryRepository().observeIsFavorite(song.id).collect { value = it }
                } else {
                    value = false
                }
            }
            val artwork by produceState<Bitmap?>(initialValue = null, song?.id) {
                if (song != null) {
                    val rawBitmap = ArtworkLoader.load(context, song.uri, song.artworkUri, ALBUM_ART_SIZE_PX)
                    val density = context.resources.displayMetrics.density
                    val radiusPx = 14f * density
                    value = rawBitmap?.toRoundedCorners(radiusPx)
                } else {
                    value = null
                }
            }

            WidgetContent(
                title = song?.title ?: "Nothing playing",
                artist = song?.artist.orEmpty(),
                artwork = artwork,
                isPlaying =
                    playbackState.status == PlaybackStatus.PLAYING ||
                        playbackState.status == PlaybackStatus.BUFFERING,
                isFavorite = isFavorite,
                hasSong = song != null,
                positionMs = playbackState.positionMs,
                durationMs = playbackState.durationMs,
            )
        }
    }

    @Composable
    private fun WidgetContent(
        title: String,
        artist: String,
        artwork: Bitmap?,
        isPlaying: Boolean,
        isFavorite: Boolean,
        hasSong: Boolean,
        positionMs: Long,
        durationMs: Long,
    ) {
        val whiteProvider = ColorProvider(day = Color.White, night = Color.White)
        val mutedWhiteProvider = ColorProvider(day = Color.White.copy(alpha = 0.7f), night = Color.White.copy(alpha = 0.7f))

        Box(
            modifier =
                GlanceModifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .background(ImageProvider(R.drawable.glass_widget_background))
                    .clickable(actionStartActivity<MainActivity>()),
        ) {
            Column(
                modifier =
                    GlanceModifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                // Header Row: Brand Title
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "empee3",
                        style = TextStyle(color = mutedWhiteProvider, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                    )
                }

                Spacer(modifier = GlanceModifier.defaultWeight())

                // Main Track Details Row: 72dp Artwork + Song Title, Artist & Like Heart Button
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier =
                            GlanceModifier
                                .size(72.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (artwork != null) {
                            Image(
                                provider = ImageProvider(artwork),
                                contentDescription = null,
                                modifier = GlanceModifier.size(72.dp),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Image(
                                provider = ImageProvider(R.drawable.ic_widget_thumb),
                                contentDescription = null,
                                modifier = GlanceModifier.size(36.dp),
                                colorFilter = ColorFilter.tint(mutedWhiteProvider),
                            )
                        }
                    }

                    Column(
                        modifier =
                            GlanceModifier
                                .defaultWeight()
                                .padding(start = 14.dp, end = 8.dp),
                    ) {
                        MarqueeTitle(
                            title = title,
                            textColor = Color.White,
                            modifier = GlanceModifier.fillMaxWidth(),
                        )

                        Text(
                            text = if (artist.isNotEmpty()) artist else "Unknown Artist",
                            style = TextStyle(color = mutedWhiteProvider, fontSize = 14.sp),
                            maxLines = 1,
                        )
                    }

                    if (hasSong) {
                        val favoriteIconRes = if (isFavorite) R.drawable.ic_widget_favorite else R.drawable.ic_widget_favorite_border
                        Image(
                            provider = ImageProvider(favoriteIconRes),
                            contentDescription = "Favorite",
                            modifier =
                                GlanceModifier
                                    .size(24.dp)
                                    .clickable(actionRunCallback<GlassToggleFavoriteAction>()),
                            colorFilter = ColorFilter.tint(whiteProvider),
                        )
                    }
                }

                Spacer(modifier = GlanceModifier.defaultWeight())

                // Progress Bar with 10-Segment Interactive Tap-to-Seek Overlay & mm:ss Timestamps
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

                    Text(
                        text = formatTime(positionMs),
                        style = TextStyle(color = mutedWhiteProvider, fontSize = 11.sp),
                        modifier = GlanceModifier.padding(end = 8.dp),
                    )

                    Box(
                        modifier = GlanceModifier.defaultWeight().height(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        LinearProgressIndicator(
                            progress = progress,
                            modifier = GlanceModifier.fillMaxWidth().height(3.dp),
                            color = whiteProvider,
                            backgroundColor = ColorProvider(day = Color.White.copy(alpha = 0.2f), night = Color.White.copy(alpha = 0.2f)),
                        )

                        // 10 invisible touch targets tightly aligned over the progress bar track
                        Row(
                            modifier = GlanceModifier.fillMaxWidth().height(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            for (i in 0 until 10) {
                                val targetPercent = (i + 0.5f) / 10f
                                Box(
                                    modifier =
                                        GlanceModifier
                                            .defaultWeight()
                                            .height(16.dp)
                                            .clickable(
                                                actionRunCallback<GlassSeekToPercentAction>(
                                                    actionParametersOf(
                                                        GlassSeekToPercentAction.KEY_PERCENT to targetPercent,
                                                    ),
                                                ),
                                            ),
                                ) {}
                            }
                        }
                    }

                    Text(
                        text = formatTime(durationMs),
                        style = TextStyle(color = mutedWhiteProvider, fontSize = 11.sp),
                        modifier = GlanceModifier.padding(start = 8.dp),
                    )
                }

                Spacer(modifier = GlanceModifier.defaultWeight())

                // Central Playback Controls Row (3 Buttons: Prev | Play/Pause | Next)
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_widget_skip_previous),
                        contentDescription = "Previous",
                        modifier =
                            GlanceModifier
                                .size(CONTROL_ICON_SIZE_DP.dp)
                                .clickable(actionRunCallback<GlassSkipPreviousAction>()),
                        colorFilter = ColorFilter.tint(whiteProvider),
                    )

                    Spacer(modifier = GlanceModifier.width(44.dp))

                    Image(
                        provider = ImageProvider(if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play),
                        contentDescription = "Play/Pause",
                        modifier =
                            GlanceModifier
                                .size(CONTROL_ICON_SIZE_LARGE_DP.dp)
                                .clickable(actionRunCallback<GlassPlayPauseAction>()),
                        colorFilter = ColorFilter.tint(whiteProvider),
                    )

                    Spacer(modifier = GlanceModifier.width(44.dp))

                    Image(
                        provider = ImageProvider(R.drawable.ic_widget_skip_next),
                        contentDescription = "Next",
                        modifier =
                            GlanceModifier
                                .size(CONTROL_ICON_SIZE_DP.dp)
                                .clickable(actionRunCallback<GlassSkipNextAction>()),
                        colorFilter = ColorFilter.tint(whiteProvider),
                    )
                }
            }
        }
    }

    @Composable
    private fun MarqueeTitle(
        title: String,
        textColor: Color,
        modifier: GlanceModifier,
    ) {
        val packageName = LocalContext.current.packageName
        AndroidRemoteViews(
            remoteViews =
                RemoteViews(packageName, R.layout.widget_title_marquee).apply {
                    setTextViewText(R.id.widget_title_text, title)
                    setTextColor(R.id.widget_title_text, textColor.toArgb())
                },
            modifier = modifier,
        )
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }

    private fun Bitmap.toRoundedCorners(radiusPx: Float): Bitmap {
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = Rect(0, 0, width, height)
        val rectF = RectF(rect)

        canvas.drawARGB(0, 0, 0, 0)
        canvas.drawRoundRect(rectF, radiusPx, radiusPx, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(this, rect, rect, paint)
        return output
    }
}
