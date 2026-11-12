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
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.erhu.erhu.MainActivity
import com.erhu.erhu.R
import com.erhu.erhu.core.common.locale.AppLocaleManager
import com.erhu.erhu.core.common.locale.LocaleFormatters
import com.erhu.erhu.core.designsystem.artwork.ArtworkLoader
import com.erhu.erhu.core.model.PlaybackStatus
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.Locale

private const val ALBUM_ART_SIZE_PX = 400

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
        val compact = LocalSize.current.height < 170.dp
        val extractedColors =
            remember(artwork) {
                artwork?.let {
                    com.erhu.erhu.core.designsystem.theme.DominantColorExtractor
                        .extractColors(it)
                }
            }
        val accent =
            androidx.compose.ui.graphics
                .lerp(extractedColors?.accentColor ?: Color(0xFFD2B48C), Color.White, 0.65f)
        val tint = ColorProvider(day = accent, night = accent)
        val muted = ColorProvider(day = accent.copy(alpha = 0.7f), night = accent.copy(alpha = 0.7f))
        Box(
            GlanceModifier
                .fillMaxWidth()
                .fillMaxHeight()
                .background(
                    Color(0xFF0B0B0B),
                ).appWidgetBackground()
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            Row(GlanceModifier.fillMaxWidth().fillMaxHeight(), horizontalAlignment = Alignment.End) {
                Box(GlanceModifier.width(160.dp).fillMaxHeight()) {
                    if (artwork !=
                        null
                    ) {
                        Image(
                            ImageProvider(artwork),
                            null,
                            GlanceModifier.fillMaxWidth().fillMaxHeight(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    Image(
                        ImageProvider(R.drawable.widget_cinematic_gradient),
                        null,
                        GlanceModifier.fillMaxWidth().fillMaxHeight(),
                        contentScale = ContentScale.FillBounds,
                    )
                }
            }
            Column(GlanceModifier.fillMaxWidth().fillMaxHeight().padding(12.dp)) {
                val localizedContext = AppLocaleManager.localizedContext(LocalContext.current)
                val locale = AppLocaleManager.activeLocale(localizedContext)
                if (!compact) Text(localizedContext.getString(R.string.app_name), style = TextStyle(color = muted, fontSize = 12.sp))
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(GlanceModifier.defaultWeight()) {
                        MarqueeTitle(title, accent, GlanceModifier.fillMaxWidth(), compact)
                        Text(artist, style = TextStyle(color = muted, fontSize = 13.sp), maxLines = 1)
                    }
                    if (hasSong) {
                        WidgetControl(
                            if (isFavorite) R.drawable.ic_widget_favorite else R.drawable.ic_widget_favorite_border,
                            localizedContext.getString(R.string.widget_cd_favorite),
                            actionRunCallback<ToggleFavoriteAction>(),
                            tint,
                        )
                    }
                }
                Spacer(GlanceModifier.defaultWeight())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WidgetControl(
                        R.drawable.ic_widget_skip_previous,
                        localizedContext.getString(R.string.widget_cd_previous),
                        (if (hasSong) actionRunCallback<SkipPreviousAction>() else actionStartActivity<MainActivity>()),
                        tint,
                    )
                    Spacer(GlanceModifier.width(12.dp))
                    WidgetControl(
                        if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
                        localizedContext.getString(if (isPlaying) R.string.widget_cd_pause else R.string.widget_cd_play),
                        (if (hasSong) actionRunCallback<PlayPauseAction>() else actionStartActivity<MainActivity>()),
                        tint,
                        iconSize = 36,
                    )
                    Spacer(GlanceModifier.width(12.dp))
                    WidgetControl(
                        R.drawable.ic_widget_skip_next,
                        localizedContext.getString(R.string.widget_cd_next),
                        (if (hasSong) actionRunCallback<SkipNextAction>() else actionStartActivity<MainActivity>()),
                        tint,
                    )
                }
                if (!compact) {
                    val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
                    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(formatTime(positionMs, locale), style = TextStyle(color = muted, fontSize = 11.sp))
                        Box(GlanceModifier.defaultWeight().height(20.dp).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                            LinearProgressIndicator(
                                progress,
                                GlanceModifier.fillMaxWidth().height(2.dp),
                                color = tint,
                                backgroundColor = muted,
                            )
                            Row(GlanceModifier.fillMaxWidth().height(20.dp)) {
                                repeat(10) { i ->
                                    Box(
                                        GlanceModifier.defaultWeight().height(20.dp).clickable(
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
                        Text(formatTime(durationMs, locale), style = TextStyle(color = muted, fontSize = 11.sp))
                    }
                }
            }
        }
    }

    @Composable
    private fun MarqueeTitle(
        title: String,
        textColor: Color,
        modifier: GlanceModifier,
        compact: Boolean = false,
    ) {
        val packageName = LocalContext.current.packageName
        AndroidRemoteViews(
            remoteViews =
                RemoteViews(packageName, R.layout.widget_title_marquee).apply {
                    setTextViewText(R.id.widget_title_text, title)
                    setInt(R.id.widget_title_text, "setMaxLines", if (compact) 1 else 2)
                    setTextColor(R.id.widget_title_text, textColor.toArgb())
                },
            modifier = modifier.height(if (compact) 24.dp else 44.dp),
        )
    }

    private fun formatTime(
        ms: Long,
        locale: java.util.Locale,
    ): String = LocaleFormatters.formatDurationClock(ms, locale)
}
