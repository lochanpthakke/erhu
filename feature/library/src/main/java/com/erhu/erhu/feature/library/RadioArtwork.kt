package com.erhu.erhu.feature.library

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.erhu.erhu.core.designsystem.artwork.AlbumArtwork
import com.erhu.erhu.core.designsystem.component.ARTWORK_PRESSED_SCALE
import com.erhu.erhu.core.designsystem.component.pressScale
import com.erhu.erhu.core.designsystem.foundation.Radius
import com.erhu.erhu.core.designsystem.foundation.Spacing
import com.erhu.erhu.core.designsystem.foundation.TextStyles
import com.erhu.erhu.core.common.R as CommonR

/** Same footprint and feedback as AlbumTile; only the cover has an editorial treatment. */
@Composable
internal fun RadioTile(
    radio: FeaturedSetlist.RadioPick,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val openDescription = stringResource(CommonR.string.radio_cd_open, radio.album.title, radio.album.artist)
    Column(
        modifier =
            modifier
                .semantics(mergeDescendants = true) {
                    contentDescription = openDescription
                }.clickable(interactionSource = interaction, indication = LocalIndication.current, role = Role.Button, onClick = onClick),
    ) {
        RadioArtwork(radio, Modifier.fillMaxWidth().aspectRatio(1f).pressScale(interaction, ARTWORK_PRESSED_SCALE))
        Spacer(Modifier.height(Spacing.sm))
        Text(
            stringResource(CommonR.string.radio_title_format, radio.identity),
            style = TextStyles.tileTitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            stringResource(CommonR.string.radio_subtitle_based_on, radio.album.title),
            style = TextStyles.tileSubtitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Local cached images, fixed proportions and one solid theme tone: no bitmap composition. */
@Composable
internal fun RadioArtwork(
    radio: FeaturedSetlist.RadioPick,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val (background, ink) =
        when (Math.floorMod(radio.album.id, 3)) {
            0 -> colors.primaryContainer to colors.onPrimaryContainer
            1 -> colors.secondaryContainer to colors.onSecondaryContainer
            else -> colors.tertiaryContainer to colors.onTertiaryContainer
        }
    BoxWithConstraints(modifier.clip(Radius.mediumShape).background(background)) {
        val side = maxWidth
        // Supporting records are from this artist's actual featured albums, behind the portrait.
        radio.supportingArtwork.forEachIndexed { index, uri ->
            AlbumArtwork(
                artworkUri = uri,
                modifier =
                    Modifier
                        .offset(x = side * if (index == 0) -0.09f else 0.75f, y = side * 0.31f)
                        .size(side * 0.34f)
                        .clip(CircleShape),
            )
        }
        AlbumArtwork(
            artworkUri = radio.artworkUri,
            placeholderIcon = Icons.Default.GraphicEq,
            modifier = Modifier.offset(x = side * 0.24f, y = side * 0.18f).size(side * 0.52f).clip(CircleShape),
        )
        RadioBadgeHeader(ink)
        RadioIdentityLabel(radio.identity, ink, Modifier.align(Alignment.BottomStart).height(side * 0.30f))
    }
}

@Composable
private fun RadioBadgeHeader(ink: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.GraphicEq, contentDescription = null, tint = ink, modifier = Modifier.size(16.dp))
        Spacer(Modifier.weight(1f))
        Text(
            stringResource(CommonR.string.radio_badge),
            color = ink,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.4.sp,
        )
    }
}

@Composable
private fun RadioIdentityLabel(
    identity: String,
    ink: Color,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth().padding(horizontal = Spacing.sm)) {
        Text(
            identity,
            color = ink,
            fontSize = 20.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.5).sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.CenterStart),
        )
    }
}
