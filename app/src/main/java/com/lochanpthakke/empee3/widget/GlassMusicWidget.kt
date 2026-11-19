// Published Android/Glance/WorkManager identity retained for upgrade compatibility.
package com.lochanpthakke.empee3.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.DpSize
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
import com.erhu.erhu.core.common.locale.AppLocaleManager
import com.erhu.erhu.core.common.locale.LocaleFormatters
import com.erhu.erhu.core.designsystem.artwork.ArtworkLoader
import com.erhu.erhu.core.designsystem.theme.BuiltInThemes
import com.erhu.erhu.core.designsystem.theme.GlassTokens
import com.erhu.erhu.core.model.PlaybackStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

private const val ALBUM_ART_SIZE_PX = 400
private const val WIDGET_BACKGROUND_MAX_PX = 512f
private const val WIDGET_CORNER_RADIUS_DP = 24f
private const val COVER_SIDE_DP = 64f
private const val COVER_RADIUS_DP = 8f

private val widgetGlass = GlassTokens.derivedFrom(BuiltInThemes.DEFAULT_DARK.colors, isDark = true)

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
                    val sidePx = (COVER_SIDE_DP * density).roundToInt().coerceAtLeast(1)
                    value = rawBitmap?.let { withContext(Dispatchers.Default) { it.toRoundedSquare(sidePx, COVER_RADIUS_DP * density) } }
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
        val compact = widgetSize.height < 180.dp
        val background = remember(widgetSize) { renderGlassBackground(widgetSize) }
        val white = ColorProvider(day = Color.White, night = Color.White)
        val muted = ColorProvider(day = Color.White.copy(alpha = 0.7f), night = Color.White.copy(alpha = 0.7f))
        val artSide = if (compact) 48.dp else COVER_SIDE_DP.dp
        Box(
            GlanceModifier
                .fillMaxWidth()
                .fillMaxHeight()
                .background(
                    ImageProvider(background),
                    contentScale = ContentScale.FillBounds,
                ).appWidgetBackground()
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            Column(GlanceModifier.fillMaxWidth().fillMaxHeight().padding(horizontal = 12.dp, vertical = 8.dp)) {
                val localizedContext = AppLocaleManager.localizedContext(LocalContext.current)
                val locale = AppLocaleManager.activeLocale(localizedContext)
                Text(localizedContext.getString(R.string.app_name), style = TextStyle(color = muted, fontSize = 13.sp))
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
                        Text(
                            artist.ifEmpty { localizedContext.getString(R.string.widget_idle_hint) },
                            style = TextStyle(color = muted, fontSize = 13.sp),
                            maxLines = 1,
                        )
                    }
                    if (hasSong) {
                        Box(
                            GlanceModifier.size(48.dp).clickable(actionRunCallback<GlassToggleFavoriteAction>()),
                            contentAlignment = Alignment.CenterEnd,
                        ) {
                            Image(
                                ImageProvider(if (isFavorite) R.drawable.ic_widget_favorite else R.drawable.ic_widget_favorite_border),
                                localizedContext.getString(R.string.widget_cd_favorite),
                                GlanceModifier.size(26.dp),
                                colorFilter = ColorFilter.tint(white),
                            )
                        }
                    }
                }
                Spacer(GlanceModifier.defaultWeight())
                if (!compact) {
                    val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
                    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(formatTime(positionMs, locale), style = TextStyle(color = muted, fontSize = 11.sp))
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
                        Text(formatTime(durationMs, locale), style = TextStyle(color = muted, fontSize = 11.sp))
                    }
                }
                Spacer(GlanceModifier.defaultWeight())
                Row(GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    WidgetControl(
                        R.drawable.ic_widget_skip_previous,
                        localizedContext.getString(R.string.widget_cd_previous),
                        (if (hasSong) actionRunCallback<GlassSkipPreviousAction>() else actionStartActivity<MainActivity>()),
                        white,
                    )
                    Spacer(GlanceModifier.width(12.dp))
                    WidgetControl(
                        if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
                        localizedContext.getString(if (isPlaying) R.string.widget_cd_pause else R.string.widget_cd_play),
                        (if (hasSong) actionRunCallback<GlassPlayPauseAction>() else actionStartActivity<MainActivity>()),
                        white,
                        iconSize = 36,
                    )
                    Spacer(GlanceModifier.width(12.dp))
                    WidgetControl(
                        R.drawable.ic_widget_skip_next,
                        localizedContext.getString(R.string.widget_cd_next),
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

    private fun formatTime(
        ms: Long,
        locale: Locale,
    ): String = LocaleFormatters.formatDurationClock(ms, locale)

    private fun Bitmap.toRoundedSquare(
        sidePx: Int,
        radiusPx: Float,
    ): Bitmap {
        val output = Bitmap.createBitmap(sidePx, sidePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val cropSide = minOf(width, height)
        val left = (width - cropSide) / 2
        val top = (height - cropSide) / 2
        val source = Rect(left, top, left + cropSide, top + cropSide)
        val destination = RectF(0f, 0f, sidePx.toFloat(), sidePx.toFloat())

        canvas.drawRoundRect(destination, radiusPx, radiusPx, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(this, source, destination, paint)
        return output
    }

    /** Rasterize the app's glass surface colors for the launcher, which owns the wallpaper backdrop. */
    private fun renderGlassBackground(size: DpSize): Bitmap {
        val scale = minOf(1f, WIDGET_BACKGROUND_MAX_PX / maxOf(size.width.value, size.height.value))
        val width = (size.width.value * scale).roundToInt().coerceAtLeast(1)
        val height = (size.height.value * scale).roundToInt().coerceAtLeast(1)
        val right = width.toFloat()
        val bottom = height.toFloat()
        val radius = WIDGET_CORNER_RADIUS_DP * scale
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bounds = RectF(0f, 0f, right, bottom)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val base = widgetGlass.panelFillColor(strong = true)
        val fill = base.copy(alpha = base.alpha * 0.3f)
        val sheen = fill.copy(alpha = fill.alpha + 0.06f)
        paint.shader = LinearGradient(0f, 0f, 0f, bottom, sheen.toArgb(), fill.toArgb(), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(bounds, radius, radius, paint)

        val light = Color(widgetGlass.specular)
        val clearLight = light.copy(alpha = 0f).toArgb()
        paint.shader = LinearGradient(0f, 0f, 0f, bottom * 0.3f, light.toArgb(), clearLight, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(bounds, radius, radius, paint)
        paint.shader =
            RadialGradient(
                0f,
                0f,
                maxOf(right, bottom) * 0.9f,
                light.copy(alpha = light.alpha * 0.6f).toArgb(),
                clearLight,
                Shader.TileMode.CLAMP,
            )
        canvas.drawRoundRect(bounds, radius, radius, paint)

        val shade = Color(widgetGlass.edgeShade)
        paint.shader = LinearGradient(0f, bottom * 0.55f, 0f, bottom, shade.copy(alpha = 0f).toArgb(), shade.toArgb(), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(bounds, radius, radius, paint)

        val rim = Color(widgetGlass.borderHighlight)
        val rimWidth = widgetGlass.borderWidthDp * scale
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = rimWidth
        paint.shader =
            LinearGradient(
                0f,
                0f,
                right,
                bottom,
                intArrayOf(rim.toArgb(), rim.copy(alpha = rim.alpha * 0.08f).toArgb(), rim.copy(alpha = rim.alpha * 0.35f).toArgb()),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP,
            )
        bounds.inset(rimWidth / 2f, rimWidth / 2f)
        canvas.drawRoundRect(bounds, radius - rimWidth / 2f, radius - rimWidth / 2f, paint)
        return bitmap
    }
}
