package com.erhu.erhu.feature.library.components

import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.erhu.erhu.core.common.locale.AppLocaleManager
import com.erhu.erhu.core.common.locale.LocaleFormatters
import com.erhu.erhu.core.common.locale.localizedMessage
import com.erhu.erhu.core.database.repository.LiveCoverCheck
import com.erhu.erhu.core.database.repository.LiveCoverVariant
import com.erhu.erhu.core.designsystem.artwork.AlbumArtwork
import com.erhu.erhu.core.designsystem.component.GlassMenu
import com.erhu.erhu.core.designsystem.foundation.Spacing
import com.erhu.erhu.core.model.AnimatedArtOrientation
import com.erhu.erhu.core.model.ArtworkCompressionState
import com.erhu.erhu.feature.library.LiveCoverFailureKind
import com.erhu.erhu.feature.library.LiveCoverUi
import com.erhu.erhu.core.common.R as CommonR

private val POPUP_MAX_WIDTH = 300.dp
private val POPUP_MARGIN = 16.dp
private val THUMBNAIL_SIZE = 64.dp

private enum class BitrateTier { LOW, MEDIUM, HIGH }

private val DownloadArrowBarIcon: ImageVector by lazy {
    ImageVector
        .Builder(
            name = "DownloadArrowBar",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Unspecified),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.5f,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(12f, 3f)
                lineTo(12f, 14.5f)
            }
            path(
                fill = SolidColor(Color.Unspecified),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(6.5f, 9.5f)
                lineTo(12f, 15f)
                lineTo(17.5f, 9.5f)
            }
            path(
                fill = SolidColor(Color.Unspecified),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.5f,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(5f, 20f)
                lineTo(19f, 20f)
            }
        }.build()
}

/**
 * The album page's live cover control: a small glass button (download arrow; a tick once saved)
 * that opens a popup beside it with a preview, the cover's resolution and size, and Download,
 * Cancel, Retry or Remove. Draws nothing while [state] is [LiveCoverUi.Hidden].
 */
@Composable
fun LiveCoverControl(
    state: LiveCoverUi,
    artworkUri: String?,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
    onSelectVariant: ((LiveCoverVariant) -> Unit)? = null,
    modifier: Modifier = Modifier,
    compression: ArtworkCompressionState = ArtworkCompressionState(),
    onCompress: () -> Unit = {},
    onCancelCompression: () -> Unit = {},
) {
    var open by rememberSaveable { mutableStateOf(false) }
    AnimatedVisibility(
        visible = state != LiveCoverUi.Hidden,
        enter = fadeIn() + scaleIn(initialScale = 0.8f),
        exit = fadeOut() + scaleOut(targetScale = 0.8f),
        modifier = modifier,
    ) {
        Box {
            LiveCoverButton(state = state, onClick = { open = true })
            GlassMenu(
                expanded = open && state != LiveCoverUi.Hidden,
                onDismissRequest = { open = false },
                label = stringResource(CommonR.string.live_cover_title),
            ) {
                LiveCoverCard(
                    state,
                    artworkUri,
                    onDownload,
                    onCancel,
                    onRemove,
                    onSelectVariant,
                    compression,
                    onCompress,
                    onCancelCompression,
                )
            }
        }
    }
}

@Composable
private fun LiveCoverButton(
    state: LiveCoverUi,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        colors =
            IconButtonDefaults.iconButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
    ) {
        when (state) {
            is LiveCoverUi.Downloading ->
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            is LiveCoverUi.Saved ->
                Icon(Icons.Filled.DownloadDone, contentDescription = stringResource(CommonR.string.live_cover_cd_saved))
            is LiveCoverUi.RateLimited ->
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = DownloadArrowBarIcon,
                        contentDescription = stringResource(CommonR.string.live_cover_cd_rate_limited),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    )
                    Icon(
                        imageVector = Icons.Filled.Block,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp).align(Alignment.BottomEnd),
                    )
                }
            else -> Icon(DownloadArrowBarIcon, contentDescription = stringResource(CommonR.string.live_cover_cd_download))
        }
    }
}

