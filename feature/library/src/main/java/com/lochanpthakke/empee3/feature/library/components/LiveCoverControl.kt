package com.lochanpthakke.empee3.feature.library.components

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
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.lochanpthakke.empee3.core.database.repository.LiveCoverCheck
import com.lochanpthakke.empee3.core.database.repository.LiveCoverVariant
import com.lochanpthakke.empee3.core.designsystem.artwork.AlbumArtwork
import com.lochanpthakke.empee3.core.designsystem.foundation.Spacing
import com.lochanpthakke.empee3.core.designsystem.theme.EmPee3Glass
import com.lochanpthakke.empee3.core.designsystem.theme.GlassLevel
import com.lochanpthakke.empee3.core.designsystem.theme.glassPanel
import com.lochanpthakke.empee3.core.model.AnimatedArtOrientation
import com.lochanpthakke.empee3.feature.library.LiveCoverUi

private val POPUP_MAX_WIDTH = 300.dp
private val POPUP_MARGIN = 16.dp
private val POPUP_GAP = 8.dp
private val THUMBNAIL_SIZE = 64.dp
private val POPUP_SHAPE = RoundedCornerShape(24.dp)

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
            if (open && state != LiveCoverUi.Hidden) {
                val density = LocalDensity.current
                Popup(
                    popupPositionProvider =
                        remember(density) {
                            with(density) { BesideAnchor(POPUP_MARGIN.roundToPx(), POPUP_GAP.roundToPx()) }
                        },
                    onDismissRequest = { open = false },
                    properties = PopupProperties(focusable = true),
                ) {
                    LiveCoverCard(state, artworkUri, onDownload, onCancel, onRemove, onSelectVariant)
                }
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
            is LiveCoverUi.Saved -> Icon(Icons.Filled.DownloadDone, contentDescription = "Live cover saved")
            is LiveCoverUi.RateLimited ->
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = DownloadArrowBarIcon,
                        contentDescription = "Rate limited",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    )
                    Icon(
                        imageVector = Icons.Filled.Block,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp).align(Alignment.BottomEnd),
                    )
                }
            else -> Icon(DownloadArrowBarIcon, contentDescription = "Download live cover")
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
) {
    val width = min(POPUP_MAX_WIDTH, LocalConfiguration.current.screenWidthDp.dp - POPUP_MARGIN * 2)
    val context = LocalContext.current
    val offer = extractOffer(state)
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
                // The popup is its own window: tinted glass without the page's frost behind it.
                .glassPanel(POPUP_SHAPE, level = GlassLevel.Strong, backdrop = null)
                .background(EmPee3Glass.containerColor(MaterialTheme.colorScheme.surfaceContainerHigh), POPUP_SHAPE)
                .padding(Spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                AlbumArtwork(
                    artworkUri = artworkUri,
                    modifier = Modifier.size(THUMBNAIL_SIZE).clip(RoundedCornerShape(12.dp)),
                )
                Text(
                    text = "LIVE",
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
                Text(text = "Live cover", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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
        LiveCoverActions(state, onDownload, onCancel, onRemove)

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
                        contentDescription = if (expanded) "Collapse options" else "Expand resolution options",
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
                        text = "Bitrate",
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
                                when (tier) {
                                    BitrateTier.LOW -> "Low"
                                    BitrateTier.MEDIUM -> "Medium"
                                    BitrateTier.HIGH -> "High"
                                }
                            Surface(
                                onClick = { selectedBitrate = tier },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
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
                                    val sizeStr =
                                        v.bytes?.let { Formatter.formatShortFileSize(context, it) }
                                            ?: String.format(java.util.Locale.US, "%.1f MB", v.estimatedSizeMb)
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
                                        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
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

private fun extractOffer(state: LiveCoverUi): LiveCoverCheck.Downloadable? =
    when (state) {
        is LiveCoverUi.Offer -> state.offer
        is LiveCoverUi.Downloading -> state.offer
        is LiveCoverUi.Failed -> state.offer
        LiveCoverUi.Hidden, is LiveCoverUi.Saved, is LiveCoverUi.RateLimited -> null
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
                Text("Download")
            }
        is LiveCoverUi.Downloading ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Downloading…", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
        is LiveCoverUi.Failed ->
            Column {
                Text(state.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(Spacing.sm))
                FilledTonalButton(onClick = onDownload, modifier = Modifier.fillMaxWidth()) { Text("Try again") }
            }
        is LiveCoverUi.Saved ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = "Saved on this phone",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRemove) { Text("Remove") }
            }
        is LiveCoverUi.RateLimited ->
            Column {
                Text(state.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        LiveCoverUi.Hidden -> Unit
    }
}

/** "Apple Music · 1080 × 1080 · 26.9 MB": what is known, nothing guessed. */
@Composable
private fun details(state: LiveCoverUi): String {
    val context = LocalContext.current
    val size: (Long) -> String = { Formatter.formatShortFileSize(context, it) }
    val parts =
        when (state) {
            is LiveCoverUi.Saved ->
                listOf(orientationName(state.saved.orientation), size(state.saved.bytes))
            is LiveCoverUi.Offer -> offerParts(state.offer, size)
            is LiveCoverUi.Downloading -> offerParts(state.offer, size)
            is LiveCoverUi.Failed -> offerParts(state.offer, size)
            is LiveCoverUi.RateLimited -> listOf("Rate Limited")
            LiveCoverUi.Hidden -> emptyList()
        }
    return (listOf("Apple Music") + parts).joinToString(" · ")
}

private fun offerParts(
    offer: LiveCoverCheck.Downloadable,
    size: (Long) -> String,
): List<String> =
    listOfNotNull(
        if (offer.width != null && offer.height != null) "${offer.width} × ${offer.height}" else orientationName(offer.orientation),
        offer.bytes?.let(size),
    )

private fun orientationName(orientation: AnimatedArtOrientation): String =
    when (orientation) {
        AnimatedArtOrientation.SQUARE_1_1 -> "Square"
        AnimatedArtOrientation.TALL_3_4 -> "Tall"
    }

private class BesideAnchor(
    private val marginPx: Int,
    private val gapPx: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = popupBesideAnchor(anchorBounds, windowSize, layoutDirection, popupContentSize, marginPx, gapPx)
}

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
