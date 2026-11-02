// Published Android/Glance/WorkManager identity retained for upgrade compatibility.
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
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
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
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.erhu.erhu.MainActivity
import com.erhu.erhu.R
import com.erhu.erhu.core.designsystem.artwork.ArtworkLoader
import com.erhu.erhu.core.model.PlaybackStatus
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.Locale

private const val ALBUM_ART_SIZE_PX = 400

class GlassMusicWidget : GlanceAppWidget() {
    override val sizeMode = androidx.glance.appwidget.SizeMode.Exact
    override val previewSizeMode =
        androidx.glance.appwidget.SizeMode.Responsive(
            setOf(
                androidx.compose.ui.unit
                    .DpSize(220.dp, 140.dp),
                androidx.compose.ui.unit
                    .DpSize(300.dp, 200.dp),
            ),
        )

    override suspend fun providePreview(
        context: Context,
        widgetCategory: Int,
    ) {
        provideContent {
            WidgetContent(
                title = "Nothing playing",
                artist = "Choose music in erhu",
                artwork = null,
                isPlaying = false,
                isFavorite = false,
                hasSong = false,
                positionMs = 0L,
                durationMs = 0L,
            )
        }
    }

    override fun onCompositionError(
        context: Context,
        glanceId: GlanceId,
        appWidgetId: Int,
        throwable: Throwable,
    ) {
        android.util.Log.e("ErhuWidget", "GlassMusicWidget rendering failed: $appWidgetId", throwable)
        super.onCompositionError(context, glanceId, appWidgetId, throwable)
    }

    override val stateDefinition: GlanceStateDefinition<Preferences> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        provideContent {
            // Throttle position updates to 15-second buckets to eliminate high-frequency SystemUI re-inflation (flickering)
            val throttledPlaybackFlow =
                remember(context) {
                    context
                        .playbackController()
                        .playbackState
                        .map { state ->
                            val bucketedPosition = (state.positionMs / 15000) * 15000
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
        val compact = LocalSize.current.height < 180.dp
        val white = ColorProvider(day = Color.White, night = Color.White)
        val muted = ColorProvider(day = Color.White.copy(alpha = 0.7f), night = Color.White.copy(alpha = 0.7f))
        val artSide = if (compact) 48.dp else 64.dp
        Box(
            GlanceModifier
                .fillMaxWidth()
                .fillMaxHeight()
                .background(
                    ImageProvider(R.drawable.glass_widget_background),
                ).appWidgetBackground()
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            Column(GlanceModifier.fillMaxWidth().fillMaxHeight().padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text("erhu", style = TextStyle(color = muted, fontSize = 12.sp))
                Spacer(GlanceModifier.defaultWeight())
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(GlanceModifier.size(artSide), contentAlignment = Alignment.Center) {
                        if (artwork !=
                            null
                        ) {
                            Image(ImageProvider(artwork), null, GlanceModifier.size(artSide), contentScale = ContentScale.Crop)
                        } else {
                            Image(
                                ImageProvider(R.drawable.ic_widget_thumb),
                                null,
                                GlanceModifier.size(32.dp),
                                colorFilter = ColorFilter.tint(muted),
                            )
                        }
                    }
                    Column(GlanceModifier.defaultWeight().padding(start = 10.dp)) {
                        MarqueeTitle(title, Color.White, GlanceModifier.fillMaxWidth())
                        Text(artist.ifEmpty { "Choose music in erhu" }, style = TextStyle(color = muted, fontSize = 13.sp), maxLines = 1)
                    }
                    if (hasSong) {
                        WidgetControl(
                            if (isFavorite) R.drawable.ic_widget_favorite else R.drawable.ic_widget_favorite_border,
                            "Favorite",
                            actionRunCallback<GlassToggleFavoriteAction>(),
                            white,
                        )
                    }
                }
                Spacer(GlanceModifier.defaultWeight())
                if (!compact) {
                    val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
                    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(formatTime(positionMs), style = TextStyle(color = muted, fontSize = 11.sp))
                        Box(GlanceModifier.defaultWeight().height(20.dp).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                            LinearProgressIndicator(
                                progress,
                                GlanceModifier.fillMaxWidth().height(2.dp),
                                color = white,
                                backgroundColor = muted,
                            )
                            Row(GlanceModifier.fillMaxWidth().height(20.dp)) {
                                repeat(10) { i ->
                                    Box(
                                        GlanceModifier.defaultWeight().height(20.dp).clickable(
                                            actionRunCallback<GlassSeekToPercentAction>(
                                                actionParametersOf(
                                                    GlassSeekToPercentAction.KEY_PERCENT to (i + 0.5f) / 10f,
                                                ),
                                            ),
                                        ),
                                    ) {}
                                }
                            }
                        }
                        Text(formatTime(durationMs), style = TextStyle(color = muted, fontSize = 11.sp))
                    }
                }
                Spacer(GlanceModifier.defaultWeight())
                Row(GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    WidgetControl(
                        R.drawable.ic_widget_skip_previous,
                        "Previous",
                        (if (hasSong) actionRunCallback<GlassSkipPreviousAction>() else actionStartActivity<MainActivity>()),
                        white,
                    )
                    Spacer(GlanceModifier.width(12.dp))
                    WidgetControl(
                        if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
                        if (isPlaying) "Pause" else "Play",
                        (if (hasSong) actionRunCallback<GlassPlayPauseAction>() else actionStartActivity<MainActivity>()),
                        white,
                        iconSize = 36,
                    )
                    Spacer(GlanceModifier.width(12.dp))
                    WidgetControl(
                        R.drawable.ic_widget_skip_next,
                        "Next",
                        (if (hasSong) actionRunCallback<GlassSkipNextAction>() else actionStartActivity<MainActivity>()),
                        white,
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
                    setInt(R.id.widget_title_text, "setMaxLines", 1)
                    setTextColor(R.id.widget_title_text, textColor.toArgb())
                },
            modifier = modifier.height(24.dp),
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