@Composable
private fun LiveCoverCard(
    state: LiveCoverUi,
    artworkUri: String?,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
    onSelectVariant: ((LiveCoverVariant) -> Unit)? = null,
    compression: ArtworkCompressionState,
    onCompress: () -> Unit,
    onCancelCompression: () -> Unit,
) {
    val width = min(POPUP_MAX_WIDTH, LocalConfiguration.current.screenWidthDp.dp - POPUP_MARGIN * 2)
    val context = LocalContext.current
    val offer =
        when (state) {
            is LiveCoverUi.Offer -> state.offer
            is LiveCoverUi.Downloading -> state.offer
            is LiveCoverUi.Failed -> state.offer
            LiveCoverUi.Hidden, is LiveCoverUi.Saved, is LiveCoverUi.RateLimited -> null
        }
    val variants = offer?.variants.orEmpty()

    var expanded by remember { mutableStateOf(false) }
    var selectedBitrate by remember { mutableStateOf(BitrateTier.HIGH) }

    val filteredVariants =
        remember(variants, selectedBitrate) {
            filterVariantsByBitrate(variants, selectedBitrate)
        }

    Column(
        modifier =
            Modifier
                .width(width)
                .padding(Spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                AlbumArtwork(
                    artworkUri = artworkUri,
                    modifier = Modifier.size(THUMBNAIL_SIZE).clip(RoundedCornerShape(12.dp)),
                )
                Text(
                    text = stringResource(CommonR.string.live_cover_badge),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier =
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(4.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
            Spacer(modifier = Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(CommonR.string.live_cover_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = details(state),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.md))
        if (!compression.running) LiveCoverActions(state, onDownload, onCancel, onRemove)
        if (state is LiveCoverUi.Saved) {
            ArtworkCompressionControl(compression, onCompress, onCancelCompression)
        }

        if (variants.isNotEmpty() && state !is LiveCoverUi.Saved) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription =
                            stringResource(
                                if (expanded) {
                                    CommonR.string.live_cover_cd_collapse_options
                                } else {
                                    CommonR.string.live_cover_cd_expand_options
                                },
                            ),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs)) {
                    Text(
                        text = stringResource(CommonR.string.live_cover_bitrate_label),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.xs),
                    )
                    // Bitrate Selector: Low | Medium | High
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(12.dp),
                                ).padding(2.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        BitrateTier.entries.forEach { tier ->
                            val isSelected = selectedBitrate == tier
                            val label =
                                stringResource(
                                    when (tier) {
                                        BitrateTier.LOW -> CommonR.string.live_cover_bitrate_low
                                        BitrateTier.MEDIUM -> CommonR.string.live_cover_bitrate_medium
                                        BitrateTier.HIGH -> CommonR.string.live_cover_bitrate_high
                                    },
                                )
                            Surface(
                                onClick = { selectedBitrate = tier },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                contentColor =
                                    if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 6.dp),
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.sm))

                    // 2-Column Resolution Grid
                    val chunked = filteredVariants.chunked(2)
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        chunked.forEach { rowVariants ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                            ) {
                                rowVariants.forEach { v ->
                                    val isSelected = offer?.selectedVariant?.variantUrl == v.variantUrl
                                    val localizedContext = AppLocaleManager.localizedContext(context)
                                    val locale = AppLocaleManager.activeLocale(localizedContext)
                                    val sizeStr =
                                        v.bytes?.let { Formatter.formatShortFileSize(localizedContext, it) }
                                            ?: stringResource(
                                                CommonR.string.format_size_mb,
                                                LocaleFormatters.formatDecimal(v.estimatedSizeMb.toDouble(), 1, locale),
                                            )
                                    Surface(
                                        onClick = { onSelectVariant?.invoke(v) },
                                        shape = RoundedCornerShape(10.dp),
                                        color =
                                            if (isSelected) {
                                                MaterialTheme.colorScheme.primaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.surfaceVariant
                                                    .copy(
                                                        alpha = 0.4f,
                                                    )
                                            },
                                        contentColor =
                                            if (isSelected) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            },
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                        ) {
                                            Text(
                                                text = v.resolutionText,
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                            )
                                            Text(
                                                text = sizeStr,
                                                style = MaterialTheme.typography.bodySmall,
                                                color =
                                                    if (isSelected) {
                                                        MaterialTheme.colorScheme.onPrimaryContainer.copy(
                                                            alpha = 0.8f,
                                                        )
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                    },
                                            )
                                        }
                                    }
                                }
                                if (rowVariants.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun filterVariantsByBitrate(
    allVariants: List<LiveCoverVariant>,
    tier: BitrateTier,
): List<LiveCoverVariant> {
    if (allVariants.isEmpty()) return emptyList()
    val grouped = allVariants.groupBy { it.resolutionText }
    return grouped.values
        .mapNotNull { group ->
            val sorted = group.sortedByDescending { it.bandwidth }
            when (tier) {
                BitrateTier.HIGH -> sorted.firstOrNull()
                BitrateTier.MEDIUM -> sorted[sorted.size / 2]
                BitrateTier.LOW -> sorted.lastOrNull()
            }
        }.sortedByDescending { (it.width ?: 0) * (it.height ?: 0) }
}

@Composable
private fun LiveCoverActions(
    state: LiveCoverUi,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
) {
    when (state) {
        is LiveCoverUi.Offer ->
            FilledTonalButton(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                Icon(DownloadArrowBarIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(Spacing.sm))
                Text(stringResource(CommonR.string.live_cover_download))
            }
        is LiveCoverUi.Downloading ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(CommonR.string.live_cover_downloading), style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                TextButton(onClick = onCancel) { Text(stringResource(CommonR.string.action_cancel)) }
            }
        is LiveCoverUi.Failed ->
            Column {
                Text(
                    text = localizedFailureMessage(state),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
                FilledTonalButton(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(CommonR.string.action_try_again))
                }
            }
        is LiveCoverUi.Saved ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = stringResource(CommonR.string.live_cover_saved_on_phone),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRemove) { Text(stringResource(CommonR.string.action_remove)) }
            }
        is LiveCoverUi.RateLimited ->
            Column {
                val locale = AppLocaleManager.activeLocale(LocalContext.current)
                val text =
                    state.minutes?.let {
                        pluralStringResource(
                            CommonR.plurals.apple_rate_limit_wait,
                            it,
                            LocaleFormatters.formatInteger(it, locale),
                        )
                    } ?: state.message
                Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        LiveCoverUi.Hidden -> Unit
    }
}

