// Fixed portrait proportions and bounded image sampling sizes.
@file:Suppress("MagicNumber")

package com.erhu.erhu.feature.library

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.util.LruCache
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.erhu.erhu.core.database.library.ArtistArtworkCache
import com.erhu.erhu.core.database.library.CreditText
import com.erhu.erhu.core.designsystem.artwork.AlbumArtwork
import com.erhu.erhu.core.designsystem.artwork.ArtworkLoader
import com.erhu.erhu.core.designsystem.foundation.Spacing
import com.erhu.erhu.core.designsystem.foundation.TextStyles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Uses verified station credits only. No fuzzy artist parsing, new radio generation or playback input. */
internal fun radioPortraits(content: RadioStationContent): List<RadioPortrait> {
    val songs = content.songs.associateBy { it.id }
    val artists =
        content.artists
            .filter {
                it.representativeSongId in songs &&
                    !it.artist.isUnresolved &&
                    CreditText.canonicalKey(it.artist.name) !in UNKNOWN_RADIO_ARTISTS
            }.distinctBy { it.artist.artistKey.ifBlank { CreditText.canonicalKey(it.artist.name) } }
    val lead = artists.firstOrNull { it.artist.id == content.album.artistId } ?: artists.firstOrNull()
    if (lead == null) {
        val song = songs[content.plan?.startSongId] ?: content.songs.firstOrNull()
        return listOf(RadioPortrait(null, content.album.artist, null, song))
    }
    return (listOf(lead) + artists.filter { it.artist.id != lead.artist.id }).take(3).map {
        RadioPortrait(
            it.artist.id,
            it.artist.name,
            it.artist.artworkUri?.takeIf(ArtistArtworkCache::hasArtwork),
            songs[it.representativeSongId],
        )
    }
}

private val UNKNOWN_RADIO_ARTISTS = setOf("", "unknown artist", "<unknown>", "various artists")

private val radioPaletteCache = LruCache<String, LyricsPalette>(32)

/** One small, cached image sample per station image; independent of playing track and position ticks. */
@Composable
internal fun rememberRadioPalette(portrait: RadioPortrait?): State<LyricsPalette?> {
    val context = LocalContext.current.applicationContext
    val key =
        remember(portrait?.imageUri, portrait?.song?.uri, portrait?.song?.artworkUri) {
            listOf(portrait?.imageUri, portrait?.song?.uri, portrait?.song?.artworkUri).joinToString("|")
        }
    return produceState<LyricsPalette?>(remember(key) { radioPaletteCache.get(key) }, key) {
        value = radioPaletteCache.get(key)
        if (value != null) return@produceState
        val bitmap =
            withContext(Dispatchers.IO) {
                val photo =
                    portrait?.imageUri?.let { uri ->
                        val request =
                            ImageRequest
                                .Builder(context)
                                .data(uri)
                                .size(64)
                                .allowHardware(false)
                                .build()
                        ((context.imageLoader.execute(request) as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
                    }
                photo ?: portrait?.song?.let { ArtworkLoader.load(context, it.uri, it.artworkUri, 64) }
            }
        val palette = bitmap?.let { withContext(Dispatchers.Default) { radioPalette(it) } }
        if (palette != null) radioPaletteCache.put(key, palette)
        value = palette
    }
}

internal fun radioPalette(bitmap: Bitmap): LyricsPalette {
    val sample = Bitmap.createScaledBitmap(bitmap, 32, 32, true)
    return try {
        val pixels = IntArray(32 * 32)
        sample.getPixels(pixels, 0, 32, 0, 0, 32, 32)
        LyricsPalette.fromPixels(pixels)
    } finally {
        if (sample !== bitmap) sample.recycle()
    }
}

internal fun radioColorScheme(
    base: ColorScheme,
    palette: LyricsPalette?,
): ColorScheme =
    if (palette == null) {
        base
    } else {
        base.copy(
            primary = Color(palette.accent),
            onPrimary = Color(palette.surface),
            primaryContainer = Color(palette.gradient.first()),
            onPrimaryContainer = Color(palette.content),
            outline = Color(palette.rim),
        )
    }

@Composable
@Suppress("LongMethod")
internal fun RadioStationHero(
    portraits: List<RadioPortrait>,
    title: String,
    subtitle: String,
    palette: LyricsPalette?,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val gradient = listOf(colors.background, palette?.gradient?.first()?.let(::Color) ?: colors.surfaceVariant, colors.background)
    Column(modifier.fillMaxWidth().background(Brush.verticalGradient(gradient))) {
        BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(1.42f).clipToBounds()) {
            val width = maxWidth
            portraits.drop(1).take(2).forEachIndexed { index, portrait ->
                RadioPortraitImage(
                    portrait,
                    Modifier
                        .align(if (index == 0) Alignment.TopStart else Alignment.TopEnd)
                        .offset(x = width * if (index == 0) -0.10f else 0.10f, y = width * 0.13f)
                        .size(width * 0.46f),
                )
            }
            portraits.firstOrNull()?.let { main ->
                RadioPortraitImage(main, Modifier.align(Alignment.TopCenter).offset(y = width * 0.02f).size(width * 0.66f))
            }
            // Like the album hero, the lower artwork blends into the page, including its edges.
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, colors.background))),
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.background)
                .padding(horizontal = Spacing.screenGutter)
                .padding(bottom = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(title, style = TextStyles.pageTitle, color = colors.onBackground)
            Text(subtitle, style = TextStyles.metadata, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun RadioPortraitImage(
    portrait: RadioPortrait,
    modifier: Modifier = Modifier,
) {
    val imageModifier = Modifier.fillMaxSize()
    val fallback: @Composable () -> Unit = {
        AlbumArtwork(portrait.song?.artworkUri ?: portrait.song?.uri, imageModifier)
    }
    androidx.compose.foundation.layout.Box(
        modifier
            .clip(CircleShape)
            .border(2.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f), CircleShape)
            .semantics { contentDescription = portrait.name },
    ) {
        if (portrait.imageUri == null) {
            fallback()
        } else {
            SubcomposeAsyncImage(
                model = portrait.imageUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = imageModifier,
                loading = { fallback() },
                error = { fallback() },
            )
        }
    }
}
