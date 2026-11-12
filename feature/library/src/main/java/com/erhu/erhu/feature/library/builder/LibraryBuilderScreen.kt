package com.erhu.erhu.feature.library.builder

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.erhu.erhu.core.common.locale.AppLocaleManager
import com.erhu.erhu.core.common.locale.LocaleFormatters
import com.erhu.erhu.core.database.builder.ProposedMove
import com.erhu.erhu.core.designsystem.component.ActionButton
import com.erhu.erhu.core.designsystem.component.AppScaffold
import com.erhu.erhu.core.designsystem.component.GlassAlertDialog
import com.erhu.erhu.core.designsystem.component.GroupToggleRow
import com.erhu.erhu.core.designsystem.component.LocalCollapsingTitle
import com.erhu.erhu.core.designsystem.component.OverlayHeader
import com.erhu.erhu.core.designsystem.component.ProgressBar
import com.erhu.erhu.core.designsystem.component.ReportTitleScroll
import com.erhu.erhu.core.designsystem.component.SectionHeading
import com.erhu.erhu.core.designsystem.component.TopBarNavigation
import com.erhu.erhu.core.designsystem.component.forScrollingContent
import com.erhu.erhu.core.designsystem.component.groupSurface
import com.erhu.erhu.core.designsystem.component.rememberCollapsingTitleState
import com.erhu.erhu.core.designsystem.foundation.Spacing
import com.erhu.erhu.core.common.R as CommonR

/**
 * Library Builder, styled like the Settings pages: the app's floating header (round glass back
 * button; the title merges into it on scroll) over one scrolling list of group surfaces.
 */
@Composable
fun LibraryBuilderScreen(
    viewModel: LibraryBuilderViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val forceReorganize by viewModel.forceReorganize.collectAsState()
    var showResolveDialogFor by remember { mutableStateOf<ProposedMove?>(null) }

    showResolveDialogFor?.let { move ->
        ResolveDialog(
            move = move,
            onDismiss = { showResolveDialogFor = null },
            onConfirm = { newDest ->
                viewModel.resolveManually(move.sourcePath, newDest)
                showResolveDialogFor = null
            },
        )
    }

    val collapsingTitle = rememberCollapsingTitleState()
    AppScaffold(
        topBar = {
            OverlayHeader(
                navigation = TopBarNavigation.Detail(onBack = onNavigateBack),
                title = stringResource(CommonR.string.builder_title),
                collapsingTitle = collapsingTitle,
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        val scroll = innerPadding.forScrollingContent(topOverlay = true)
        val listState = rememberLazyListState()
        CompositionLocalProvider(LocalCollapsingTitle provides collapsingTitle) { ReportTitleScroll(listState) }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(scroll.container),
            contentPadding =
                PaddingValues(
                    start = Spacing.screenGutter,
                    end = Spacing.screenGutter,
                    top = scroll.contentTop + Spacing.sm,
                    bottom = scroll.contentBottom + Spacing.xl,
                ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item(key = "options") { OptionsGroup(forceReorganize, onForceReorganizeChange = viewModel::setForceReorganize) }
            stateItems(
                state = uiState,
                viewModel = viewModel,
                onResolveClick = { showResolveDialogFor = it },
                onDone = onNavigateBack,
            )
            item(key = "intro") { IntroGroup(modifier = Modifier.fillParentMaxHeight(0.45f)) }
        }
    }
}

/** The part of the page that follows the builder's progress: scan, review, organise, results. */
private fun LazyListScope.stateItems(
    state: LibraryBuilderState,
    viewModel: LibraryBuilderViewModel,
    onResolveClick: (ProposedMove) -> Unit,
    onDone: () -> Unit,
) {
    when (state) {
        is LibraryBuilderState.Idle ->
            item(key = "idle") { IdleActions(onScan = viewModel::startScanning) }
        is LibraryBuilderState.Scanning ->
            item(key = "scanning") { WorkingGroup(text = stringResource(CommonR.string.builder_scanning_files)) }
        is LibraryBuilderState.Review ->
            reviewItems(
                moves = state.moves,
                onSkipClick = viewModel::skipMove,
                onResolveClick = onResolveClick,
                onExecuteClick = viewModel::executeMoves,
            )
        is LibraryBuilderState.Executing ->
            item(key = "executing") {
                val locale = AppLocaleManager.activeLocale(LocalContext.current)
                WorkingGroup(
                    text =
                        stringResource(
                            CommonR.string.builder_organizing_progress,
                            LocaleFormatters.formatInteger(state.progress, locale),
                            LocaleFormatters.formatInteger(state.total, locale),
                        ),
                    progress = if (state.total > 0) state.progress.toFloat() / state.total else null,
                )
            }
        is LibraryBuilderState.Complete ->
            item(key = "complete") { CompleteGroup(result = state.result, onDone = onDone) }
    }
}

@Composable
private fun IntroGroup(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(CommonR.string.builder_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = Spacing.lg),
        )
    }
}

@Composable
private fun OptionsGroup(
    forceReorganize: Boolean,
    onForceReorganizeChange: (Boolean) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().groupSurface(CircleShape)) {
        GroupToggleRow(
            title = stringResource(CommonR.string.builder_force_reorganize),
            checked = forceReorganize,
            onCheckedChange = onForceReorganizeChange,
        )
    }
}

/** Scans the scan location, or another folder once without changing the library's scan location. */
@Composable
private fun IdleActions(onScan: (String) -> Unit) {
    val context = LocalContext.current
    val otherFolderPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            if (uri != null) {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                } catch (_: Exception) {
                }
                onScan(uri.toString())
            }
        }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        ActionButton(
            text = stringResource(CommonR.string.builder_start_scanning),
            onClick = { onScan("") },
            icon = Icons.Default.Search,
            primary = true,
        )
        ActionButton(
            text = stringResource(CommonR.string.builder_scan_another_folder),
            onClick = { otherFolderPicker.launch(null) },
            icon = Icons.Default.FolderOpen,
        )
    }
}