@Composable
private fun localizedFailureMessage(state: LiveCoverUi.Failed): String {
    val context = LocalContext.current
    val locale = AppLocaleManager.activeLocale(context)
    return when (state.kind) {
        LiveCoverFailureKind.NO_LONGER_OFFERED -> stringResource(CommonR.string.live_cover_no_longer_offered)
        LiveCoverFailureKind.RATE_LIMITED ->
            state.rateLimitMinutes?.let {
                pluralStringResource(
                    CommonR.plurals.apple_rate_limit_wait,
                    it,
                    LocaleFormatters.formatInteger(it, locale),
                )
            } ?: state.message
        LiveCoverFailureKind.DOWNLOAD_FAILED -> {
            val detail = state.error?.localizedMessage(context) ?: state.error?.message
            if (detail.isNullOrBlank()) {
                stringResource(CommonR.string.live_cover_download_failed)
            } else {
                stringResource(CommonR.string.live_cover_download_failed_detail, detail)
            }
        }
    }
}

/** "Apple Music · 1080 × 1080 · 26.9 MB": what is known, nothing guessed. */
@Composable
private fun details(state: LiveCoverUi): String {
    val context = AppLocaleManager.localizedContext(LocalContext.current)
    val size: (Long) -> String = { Formatter.formatShortFileSize(context, it) }
    val parts =
        when (state) {
            is LiveCoverUi.Saved ->
                listOf(orientationName(state.saved.orientation), size(state.saved.bytes))
            is LiveCoverUi.Offer -> offerParts(state.offer, size)
            is LiveCoverUi.Downloading -> offerParts(state.offer, size)
            is LiveCoverUi.Failed -> offerParts(state.offer, size)
            is LiveCoverUi.RateLimited -> listOf(stringResource(CommonR.string.live_cover_rate_limited))
            LiveCoverUi.Hidden -> emptyList()
        }
    return (listOf(stringResource(CommonR.string.live_cover_source_apple_music)) + parts).joinToString(" · ")
}

@Composable
private fun offerParts(
    offer: LiveCoverCheck.Downloadable,
    size: (Long) -> String,
): List<String> {
    val locale = AppLocaleManager.activeLocale(LocalContext.current)
    val dimensionOrOrientation =
        if (offer.width != null && offer.height != null) {
            stringResource(
                CommonR.string.live_cover_dimensions,
                LocaleFormatters.formatInteger(offer.width!!, locale),
                LocaleFormatters.formatInteger(offer.height!!, locale),
            )
        } else {
            orientationName(offer.orientation)
        }
    return listOfNotNull(dimensionOrOrientation, offer.bytes?.let(size))
}

@Composable
private fun orientationName(orientation: AnimatedArtOrientation): String =
    stringResource(
        when (orientation) {
            AnimatedArtOrientation.SQUARE_1_1 -> CommonR.string.live_cover_orientation_square
            AnimatedArtOrientation.TALL_3_4 -> CommonR.string.live_cover_orientation_tall
        },
    )

/**
 * Below the anchor with their end edges aligned (the button sits at the cover's end), kept
 * [marginPx] inside the window; above the anchor when there is no room below.
 */
internal fun popupBesideAnchor(
    anchor: IntRect,
    window: IntSize,
    layoutDirection: LayoutDirection,
    popup: IntSize,
    marginPx: Int,
    gapPx: Int,
): IntOffset {
    val alignedX = if (layoutDirection == LayoutDirection.Ltr) anchor.right - popup.width else anchor.left
    val x = alignedX.coerceIn(marginPx, (window.width - marginPx - popup.width).coerceAtLeast(marginPx))
    val below = anchor.bottom + gapPx
    val above = anchor.top - gapPx - popup.height
    val y =
        when {
            below + popup.height <= window.height - marginPx -> below
            above >= marginPx -> above
            else -> (window.height - marginPx - popup.height).coerceAtLeast(marginPx)
        }
    return IntOffset(x, y)
}
