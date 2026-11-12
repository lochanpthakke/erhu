package com.erhu.erhu.feature.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.erhu.erhu.core.common.locale.AppLocaleManager
import com.erhu.erhu.core.common.locale.LocaleFormatters
import com.erhu.erhu.core.designsystem.component.AppScaffold
import com.erhu.erhu.core.designsystem.component.GlassAlertDialog
import com.erhu.erhu.core.designsystem.component.OverlayHeader
import com.erhu.erhu.core.designsystem.component.SectionHeading
import com.erhu.erhu.core.designsystem.component.TopBarNavigation
import com.erhu.erhu.core.designsystem.component.forScrollingContent
import com.erhu.erhu.core.designsystem.component.groupSurface
import com.erhu.erhu.core.designsystem.foundation.Spacing
import com.erhu.erhu.core.network.artwork.ArtworkUpgradeMatch
import com.erhu.erhu.core.common.R as CommonR

@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod")
internal fun ArtworkUpgradeScreen(
    controller: ArtworkUpgradeController,
    kind: ArtworkUpgradeKind,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsState()
    val locale = AppLocaleManager.activeLocale(LocalContext.current)
    var selected by remember(kind) { mutableStateOf<ArtworkUpgradeResult?>(null) }
    val back = { if (!state.saving) onBack() }
    BackHandler(onBack = back)
    LaunchedEffect(kind) { controller.select(kind) }
    DisposableEffect(controller) { onDispose { controller.stop() } }
    LaunchedEffect(state.results) {
        if (selected !in state.results) selected = null
    }
    AppScaffold(
        topBar = {
            OverlayHeader(
                navigation = TopBarNavigation.Detail(back),
                title =
                    stringResource(
                        if (kind == ArtworkUpgradeKind.ALBUMS) {
                            CommonR.string.artwork_upgrade_covers
                        } else {
                            CommonR.string.artwork_upgrade_photos
                        },
                    ),
            )
        },
        modifier = modifier,
    ) { padding ->
        val scroll = padding.forScrollingContent(topOverlay = true)
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(scroll.container),
            contentPadding =
                PaddingValues(
                    start = Spacing.screenGutter,
                    end = Spacing.screenGutter,
                    top = scroll.contentTop + Spacing.md,
                    bottom = scroll.contentBottom + Spacing.xl,
                ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item {
                Column(Modifier.fillMaxWidth().groupSurface().padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Text(
                        stringResource(
                            CommonR.string.artwork_upgrade_intro,
                            LocaleFormatters.formatInteger(ArtworkUpgradeMatch.MAX_SAVED_PIXELS, locale),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    val checkedText = LocaleFormatters.formatInteger(state.checked, locale)
                    val totalText = LocaleFormatters.formatInteger(state.total, locale)
                    if (state.scanning) {
                        LinearProgressIndicator(progress = {
                            if (state.total ==
                                0
                            ) {
                                0f
                            } else {
                                state.checked.toFloat() / state.total
                            }
                        }, modifier = Modifier.fillMaxWidth())
                        Text(stringResource(CommonR.string.artwork_upgrade_checked_progress, checkedText, totalText))
                        TextButton(onClick = controller::stop) {
                            Text(stringResource(CommonR.string.artwork_upgrade_stop_search))
                        }
                    } else {
                        Button(onClick = {
                            selected = null
                            controller.search(kind)
                        }, enabled = !state.saving) {
                            Text(
                                stringResource(
                                    if (state.checked > 0 || state.finished) {
                                        CommonR.string.artwork_upgrade_search_again
                                    } else {
                                        CommonR.string.artwork_upgrade_find_matches
                                    },
                                ),
                            )
                        }
                        if (state.finished) {
                            val reviewCountText =
                                pluralStringResource(
                                    CommonR.plurals.artwork_upgrades_to_review,
                                    state.results.size,
                                    LocaleFormatters.formatInteger(state.results.size, locale),
                                )
                            Text(
                                stringResource(
                                    CommonR.string.artwork_upgrade_checked_summary,
                                    checkedText,
                                    totalText,
                                    reviewCountText,
                                ),
                            )
                        }
                    }
                    if (state.skipped >
                        0
                    ) {
                        Text(
                            pluralStringResource(
                                CommonR.plurals.artwork_upgrade_skipped_count,
                                state.skipped,
                                LocaleFormatters.formatInteger(state.skipped, locale),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    state.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    if (state.saving) Text(stringResource(CommonR.string.artwork_upgrade_saving))
                }
            }
            item { SectionHeading(title = stringResource(CommonR.string.artwork_upgrade_section_matches)) }
            items(state.results, key = { it.target.id }) { result ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .groupSurface()
                        .clickable(enabled = !state.saving, role = Role.Button) {
                            controller.stop()
                            selected = result
                        }.padding(Spacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    AsyncImage(
                        result.preview,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(56.dp).clip(MaterialTheme.shapes.small),
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(result.target.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            result.target.album?.artist ?: stringResource(CommonR.string.artwork_upgrade_artist_photo),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            stringResource(
                                CommonR.string.artwork_upgrade_dims_arrow,
                                LocaleFormatters.formatInteger(result.original.width, locale),
                                LocaleFormatters.formatInteger(result.original.height, locale),
                                LocaleFormatters.formatInteger(result.replacement.width, locale),
                                LocaleFormatters.formatInteger(result.replacement.height, locale),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(stringResource(CommonR.string.artwork_upgrade_review_hint), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            if (state.finished && state.results.isEmpty()) {
                item { Text(stringResource(CommonR.string.artwork_upgrade_none_found)) }
            }
        }
    }
    selected?.let { result ->
        GlassAlertDialog(
            onDismissRequest = { if (!state.saving) selected = null },
            title = { Text(stringResource(CommonR.string.artwork_upgrade_dialog_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Text(result.target.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(
                            CommonR.string.artwork_upgrade_current_dims,
                            LocaleFormatters.formatInteger(result.original.width, locale),
                            LocaleFormatters.formatInteger(result.original.height, locale),
                        ),
                    )
                    AsyncImage(
                        result.preview,
                        stringResource(CommonR.string.artwork_upgrade_cd_current),
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                        contentScale = ContentScale.Fit,
                    )
                    Text(
                        stringResource(
                            CommonR.string.artwork_upgrade_replacement_dims,
                            LocaleFormatters.formatInteger(result.replacement.width, locale),
                            LocaleFormatters.formatInteger(result.replacement.height, locale),
                            result.source,
                        ),
                    )
                    AsyncImage(
                        result.file,
                        stringResource(CommonR.string.artwork_upgrade_cd_replacement),
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                        contentScale = ContentScale.Fit,
                    )
                    Text(stringResource(CommonR.string.artwork_upgrade_confirm_prompt))
                    state.message?.let { Text(it) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    controller.apply(result)
                }, enabled = !state.saving && !state.scanning) {
                    Text(
                        stringResource(
                            if (state.saving) {
                                CommonR.string.action_saving
                            } else {
                                CommonR.string.artwork_upgrade_use_image
                            },
                        ),
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { selected = null }, enabled = !state.saving) {
                    Text(stringResource(CommonR.string.artwork_upgrade_keep_current))
                }
            },
        )
    }
}
