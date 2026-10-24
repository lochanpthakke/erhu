package com.lochanpthakke.empee3.feature.library.builder

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderZip
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
import androidx.compose.ui.text.font.FontWeight
import com.lochanpthakke.empee3.core.database.builder.ProposedMove
import com.lochanpthakke.empee3.core.designsystem.component.ActionButton
import com.lochanpthakke.empee3.core.designsystem.component.AppScaffold
import com.lochanpthakke.empee3.core.designsystem.component.GlassAlertDialog
import com.lochanpthakke.empee3.core.designsystem.component.GroupToggleRow
import com.lochanpthakke.empee3.core.designsystem.component.IconBadge
import com.lochanpthakke.empee3.core.designsystem.component.LocalCollapsingTitle
import com.lochanpthakke.empee3.core.designsystem.component.OverlayHeader
import com.lochanpthakke.empee3.core.designsystem.component.ProgressBar
import com.lochanpthakke.empee3.core.designsystem.component.ReportTitleScroll
import com.lochanpthakke.empee3.core.designsystem.component.SectionHeading
import com.lochanpthakke.empee3.core.designsystem.component.TopBarNavigation
import com.lochanpthakke.empee3.core.designsystem.component.forScrollingContent
import com.lochanpthakke.empee3.core.designsystem.component.groupSurface
import com.lochanpthakke.empee3.core.designsystem.component.rememberCollapsingTitleState
import com.lochanpthakke.empee3.core.designsystem.foundation.Spacing

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
                title = "Library Builder",
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
            item(key = "intro") { IntroGroup() }
            item(key = "options") { OptionsGroup(forceReorganize, onForceReorganizeChange = viewModel::setForceReorganize) }
            item(key = "gap") { Spacer(modifier = Modifier.height(Spacing.sm)) }
            stateItems(
                state = uiState,
                viewModel = viewModel,
                onResolveClick = { showResolveDialogFor = it },
                onDone = onNavigateBack,
            )
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
            item(key = "scanning") { WorkingGroup(text = "Scanning library files…") }
        is LibraryBuilderState.Review ->
            reviewItems(
                moves = state.moves,
                onSkipClick = viewModel::skipMove,
                onResolveClick = onResolveClick,
                onExecuteClick = viewModel::executeMoves,
            )
        is LibraryBuilderState.Executing ->
            item(key = "executing") {
                WorkingGroup(
                    text = "Organizing files (${state.progress} / ${state.total})…",
                    progress = if (state.total > 0) state.progress.toFloat() / state.total else null,
                )
            }
        is LibraryBuilderState.Complete ->
            item(key = "complete") { CompleteGroup(result = state.result, onDone = onDone) }
    }
}

@Composable
private fun IntroGroup() {
    Row(
        modifier = Modifier.fillMaxWidth().groupSurface().padding(Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon = Icons.Default.FolderZip)
        Spacer(modifier = Modifier.width(Spacing.md))
        Column {
            Text(
                text = "Library Builder",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "Organises your audio files into hierarchical folders automatically",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun OptionsGroup(
    forceReorganize: Boolean,
    onForceReorganizeChange: (Boolean) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().groupSurface()) {
        GroupToggleRow(
            title = "Force Re-organize",
            summary = "Include files that were previously organized or skipped",
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
        ActionButton(text = "Start Scanning Files", onClick = { onScan("") }, icon = Icons.Default.Search, primary = true)
        ActionButton(text = "Scan another folder…", onClick = { otherFolderPicker.launch(null) }, icon = Icons.Default.FolderOpen)
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
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        SectionHeading(title = "Organization complete")
        Column(modifier = Modifier.fillMaxWidth().groupSurface().padding(Spacing.lg)) {
            val lines =
                listOfNotNull(
                    "Successfully Moved: ${result.successCount}" to colors.primary,
                    "Failed: ${result.failedCount}" to colors.error,
                    "Skipped: ${result.skippedCount}" to colors.onSurfaceVariant,
                    ("Already in place: ${result.alreadyOrganizedCount}" to colors.onSurfaceVariant)
                        .takeIf { result.alreadyOrganizedCount > 0 },
                )
            lines.forEach { (text, color) -> Text(text = text, style = MaterialTheme.typography.bodyLarge, color = color) }
            result.failureReasons.forEach { (reason, count) ->
                Text("$count × ${reason.label}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
        }
        ActionButton(text = "Return to Library", onClick = onDone, primary = true)
    }
}

private fun LazyListScope.reviewItems(
    moves: List<ProposedMove>,
    onSkipClick: (String) -> Unit,
    onResolveClick: (ProposedMove) -> Unit,
    onExecuteClick: () -> Unit,
) {
    item(key = "review-heading") {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SectionHeading(title = "Proposed moves", trailing = moves.size.toString())
            ActionButton(text = "Execute Moves", onClick = onExecuteClick, primary = true, enabled = moves.isNotEmpty())
        }
    }
    if (moves.isEmpty()) {
        item(key = "review-empty") {
            Text(
                text = "No files need organizing. Enable 'Force Re-organize' to re-scan all files.",
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
        Text(text = move.title.ifBlank { "Unknown Title" }, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Text(
            text = "${move.artist.ifBlank { "Unknown Artist" }} - ${move.album.ifBlank { "Unknown Album" }}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(Spacing.xs))
        Text(
            text = "→ ${move.destinationPath}",
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

        Text(
            text = move.state.name,
            style = MaterialTheme.typography.labelMedium,
            color = statusColor,
        )

        if (move.state == ProposedMove.State.MISSING_METADATA || move.state == ProposedMove.State.COLLISION) {
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextButton(onClick = onSkipClick) {
                    Text("Skip")
                }
                TextButton(onClick = onResolveClick) {
                    Text("Resolve")
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
        title = { Text("Resolve Move") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Destination Path") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
