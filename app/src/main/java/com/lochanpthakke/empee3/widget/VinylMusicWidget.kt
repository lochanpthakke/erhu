// Published Android/Glance/WorkManager identity retained for upgrade compatibility.
package com.lochanpthakke.empee3.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.provideContent
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
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
import com.erhu.erhu.core.designsystem.artwork.ArtworkLoader
import com.erhu.erhu.core.designsystem.theme.DominantColorExtractor
import com.erhu.erhu.core.model.PlaybackStatus
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private const val ALBUM_ART_SIZE_PX = 400

class VinylMusicWidget : GlanceAppWidget() {
    override val sizeMode = androidx.glance.appwidget.SizeMode.Exact
    override val previewSizeMode =
        androidx.glance.appwidget.SizeMode.Responsive(
            setOf(
                androidx.compose.ui.unit
                    .DpSize(180.dp, 180.dp),
                androidx.compose.ui.unit
                    .DpSize(260.dp, 260.dp),
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
                rawArtwork = null,
                isPlaying = false,
                hasSong = false,
            )
        }
    }

    override fun onCompositionError(
        context: Context,
        glanceId: GlanceId,
        appWidgetId: Int,
        throwable: Throwable,
    ) {
        android.util.Log.e("ErhuWidget", "VinylMusicWidget rendering failed: $appWidgetId", throwable)
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
            val rawArtwork by produceState<Bitmap?>(initialValue = null, song?.id) {
                if (song != null) {
                    value = ArtworkLoader.load(context, song.uri, song.artworkUri, ALBUM_ART_SIZE_PX)
                } else {
                    value = null
                }
            }

            val circularArtwork =
                remember(rawArtwork) {
                    rawArtwork?.toCircularBitmap()
                }

            WidgetContent(
                title = song?.title ?: AppLocaleManager.localizedContext(context).getString(R.string.widget_idle_title),
                artist = song?.artist.orEmpty(),
                artwork = circularArtwork,
                rawArtwork = rawArtwork,
                hasSong = song != null,
                isPlaying =
                    playbackState.status == PlaybackStatus.PLAYING ||
                        playbackState.status == PlaybackStatus.BUFFERING,
            )
        }
    }

    @Composable
    private fun WidgetContent(
        title: String,
        artist: String,
        artwork: Bitmap?,
        rawArtwork: Bitmap?,
        isPlaying: Boolean,
        hasSong: Boolean,
    ) {
        val context = LocalContext.current
        val isDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val defaultTitleColor = if (isDark) Color(0xFFF4F4F4) else Color(0xFF1D1D1F)
        val adaptiveTextColor = ColorProvider(day = Color(0xFF1D1D1F), night = Color(0xFFF4F4F4))
        val mutedTextColor = ColorProvider(day = Color(0xFF6E6E73), night = Color(0xFFA1A1A6))

        // Extract dynamic dominant accent color from album artwork
        val extractedColors = remember(rawArtwork) { rawArtwork?.let { DominantColorExtractor.extractColors(it) } }
        val rawAccent =
            extractedColors?.accentColor?.let {
                androidx.compose.ui.graphics.lerp(
                    it,
                    if (isDark) Color.White else Color.Black,
                    0.65f,
                )
            }
        val titleTextColor = rawAccent ?: defaultTitleColor
        val controlIconColor = if (rawAccent != null) ColorProvider(day = rawAccent, night = rawAccent) else adaptiveTextColor

        val compositedBackground by produceState<Bitmap?>(initialValue = null, artwork, isDark) {
            val options = BitmapFactory.Options().apply { inMutable = true }
            val rawBg = BitmapFactory.decodeResource(context.resources, R.drawable.ic_vinyl_widget_bg, options)
            if (rawBg != null) {
                val targetSize = 480
                val baseBg =
                    if (rawBg.width != targetSize) {
                        val scaled = Bitmap.createScaledBitmap(rawBg, targetSize, targetSize, true)
                        rawBg.recycle()
                        scaled
                    } else {
                        rawBg
                    }
                val canvas = Canvas(baseBg)
                val w = baseBg.width
                val h = baseBg.height
                val cx = w * 0.5030f // Exact spindle pin center
                val cy = if (isDark) h * 0.3926f else h * 0.4030f // Exact spindle pin center
                val radius = w * 0.2007f // Exact record label circle radius

                if (artwork != null) {
                    val srcRect = Rect(0, 0, artwork.width, artwork.height)
                    val dstRect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
                    canvas.drawBitmap(artwork, srcRect, dstRect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))

                    // Draw white spindle dot in the center of the record
                    val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.parseColor("#FFFDF5") }
                    canvas.drawCircle(cx, cy, w * 0.009f, dotPaint)
                }
                value = baseBg
            } else {
                value = null
            }
        }

        Box(
            modifier =
                GlanceModifier
                    .fillMaxSize()
                    .appWidgetBackground()
                    .clickable(actionStartActivity<MainActivity>()),
            contentAlignment = Alignment.Center,
        ) {
            if (compositedBackground != null) {
                Image(
                    provider = ImageProvider(compositedBackground!!),
                    contentDescription = null,
                    modifier = GlanceModifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Image(
                    provider = ImageProvider(R.drawable.ic_vinyl_widget_bg),
                    contentDescription = null,
                    modifier = GlanceModifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }

            Column(
                modifier =
                    GlanceModifier
                        .fillMaxSize()
                        .padding(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 12.dp),
            ) {
                val localizedContext = AppLocaleManager.localizedContext(LocalContext.current)
                Spacer(modifier = GlanceModifier.defaultWeight())

                // A 3x3 host may be only 180dp wide; do not squeeze labels and 3 controls into one row.
                Text(
                    artist.ifEmpty { localizedContext.getString(R.string.widget_idle_hint) },
                    style = TextStyle(color = mutedTextColor, fontSize = 12.sp),
                    maxLines = 1,
                )
                MarqueeTitle(title, titleTextColor, GlanceModifier.fillMaxWidth())
                Row(GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    WidgetControl(
                        R.drawable.ic_widget_skip_previous,
                        localizedContext.getString(R.string.widget_cd_previous),
                        (if (hasSong) actionRunCallback<VinylSkipPreviousAction>() else actionStartActivity<MainActivity>()),
                        controlIconColor,
                    )
                    WidgetControl(
                        if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
                        localizedContext.getString(if (isPlaying) R.string.widget_cd_pause else R.string.widget_cd_play),
                        (if (hasSong) actionRunCallback<VinylPlayPauseAction>() else actionStartActivity<MainActivity>()),
                        controlIconColor,
                        iconSize = 32,
                    )
                    WidgetControl(
                        R.drawable.ic_widget_skip_next,
                        localizedContext.getString(R.string.widget_cd_next),
                        (if (hasSong) actionRunCallback<VinylSkipNextAction>() else actionStartActivity<MainActivity>()),
                        controlIconColor,
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

    private fun Bitmap.toCircularBitmap(): Bitmap {
        val minDim = minOf(width, height)
        val output = Bitmap.createBitmap(minDim, minDim, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = Rect((width - minDim) / 2, (height - minDim) / 2, (width + minDim) / 2, (height + minDim) / 2)
        val rectF = RectF(0f, 0f, minDim.toFloat(), minDim.toFloat())

        canvas.drawARGB(0, 0, 0, 0)
        canvas.drawRoundRect(rectF, minDim / 2f, minDim / 2f, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(this, rect, rectF, paint)
        return output
    }
}
