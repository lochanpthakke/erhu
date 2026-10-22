package com.lochanpthakke.empee3.feature.library.components

import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
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
import com.lochanpthakke.empee3.core.designsystem.artwork.AlbumArtwork
import com.lochanpthakke.empee3.core.designsystem.foundation.Spacing
import com.lochanpthakke.empee3.core.designsystem.theme.EmPee3Glass
import com.lochanpthakke.empee3.core.designsystem.theme.GlassLevel
import com.lochanpthakke.empee3.core.designsystem.theme.glassPanel
import com.lochanpthakke.empee3.core.designsystem.theme.glassPressEffect
import com.lochanpthakke.empee3.core.designsystem.theme.glassRippleConfiguration
import com.lochanpthakke.empee3.core.model.AnimatedArtOrientation
import com.lochanpthakke.empee3.feature.library.LiveCoverUi

private val BUTTON_SIZE = 40.dp
private val POPUP_MAX_WIDTH = 300.dp
private val POPUP_MARGIN = 16.dp
private val POPUP_GAP = 8.dp
private val THUMBNAIL_SIZE = 64.dp
private val POPUP_SHAPE = RoundedCornerShape(24.dp)

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
                    LiveCoverCard(state, artworkUri, onDownload, onCancel, onRemove)
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
    val interactionSource = remember { MutableInteractionSource() }
    val colors =
        IconButtonDefaults.iconButtonColors(
            containerColor = EmPee3Glass.containerColor(MaterialTheme.colorScheme.surfaceVariant),
            contentColor = MaterialTheme.colorScheme.onSurface,
        )
    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier =
                Modifier
                    .size(BUTTON_SIZE)
                    .glassPressEffect(interactionSource, CircleShape)
                    .glassPanel(CircleShape, level = GlassLevel.Strong, elevated = false),
        )
        CompositionLocalProvider(LocalRippleConfiguration provides glassRippleConfiguration()) {
            IconButton(onClick = onClick, colors = colors, interactionSource = interactionSource) {
                when (state) {
                    is LiveCoverUi.Downloading ->
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    is LiveCoverUi.Saved -> Icon(Icons.Filled.DownloadDone, contentDescription = "Live cover saved")
                    else -> Icon(Icons.Filled.Download, contentDescription = "Download live cover")
                }
            }
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
) {
    val width = min(POPUP_MAX_WIDTH, LocalConfiguration.current.screenWidthDp.dp - POPUP_MARGIN * 2)
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
    }
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
                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
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
