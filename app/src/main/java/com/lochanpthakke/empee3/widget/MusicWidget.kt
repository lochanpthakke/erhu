// Published Android/Glance/WorkManager identity retained for upgrade compatibility.
package com.lochanpthakke.empee3.widget

import android.content.Context
import android.graphics.Bitmap
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
import com.erhu.erhu.MainActivity
import com.erhu.erhu.R
import com.erhu.erhu.core.designsystem.artwork.ArtworkLoader
import com.erhu.erhu.core.model.PlaybackStatus
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.Locale

private const val ALBUM_ART_SIZE_PX = 400
private const val CONTROL_ICON_SIZE_DP = 24
private const val CONTROL_ICON_SIZE_LARGE_DP = 36

class MusicWidget : GlanceAppWidget() {
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
                    value = ArtworkLoader.load(context, song.uri, song.artworkUri, ALBUM_ART_SIZE_PX)
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
        val extractedColors =
            artwork?.let {
                com.erhu.erhu.core.designsystem.theme.DominantColorExtractor
                    .extractColors(it)
            }
        val rawAccent = extractedColors?.accentColor ?: Color(0xFFD2B48C)
        val accentProvider = ColorProvider(day = rawAccent, night = rawAccent)
        val mutedAccentProvider = ColorProvider(day = rawAccent.copy(alpha = 0.7f), night = rawAccent.copy(alpha = 0.7f))

        Box(
            modifier =
                GlanceModifier
                    .fillMaxWidth()
                    .background(Color(0xFF0B0B0B))
                    .clickable(actionStartActivity<MainActivity>()),
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End,
            ) {
                Box(modifier = GlanceModifier.width(210.dp).fillMaxHeight()) {
                    if (artwork != null) {
                        Image(
                            provider = ImageProvider(artwork),
                            contentDescription = null,
                            modifier = GlanceModifier.fillMaxWidth().fillMaxHeight(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    Image(
                        provider = ImageProvider(R.drawable.widget_cinematic_gradient),
                        contentDescription = null,
                        modifier = GlanceModifier.fillMaxWidth().fillMaxHeight(),
                        contentScale = ContentScale.FillBounds,
                    )
                }
            }

            Column(
                modifier = GlanceModifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 14.dp),
            ) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth().padding(bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "erhu",
                        style = TextStyle(color = mutedAccentProvider, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                    )

                    Spacer(modifier = GlanceModifier.defaultWeight())

                    val favoriteIconRes = if (isFavorite) R.drawable.ic_widget_favorite else R.drawable.ic_widget_favorite_border
                    if (hasSong) {
                        Image(
                            provider = ImageProvider(favoriteIconRes),
                            contentDescription = "Favorite",
                            modifier =
                                GlanceModifier
                                    .size(CONTROL_ICON_SIZE_DP.dp)
                                    .clickable(actionRunCallback<ToggleFavoriteAction>()),
                            colorFilter = androidx.glance.ColorFilter.tint(accentProvider),
                        )
                    }
                }

                MarqueeTitle(
                    title = title,
                    textColor = rawAccent,
                    modifier = GlanceModifier.fillMaxWidth().padding(end = 32.dp),
                )

                Text(
                    text = artist,
                    modifier = GlanceModifier.fillMaxWidth().padding(bottom = 12.dp, end = 32.dp),
                    style = TextStyle(color = mutedAccentProvider, fontSize = 14.sp),
                    maxLines = 1,
                )

                // Controls Row (Left-aligned: Prev | Play/Pause | Next)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = GlanceModifier.padding(bottom = 12.dp),
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_widget_skip_previous),
                        contentDescription = "Previous",
                        modifier =
                            GlanceModifier
                                .size(CONTROL_ICON_SIZE_DP.dp)
                                .clickable(actionRunCallback<SkipPreviousAction>()),
                        colorFilter = androidx.glance.ColorFilter.tint(accentProvider),
                    )

                    Spacer(modifier = GlanceModifier.width(36.dp))

                    Image(
                        provider = ImageProvider(if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play),
                        contentDescription = "Play/Pause",
                        modifier =
                            GlanceModifier
                                .size(CONTROL_ICON_SIZE_LARGE_DP.dp)
                                .clickable(actionRunCallback<PlayPauseAction>()),
                        colorFilter = androidx.glance.ColorFilter.tint(accentProvider),
                    )

                    Spacer(modifier = GlanceModifier.width(36.dp))

                    Image(
                        provider = ImageProvider(R.drawable.ic_widget_skip_next),
                        contentDescription = "Next",
                        modifier =
                            GlanceModifier
                                .size(CONTROL_ICON_SIZE_DP.dp)
                                .clickable(actionRunCallback<SkipNextAction>()),
                        colorFilter = androidx.glance.ColorFilter.tint(accentProvider),
                    )
                }

                // Progress Bar with 10-Segment Interactive Tap-to-Seek Overlay
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

                    Text(
                        text = formatTime(positionMs),
                        style = TextStyle(color = mutedAccentProvider, fontSize = 12.sp),
                        modifier = GlanceModifier.padding(end = 12.dp),
                    )

                    Box(
                        modifier = GlanceModifier.defaultWeight().height(20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        LinearProgressIndicator(
                            progress = progress,
                            modifier = GlanceModifier.fillMaxWidth().height(2.dp),
                            color = accentProvider,
                            backgroundColor = ColorProvider(day = rawAccent.copy(alpha = 0.2f), night = rawAccent.copy(alpha = 0.2f)),
                        )

                        // 10 invisible touch targets tightly aligned over the 8dp progress bar track
                        Row(
                            modifier = GlanceModifier.fillMaxWidth().height(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            for (i in 0 until 10) {
                                val targetPercent = (i + 0.5f) / 10f
                                Box(
                                    modifier =
                                        GlanceModifier
                                            .defaultWeight()
                                            .height(8.dp)
                                            .clickable(
                                                actionRunCallback<SeekToPercentAction>(
                                                    actionParametersOf(
                                                        SeekToPercentAction.KEY_PERCENT to targetPercent,
                                                    ),
                                                ),
                                            ),
                                ) {}
                            }
                        }
                    }

                    Text(
                        text = formatTime(durationMs),
                        style = TextStyle(color = mutedAccentProvider, fontSize = 12.sp),
                        modifier = GlanceModifier.padding(start = 12.dp),
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
        return String.format(java.util.Locale.getDefault(), "%d:%02d", minutes, seconds)
    }
}
