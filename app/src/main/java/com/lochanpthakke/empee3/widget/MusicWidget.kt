package com.lochanpthakke.empee3.widget

import android.content.Context
import android.graphics.Bitmap
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.Text
import com.lochanpthakke.empee3.R
import com.lochanpthakke.empee3.core.designsystem.artwork.ArtworkLoader
import com.lochanpthakke.empee3.core.model.PlaybackStatus
import kotlinx.coroutines.flow.first

private const val ALBUM_ART_SIZE_DP = 64
private const val ALBUM_ART_SIZE_PX = 192
private const val CONTROL_ICON_SIZE_DP = 32

class MusicWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<Preferences> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val playbackState = context.playbackController().playbackState.value
        val song = playbackState.currentSong
        val isFavorite = song?.let { context.libraryRepository().observeIsFavorite(it.id).first() } ?: false
        val artwork = song?.let { ArtworkLoader.load(context, it.uri, it.artworkUri, ALBUM_ART_SIZE_PX) }

        provideContent {
            WidgetContent(
                title = song?.title ?: "Nothing playing",
                artist = song?.artist.orEmpty(),
                artwork = artwork,
                isPlaying =
                    playbackState.status == PlaybackStatus.PLAYING ||
                        playbackState.status == PlaybackStatus.BUFFERING,
                isFavorite = isFavorite,
                hasSong = song != null,
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
    ) {
        Row(
            modifier = GlanceModifier.fillMaxSize().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                MarqueeTitle(title = title, modifier = GlanceModifier.fillMaxWidth())

                Text(text = artist)

                Row {
                    Image(
                        provider = ImageProvider(android.R.drawable.ic_media_previous),
                        contentDescription = "Previous",
                        modifier =
                            GlanceModifier
                                .size(CONTROL_ICON_SIZE_DP.dp)
                                .clickable(actionRunCallback<SkipPreviousAction>()),
                    )
                    Image(
                        provider =
                            ImageProvider(
                                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                            ),
                        contentDescription = "Play or pause",
                        modifier =
                            GlanceModifier
                                .size(CONTROL_ICON_SIZE_DP.dp)
                                .clickable(actionRunCallback<PlayPauseAction>()),
                    )
                    Image(
                        provider = ImageProvider(android.R.drawable.ic_media_next),
                        contentDescription = "Next",
                        modifier =
                            GlanceModifier
                                .size(CONTROL_ICON_SIZE_DP.dp)
                                .clickable(actionRunCallback<SkipNextAction>()),
                    )
                    val favoriteIconRes =
                        if (isFavorite) {
                            android.R.drawable.btn_star_big_on
                        } else {
                            android.R.drawable.btn_star_big_off
                        }

                    if (hasSong) {
                        Image(
                            provider = ImageProvider(favoriteIconRes),
                            contentDescription = "Toggle favorite",
                            modifier =
                                GlanceModifier
                                    .size(CONTROL_ICON_SIZE_DP.dp)
                                    .clickable(actionRunCallback<ToggleFavoriteAction>()),
                        )
                    }
                }
            }

            AlbumArt(bitmap = artwork)
        }
    }

    @Composable
    private fun AlbumArt(bitmap: Bitmap?) {
        if (bitmap != null) {
            Image(
                provider = ImageProvider(bitmap),
                contentDescription = "Album art",
                modifier = GlanceModifier.size(ALBUM_ART_SIZE_DP.dp),
            )
        } else {
            Box(
                modifier =
                    GlanceModifier
                        .size(ALBUM_ART_SIZE_DP.dp)
                        .background(Color.DarkGray),
            ) {}
        }
    }

    @Composable
    private fun MarqueeTitle(
        title: String,
        modifier: GlanceModifier,
    ) {
        val packageName = LocalContext.current.packageName
        AndroidRemoteViews(
            remoteViews =
                RemoteViews(packageName, R.layout.widget_title_marquee).apply {
                    setTextViewText(R.id.widget_title_text, title)
                    setBoolean(R.id.widget_title_text, "setSelected", true)
                },
            modifier = modifier,
        )
    }
}
