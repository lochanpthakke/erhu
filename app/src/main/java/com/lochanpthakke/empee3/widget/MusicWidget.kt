// Published Android/Glance/WorkManager identity retained for upgrade compatibility.
package com.lochanpthakke.empee3.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
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
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.erhu.erhu.MainActivity
import com.erhu.erhu.R
import com.erhu.erhu.core.common.locale.AppLocaleManager
import com.erhu.erhu.core.common.locale.LocaleFormatters
import com.erhu.erhu.core.designsystem.artwork.ArtworkLoader
import com.erhu.erhu.core.designsystem.theme.DominantColorExtractor
import com.erhu.erhu.core.model.PlaybackStatus
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.Locale

private const val ALBUM_ART_SIZE_PX = 400
private const val MAX_ARTWORK_WIDTH_FRACTION = 0.6f

class MusicWidget : GlanceAppWidget() {
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
        val localized =
            com.erhu.erhu.core.common.locale.AppLocaleManager
                .localizedContext(context)
        provideContent {
            WidgetContent(
                title = localized.getString(R.string.widget_idle_title),
                artist = localized.getString(R.string.widget_idle_hint),
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
        android.util.Log.e("ErhuWidget", "MusicWidget rendering failed: $appWidgetId", throwable)
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
                    value = ArtworkLoader.load(context, song.uri, song.artworkUri, ALBUM_ART_SIZE_PX)
                } else {
                    value = null
                }
            }

            WidgetContent(
                title = song?.title ?: AppLocaleManager.localizedContext(context).getString(R.string.widget_idle_title),
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
        val widgetSize = LocalSize.current
        val compact = widgetSize.height < 160.dp
        // Match this gap in the bottom inset to keep the progress row near its current height.
        val controlsTopPadding = (widgetSize.height - 174.dp).coerceIn(0.dp, 8.dp)
        // A square fills the right half at 2:1. Cap it on tall widgets to leave room for the text.
        val artworkSize = minOf(widgetSize.height, widgetSize.width * MAX_ARTWORK_WIDTH_FRACTION)
        val accent = remember(artwork) { artwork?.let { DominantColorExtractor.extractColors(it).accentColor } ?: Color(0xFFD2B48C) }
        val tint = ColorProvider(day = accent, night = accent)
        val muted = ColorProvider(day = accent.copy(alpha = 0.7f), night = accent.copy(alpha = 0.7f))
        val track = ColorProvider(day = accent.copy(alpha = 0.2f), night = accent.copy(alpha = 0.2f))
        val localizedContext = AppLocaleManager.localizedContext(LocalContext.current)
        val locale = AppLocaleManager.activeLocale(localizedContext)
        Box(
            GlanceModifier
                .fillMaxWidth()
                .fillMaxHeight()
                .background(
                    Color(0xFF0B0B0B),
                ).appWidgetBackground()
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            if (artwork != null) {
                Box(GlanceModifier.fillMaxWidth().fillMaxHeight(), contentAlignment = Alignment.CenterEnd) {
                    Box(GlanceModifier.size(artworkSize)) {
                        Image(
                            ImageProvider(artwork),
                            null,
                            GlanceModifier.fillMaxWidth().fillMaxHeight(),
                            contentScale = ContentScale.Fit,
                        )
                        Image(
                            ImageProvider(R.drawable.widget_cinematic_gradient),
                            null,
                            GlanceModifier.fillMaxWidth().fillMaxHeight(),
                            contentScale = ContentScale.FillBounds,
                        )
                    }
                }
            }
            Column(
                GlanceModifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(top = if (compact) 6.dp else 18.dp, bottom = if (compact) 4.dp else 14.dp + controlsTopPadding),
            ) {
                Column(GlanceModifier.fillMaxWidth().padding(start = 20.dp, end = if (hasSong) 56.dp else 20.dp)) {
                    Text(
                        localizedContext.getString(R.string.app_name),
                        modifier = GlanceModifier.padding(bottom = 2.dp),
                        style = TextStyle(color = muted, fontSize = 13.sp),
                    )
                    Text(
                        title,
                        modifier = GlanceModifier.fillMaxWidth(),
                        style = TextStyle(color = tint, fontSize = 20.sp, fontWeight = FontWeight.Bold),
                        maxLines = if (widgetSize.height < 200.dp) 1 else 2,
                    )
                    Text(
                        artist,
                        modifier = GlanceModifier.padding(bottom = if (compact) 0.dp else 4.dp),
                        style = TextStyle(color = muted, fontSize = if (compact) 13.sp else 14.sp),
                        maxLines = 1,
                    )
                }
                Row(
                    modifier = GlanceModifier.padding(start = 12.dp, top = controlsTopPadding),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    WidgetControl(
                        R.drawable.ic_widget_skip_previous,
                        localizedContext.getString(R.string.widget_cd_previous),
                        (if (hasSong) actionRunCallback<SkipPreviousAction>() else actionStartActivity<MainActivity>()),
                        tint,
                    )
                    Spacer(GlanceModifier.width(14.dp))
                    WidgetControl(
                        if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
                        localizedContext.getString(if (isPlaying) R.string.widget_cd_pause else R.string.widget_cd_play),
                        (if (hasSong) actionRunCallback<PlayPauseAction>() else actionStartActivity<MainActivity>()),
                        tint,
                        iconSize = 36,
                    )
                    Spacer(GlanceModifier.width(14.dp))
                    WidgetControl(
                        R.drawable.ic_widget_skip_next,
                        localizedContext.getString(R.string.widget_cd_next),
                        (if (hasSong) actionRunCallback<SkipNextAction>() else actionStartActivity<MainActivity>()),
                        tint,
                    )
                }
                // Keep the progress row low without crowding the controls when the launcher shrinks the widget.
                Spacer(GlanceModifier.defaultWeight())
                val progressHeight = if (compact) 16.dp else 20.dp
                val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
                Row(GlanceModifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(formatTime(positionMs, locale), style = TextStyle(color = muted, fontSize = 11.sp))
                    Box(GlanceModifier.defaultWeight().height(progressHeight).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                        LinearProgressIndicator(
                            progress,
                            GlanceModifier.fillMaxWidth().height(2.dp),
                            color = tint,
                            backgroundColor = track,
                        )
                        if (hasSong) {
                            Row(GlanceModifier.fillMaxWidth().height(progressHeight)) {
                                repeat(10) { i ->
                                    Box(
                                        GlanceModifier.defaultWeight().height(progressHeight).clickable(
                                            actionRunCallback<SeekToPercentAction>(
                                                actionParametersOf(
                                                    SeekToPercentAction.KEY_PERCENT to (i + 0.5f) / 10f,
                                                ),
                                            ),
                                        ),
                                    ) {}
                                }
                            }
                        }
                    }
                    Text(formatTime(durationMs, locale), style = TextStyle(color = muted, fontSize = 11.sp))
                }
                if (!compact) Spacer(GlanceModifier.defaultWeight())
            }
            if (hasSong) {
                Box(GlanceModifier.fillMaxWidth().padding(top = if (compact) 0.dp else 4.dp, end = 8.dp), contentAlignment = Alignment.TopEnd) {
                    WidgetControl(
                        if (isFavorite) R.drawable.ic_widget_favorite else R.drawable.ic_widget_favorite_border,
                        localizedContext.getString(R.string.widget_cd_favorite),
                        actionRunCallback<ToggleFavoriteAction>(),
                        tint,
                        iconSize = 24,
                    )
                }
            }
        }
    }

    private fun formatTime(
        ms: Long,
        locale: Locale,
    ): String = LocaleFormatters.formatDurationClock(ms, locale)
}
