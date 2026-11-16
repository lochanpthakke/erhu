package com.erhu.erhu.feature.library.share

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.erhu.erhu.core.common.artwork.ArtworkLoader
import com.erhu.erhu.core.common.locale.AppLocaleManager
import com.erhu.erhu.core.common.locale.LocaleFormatters
import com.erhu.erhu.core.database.repository.ListeningStatsRepository
import com.erhu.erhu.core.database.repository.PeriodStats
import com.erhu.erhu.core.database.repository.StatsPeriod
import com.erhu.erhu.core.designsystem.foundation.IconSize
import com.erhu.erhu.core.designsystem.foundation.Radius
import com.erhu.erhu.core.designsystem.foundation.Spacing
import com.erhu.erhu.core.designsystem.foundation.TextStyles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import com.erhu.erhu.core.common.R as CommonR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsShareDialog(
    statsRepository: ListeningStatsRepository,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedPeriod by remember { mutableStateOf(StatsPeriod.THIS_MONTH) }
    var selectedType by remember { mutableStateOf(StatsCardType.TOP_SONGS) }
    var aspectRatio by remember { mutableStateOf(CardAspectRatio.STORY) }
    var darkness by remember { mutableFloatStateOf(0.40f) }
    var isExporting by remember { mutableStateOf(false) }

    // Remembered per period: every slider step recomposes, and must not restart the queries.
    val periodStatsFlow = remember(statsRepository, selectedPeriod) { statsRepository.observePeriodStats(selectedPeriod) }
    val periodStats by periodStatsFlow.collectAsState(initial = PeriodStats(selectedPeriod))

    // Load artworks for top tracks, albums, and artists
    val artworkMap by produceState<Map<String, Bitmap>>(initialValue = emptyMap(), periodStats) {
        value = withContext(Dispatchers.IO) {
            val map = mutableMapOf<String, Bitmap>()
            val imageLoader = coil.Coil.imageLoader(context)

            suspend fun fetchBitmap(key: String, uriString: String?) {
                if (uriString.isNullOrBlank()) return
                try {
                    val req = coil.request.ImageRequest.Builder(context)
                        .data(uriString)
                        .size(ARTWORK_PX)
                        .allowHardware(false)
                        .build()
                    val res = imageLoader.execute(req)
                    val bm = (res.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                    if (bm != null) map[key] = bm
                } catch (_: Exception) {}
            }

            // Load top songs artwork
            periodStats.topSongs.take(5).forEach { ranked ->
                val bm = ArtworkLoader.load(context, ranked.song.uri, ranked.song.artworkUri, ARTWORK_PX)
                if (bm != null) {
                    map[StatsCardRenderer.songKey(ranked.song.id)] = bm
                } else {
                    fetchBitmap(StatsCardRenderer.songKey(ranked.song.id), ranked.song.artworkUri ?: ranked.song.uri)
                }
            }

            // Load top albums artwork
            periodStats.topAlbums.take(5).forEach { ranked ->
                fetchBitmap(StatsCardRenderer.albumKey(ranked.album.id), ranked.album.artworkUri)
            }

            // Load top artists artwork
            periodStats.topArtists.take(5).forEach { ranked ->
                fetchBitmap(StatsCardRenderer.artistKey(ranked.artist.id), ranked.artist.artworkUri)
            }

            map
        }
    }

    val periodLabel = periodLabel(selectedPeriod)
    val resources = context.resources
    val locale = AppLocaleManager.activeLocale(context)
    val labels =
        StatsCardLabels(
            periodLabel = periodLabel,
            soundCapsule = stringResource(CommonR.string.nav_sound_capsule),
            topSongs = stringResource(CommonR.string.capsule_top_songs),
            topArtists = stringResource(CommonR.string.capsule_top_artists),
            topAlbums = stringResource(CommonR.string.capsule_top_albums),
            minutesListened = stringResource(CommonR.string.capsule_minutes_listened),
            noActivity = stringResource(CommonR.string.share_stats_no_activity),
            noActivityDescription = stringResource(CommonR.string.share_stats_no_activity_desc),
            playsUnit = { resources.getQuantityString(CommonR.plurals.capsule_plays_unit, it) },
            tracksUnit = { resources.getQuantityString(CommonR.plurals.capsule_tracks_unit, it) },
            playsCount = { resources.getQuantityString(CommonR.plurals.capsule_plays_count, it, LocaleFormatters.formatInteger(it, locale)) },
            number = { LocaleFormatters.formatInteger(it.toInt(), locale) },
        )

    // The preview and the saved / shared file are this one bitmap.
    val renderedBitmap by produceState<Bitmap?>(
        initialValue = null,
        periodStats,
        selectedType,
        aspectRatio,
        darkness,
        artworkMap,
        periodLabel,
    ) {
        value = withContext(Dispatchers.Default) {
            StatsCardRenderer.render(periodStats, selectedType, artworkMap, StatsShareConfig(aspectRatio = aspectRatio, darkness = darkness), labels)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = Radius.large, topEnd = Radius.large),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                // The close button's own padding puts its icon on the gutter line.
                modifier = Modifier.fillMaxWidth().padding(start = Spacing.screenGutter, end = Spacing.xs),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(CommonR.string.share_stats_title),
                    style = TextStyles.pageTitle,
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(CommonR.string.cd_close))
                }
            }

            Spacer(modifier = Modifier.height(Spacing.md))
            CardPreview(renderedBitmap, aspectRatio)
            Spacer(modifier = Modifier.height(Spacing.xl))

            ChoiceGroup(
                title = stringResource(CommonR.string.stats_period_label),
                options = StatsPeriod.entries,
                selected = selectedPeriod,
                label = { periodLabel(it) },
                onSelect = { selectedPeriod = it },
            )
            Spacer(modifier = Modifier.height(Spacing.xl))
            ChoiceGroup(
                title = stringResource(CommonR.string.stats_type_label),
                options = StatsCardType.entries,
                selected = selectedType,
                label = { cardTypeLabel(it) },
                onSelect = { selectedType = it },
            )
            Spacer(modifier = Modifier.height(Spacing.xl))
            ChoiceGroup(
                title = stringResource(CommonR.string.share_card_ratio),
                options = CardAspectRatio.entries,
                selected = aspectRatio,
                label = { aspectRatioLabel(it) },
                onSelect = { aspectRatio = it },
            )
            Spacer(modifier = Modifier.height(Spacing.xl))

            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screenGutter)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GroupTitle(stringResource(CommonR.string.share_card_darkness))
                    Text(
                        text = "${LocaleFormatters.formatInteger((darkness * 100).roundToInt(), locale)}%",
                        style = TextStyles.metadata,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Slider(
                    value = darkness,
                    onValueChange = { darkness = it },
                    valueRange = 0.15f..0.85f,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            }

            Spacer(modifier = Modifier.height(Spacing.lg))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.screenGutter)
                    .padding(bottom = Spacing.xl),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                val fileName = "erhu_recap_${selectedType.name.lowercase()}_${selectedPeriod.name.lowercase()}"
                OutlinedButton(
                    onClick = {
                        val bitmap = renderedBitmap ?: return@OutlinedButton
                        isExporting = true
                        scope.launch {
                            try {
                                val saved = ShareCardExporter.saveToGallery(context, bitmap, fileName)
                                Toast.makeText(
                                    context,
                                    context.getString(if (saved) CommonR.string.share_card_saved else CommonR.string.share_card_save_failed),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            } finally {
                                isExporting = false
                            }
                        }
                    },
                    modifier = Modifier.weight(1f).height(ACTION_HEIGHT),
                    shape = Radius.pillShape,
                    enabled = renderedBitmap != null && !isExporting,
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(IconSize.small))
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Text(stringResource(CommonR.string.share_card_save), maxLines = 1)
                }

                Button(
                    onClick = {
                        val bitmap = renderedBitmap ?: return@Button
                        isExporting = true
                        scope.launch {
                            try {
                                val uri = ShareCardExporter.saveToCache(context, bitmap, fileName)
                                ShareCardExporter.shareImage(context, uri, periodLabel)
                            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                throw cancelled
                            } catch (error: Exception) {
                                android.util.Log.w("StatsShare", "Sharing the recap failed", error)
                                Toast.makeText(context, context.getString(CommonR.string.share_card_save_error), Toast.LENGTH_SHORT).show()
                            } finally {
                                isExporting = false
                            }
                        }
                    },
                    modifier = Modifier.weight(1f).height(ACTION_HEIGHT),
                    shape = Radius.pillShape,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    enabled = renderedBitmap != null && !isExporting,
                ) {
                    if (isExporting) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(IconSize.small))
                    } else {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(IconSize.small))
                        Spacer(modifier = Modifier.width(Spacing.sm))
                        Text(stringResource(CommonR.string.share_card_share), maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun periodLabel(period: StatsPeriod): String =
    stringResource(
        when (period) {
            StatsPeriod.LAST_7_DAYS -> CommonR.string.stats_period_7_days
            StatsPeriod.LAST_30_DAYS -> CommonR.string.stats_period_30_days
            StatsPeriod.THIS_MONTH -> CommonR.string.stats_period_this_month
            StatsPeriod.THIS_YEAR -> CommonR.string.stats_period_this_year
        },
    )

@Composable
private fun cardTypeLabel(type: StatsCardType): String =
    stringResource(
        when (type) {
            StatsCardType.TOP_SONGS -> CommonR.string.stats_type_top_songs
            StatsCardType.TOP_ARTISTS -> CommonR.string.stats_type_top_artists
            StatsCardType.TOP_ALBUMS -> CommonR.string.stats_type_top_albums
            StatsCardType.OVERVIEW -> CommonR.string.stats_type_overview
        },
    )

@Composable
private fun aspectRatioLabel(ratio: CardAspectRatio): String =
    stringResource(
        when (ratio) {
            CardAspectRatio.STORY -> CommonR.string.share_card_ratio_story
            CardAspectRatio.SQUARE -> CommonR.string.share_card_ratio_square
            CardAspectRatio.FEED -> CommonR.string.share_card_ratio_portrait
        },
    )

/**
 * The card exactly as it will be saved: at most 72% of the sheet's width and 45% of the
 * screen's height, so a Story preview does not push every control out of view.
 */
@Composable
private fun CardPreview(
    bitmap: Bitmap?,
    aspectRatio: CardAspectRatio,
) {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    BoxWithConstraints(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val width = minOf(maxWidth * PREVIEW_MAX_WIDTH, screenHeight * PREVIEW_MAX_HEIGHT * aspectRatio.ratio)
        Box(
            modifier = Modifier
                .width(width)
                .aspectRatio(aspectRatio.ratio)
                .clip(Radius.largeShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = stringResource(CommonR.string.share_stats_title),
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                CircularProgressIndicator(modifier = Modifier.size(IconSize.large))
            }
        }
    }
}

@Composable
private fun GroupTitle(text: String) {
    Text(text = text, style = TextStyles.sectionTitle, color = MaterialTheme.colorScheme.onSurface)
}

/**
 * A titled row of choices. The row scrolls under the screen edge (its gutter is content
 * padding), so an option past the edge reads as "more to scroll" instead of being cut at
 * the margin.
 */
@Composable
private fun <T> ChoiceGroup(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.padding(horizontal = Spacing.screenGutter)) { GroupTitle(title) }
        Spacer(modifier = Modifier.height(Spacing.sm))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenGutter),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(text = label(option), maxLines = 1) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer),
                )
            }
        }
    }
}

/** Artwork decode size: covers the largest artwork a 1080 px card draws (album tiles, ~190 px). */
private const val ARTWORK_PX = 320
private const val PREVIEW_MAX_WIDTH = 0.72f
private const val PREVIEW_MAX_HEIGHT = 0.45f
private val ACTION_HEIGHT = 52.dp