@Composable
private fun WorkingGroup(
    text: String,
    progress: Float? = null,
) {
    Column(
        modifier = Modifier.fillMaxWidth().groupSurface().padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
        ProgressBar(progress = progress?.let { value -> { value } })
    }
}

@Composable
private fun CompleteGroup(
    result: ExecutionResult,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val locale = AppLocaleManager.activeLocale(context)
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        SectionHeading(title = stringResource(CommonR.string.builder_complete_heading))
        Column(modifier = Modifier.fillMaxWidth().groupSurface().padding(Spacing.lg)) {
            val lines =
                listOfNotNull(
                    stringResource(
                        CommonR.string.builder_stat_moved,
                        LocaleFormatters.formatInteger(result.successCount, locale),
                    ) to colors.primary,
                    stringResource(
                        CommonR.string.builder_stat_failed,
                        LocaleFormatters.formatInteger(result.failedCount, locale),
                    ) to colors.error,
                    stringResource(
                        CommonR.string.builder_stat_skipped,
                        LocaleFormatters.formatInteger(result.skippedCount, locale),
                    ) to colors.onSurfaceVariant,
                    (
                        stringResource(
                            CommonR.string.builder_stat_already_organized,
                            LocaleFormatters.formatInteger(result.alreadyOrganizedCount, locale),
                        ) to colors.onSurfaceVariant
                    ).takeIf { result.alreadyOrganizedCount > 0 },
                )
            lines.forEach { (text, color) -> Text(text = text, style = MaterialTheme.typography.bodyLarge, color = color) }
            result.failureReasons.forEach { (reason, count) ->
                Text(
                    stringResource(
                        CommonR.string.builder_failure_reason_count,
                        LocaleFormatters.formatInteger(count, locale),
                        reason.localizedLabel(context),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        ActionButton(text = stringResource(CommonR.string.common_return_to_library), onClick = onDone, primary = true)
    }
}

private fun LazyListScope.reviewItems(
    moves: List<ProposedMove>,
    onSkipClick: (String) -> Unit,
    onResolveClick: (ProposedMove) -> Unit,
    onExecuteClick: () -> Unit,
) {
    item(key = "review-heading") {
        val locale = AppLocaleManager.activeLocale(LocalContext.current)
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SectionHeading(
                title = stringResource(CommonR.string.builder_proposed_moves),
                trailing = LocaleFormatters.formatInteger(moves.size, locale),
            )
            ActionButton(
                text = stringResource(CommonR.string.builder_execute_moves),
                onClick = onExecuteClick,
                primary = true,
                enabled = moves.isNotEmpty(),
            )
        }
    }
    if (moves.isEmpty()) {
        item(key = "review-empty") {
            Text(
                text = stringResource(CommonR.string.builder_no_files_need_organizing),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.lg),
            )
        }
    } else {
        items(moves) { move ->
            ProposedMoveItem(
                move = move,
                onSkipClick = { onSkipClick(move.sourcePath) },
                onResolveClick = { onResolveClick(move) },
            )
        }
    }
}

@Composable
private fun ProposedMoveItem(
    move: ProposedMove,
    onSkipClick: () -> Unit,
    onResolveClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().groupSurface().padding(Spacing.lg)) {
        Text(
            text = move.title.ifBlank { stringResource(CommonR.string.builder_unknown_title) },
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text =
                stringResource(
                    CommonR.string.builder_artist_album,
                    move.artist.ifBlank { stringResource(CommonR.string.builder_unknown_artist) },
                    move.album.ifBlank { stringResource(CommonR.string.fix_tags_unknown_album) },
                ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(Spacing.xs))
        Text(
            text = stringResource(CommonR.string.builder_destination_arrow, move.destinationPath),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(Spacing.sm))

        val statusColor =
            when (move.state) {
                ProposedMove.State.VALID -> MaterialTheme.colorScheme.primary
                ProposedMove.State.ALREADY_ORGANIZED -> MaterialTheme.colorScheme.secondary
                ProposedMove.State.MISSING_METADATA -> MaterialTheme.colorScheme.error
                ProposedMove.State.COLLISION -> MaterialTheme.colorScheme.error
            }
        val stateLabelRes =
            when (move.state) {
                ProposedMove.State.VALID -> CommonR.string.builder_state_valid
                ProposedMove.State.ALREADY_ORGANIZED -> CommonR.string.builder_state_already_organized
                ProposedMove.State.MISSING_METADATA -> CommonR.string.builder_state_missing_metadata
                ProposedMove.State.COLLISION -> CommonR.string.builder_state_collision
            }

        Text(
            text = stringResource(stateLabelRes),
            style = MaterialTheme.typography.labelMedium,
            color = statusColor,
        )

        if (move.state == ProposedMove.State.MISSING_METADATA || move.state == ProposedMove.State.COLLISION) {
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextButton(onClick = onSkipClick) {
                    Text(stringResource(CommonR.string.action_skip))
                }
                TextButton(onClick = onResolveClick) {
                    Text(stringResource(CommonR.string.builder_action_resolve))
                }
            }
        }
    }
}

@Composable
private fun ResolveDialog(
    move: ProposedMove,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(move.destinationPath) }

    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(CommonR.string.builder_resolve_dialog_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(CommonR.string.builder_destination_path_label)) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) {
                Text(stringResource(CommonR.string.action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(CommonR.string.action_cancel))
            }
        },
    )
}
