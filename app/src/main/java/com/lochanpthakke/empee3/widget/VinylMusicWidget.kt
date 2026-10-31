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
import androidx.glance.ColorFilter
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
import com.lochanpthakke.empee3.core.designsystem.theme.DominantColorExtractor
import com.lochanpthakke.empee3.core.model.PlaybackStatus
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private const val ALBUM_ART_SIZE_PX = 400
private const val CONTROL_ICON_SIZE_DP = 24
private const val CONTROL_ICON_SIZE_LARGE_DP = 32

class VinylMusicWidget : GlanceAppWidget() {
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
            val rawArtwork by produceState<Bitmap?>(initialValue = null, song?.id) {
                if (song != null) {
                    value = ArtworkLoader.load(context, song.uri, song.artworkUri, ALBUM_ART_SIZE_PX)
                } else {
                    value = null
                }
            }

            val circularArtwork = remember(rawArtwork) {
                rawArtwork?.toCircularBitmap()
            }

            WidgetContent(
                title = song?.title ?: "Nothing playing",
                artist = song?.artist.orEmpty(),
                artwork = circularArtwork,
                rawArtwork = rawArtwork,
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
    ) {
        val context = LocalContext.current
        val isDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val defaultTitleColor = if (isDark) Color(0xFFF4F4F4) else Color(0xFF1D1D1F)
        val adaptiveTextColor = ColorProvider(day = Color(0xFF1D1D1F), night = Color(0xFFF4F4F4))
        val mutedTextColor = ColorProvider(day = Color(0xFF6E6E73), night = Color(0xFFA1A1A6))

        // Extract dynamic dominant accent color from album artwork
        val extractedColors = remember(rawArtwork) { rawArtwork?.let { DominantColorExtractor.extractColors(it) } }
        val rawAccent = extractedColors?.accentColor
        val titleTextColor = rawAccent ?: defaultTitleColor
        val controlIconColor = if (rawAccent != null) ColorProvider(day = rawAccent, night = rawAccent) else adaptiveTextColor

        val compositedBackground by produceState<Bitmap?>(initialValue = null, artwork, isDark) {
            val options = BitmapFactory.Options().apply { inMutable = true }
            val rawBg = BitmapFactory.decodeResource(context.resources, R.drawable.ic_vinyl_widget_bg, options)
            if (rawBg != null) {
                val targetSize = 480
                val baseBg = if (rawBg.width != targetSize) {
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
                        .padding(start = 24.dp, top = 12.dp, end = 24.dp, bottom = 30.dp),
            ) {
                Spacer(modifier = GlanceModifier.defaultWeight())

                // Lower Section: Artist & Song Title on Left, Playback Controls on Right
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    // Lower Left Column: Artist Name above, Song Title below
                    Column(
                        modifier =
                            GlanceModifier
                                .defaultWeight()
                                .padding(end = 12.dp),
                    ) {
                        Text(
                            text = if (artist.isNotEmpty()) artist else "Unknown Artist",
                            style = TextStyle(color = mutedTextColor, fontSize = 12.sp, fontWeight = FontWeight.Normal),
                            maxLines = 1,
                        )

                        Spacer(modifier = GlanceModifier.height(2.dp))

                        MarqueeTitle(
                            title = title,
                            textColor = titleTextColor,
                            modifier = GlanceModifier.fillMaxWidth(),
                        )
                    }

                    // Lower Right Row: 3 Playback Controls (Previous | Play/Pause | Next) with Dominant Accent Tint
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Image(
                            provider = ImageProvider(R.drawable.ic_widget_skip_previous),
                            contentDescription = "Previous",
                            modifier =
                                GlanceModifier
                                    .size(CONTROL_ICON_SIZE_DP.dp)
                                    .clickable(actionRunCallback<VinylSkipPreviousAction>()),
                            colorFilter = ColorFilter.tint(controlIconColor),
                        )

                        Spacer(modifier = GlanceModifier.width(18.dp))

                        Image(
                            provider = ImageProvider(if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play),
                            contentDescription = "Play/Pause",
                            modifier =
                                GlanceModifier
                                    .size(CONTROL_ICON_SIZE_LARGE_DP.dp)
                                    .clickable(actionRunCallback<VinylPlayPauseAction>()),
                            colorFilter = ColorFilter.tint(controlIconColor),
                        )

                        Spacer(modifier = GlanceModifier.width(18.dp))

                        Image(
                            provider = ImageProvider(R.drawable.ic_widget_skip_next),
                            contentDescription = "Next",
                            modifier =
                                GlanceModifier
                                    .size(CONTROL_ICON_SIZE_DP.dp)
                                    .clickable(actionRunCallback<VinylSkipNextAction>()),
                            colorFilter = ColorFilter.tint(controlIconColor),
                        )
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
