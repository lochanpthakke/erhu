package com.erhu.erhu.feature.library.transition

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.erhu.erhu.core.common.R
import com.erhu.erhu.core.common.locale.AppLocaleManager
import com.erhu.erhu.core.common.locale.LocaleFormatters
import com.erhu.erhu.core.designsystem.foundation.IconSize
import com.erhu.erhu.core.designsystem.foundation.Spacing
import com.erhu.erhu.core.model.CollectionKind
import com.erhu.erhu.core.model.CollectionRef
import com.erhu.erhu.core.model.TransitionChoice
import com.erhu.erhu.core.model.TransitionConfig
import com.erhu.erhu.core.playback.transition.specOrNull

/** Stateless row: switch clicks never invoke the surrounding Studio navigation action. */
@Composable
internal fun CollectionTransitionRow(
    collection: CollectionRef,
    config: TransitionConfig,
    onEnabledChange: (Boolean) -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = AppLocaleManager.activeLocale(LocalContext.current)
    val isAlbum = collection.kind == CollectionKind.ALBUM
    val choice = config.collection(collection)
    val offForCollection = stringResource(if (isAlbum) R.string.transition_off_for_album else R.string.transition_off_for_playlist)
    val summary =
        when {
            !config.collectionEnabled(collection) -> offForCollection
            !config.global.masterEnabled -> stringResource(R.string.transition_master_off_summary)
            choice == TransitionChoice.Off -> offForCollection
            choice is TransitionChoice.Custom -> localizedSpecSummary(choice.spec)
            else -> stringResource(R.string.transition_default_prefix, localizedSpecSummary(config.global.default.specOrNull()))
        }
    val custom = config.pairs.keys.count { it.collection == collection }
    val openLabel = stringResource(R.string.transition_cd_open_studio)
    val enableSwitchLabel =
        stringResource(if (isAlbum) R.string.transition_cd_enable_for_album else R.string.transition_cd_enable_for_playlist)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClickLabel = openLabel, onClick = onOpen)
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Tune,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(IconSize.small),
        )
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.menu_transition_studio), style = MaterialTheme.typography.titleSmall)
            Text(
                if (custom > 0) {
                    stringResource(R.string.transition_summary_custom_count, summary, LocaleFormatters.formatInteger(custom, locale))
                } else {
                    summary
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Switch(
            checked = config.collectionEnabled(collection),
            onCheckedChange = onEnabledChange,
            modifier = Modifier.semantics { contentDescription = enableSwitchLabel },
        )
        Spacer(Modifier.width(Spacing.xs))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** One subscription per collection page, not one per visible track or playback position tick. */
@Composable
internal fun rememberCollectionTransitionPairs(
    collection: CollectionRef,
    tracks: List<StudioTrack>,
    viewModel: TransitionStudioViewModel,
): List<StudioPair> {
    val config by viewModel.config.collectAsState()
    return remember(collection, tracks, config) {
        if (config.collectionEnabled(collection)) studioPairs(collection, tracks, config) else emptyList()
    }
}

/** The chip belongs to this exact outgoing/incoming occurrence pair, including duplicate songs. */
@Composable
internal fun CollectionTransitionConnector(
    pair: StudioPair,
    onOpen: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = Spacing.lg + IconSize.small),
        verticalArrangement = Arrangement.Center,
    ) {
        val line =
            Modifier
                .padding(start = Spacing.sm)
                .width(1.dp)
                .height(Spacing.xs)
                .background(MaterialTheme.colorScheme.outlineVariant)
        val editDescription = stringResource(R.string.transition_cd_edit_pair, pair.from.song.title, pair.to.song.title)
        Box(line)
        AssistChip(
            onClick = onOpen,
            label = { Text(localizedPairLabel(pair.choice)) },
            leadingIcon = { Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.size(IconSize.small)) },
            trailingIcon = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
            modifier =
                Modifier.semantics {
                    contentDescription = editDescription
                },
        )
        Box(line)
    }
}
