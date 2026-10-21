package com.lochanpthakke.empee3.feature.library.builder

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lochanpthakke.empee3.core.database.builder.ProposedMove
import com.lochanpthakke.empee3.core.designsystem.component.AppScaffold
import com.lochanpthakke.empee3.core.designsystem.component.AppTopBar
import com.lochanpthakke.empee3.core.designsystem.component.GlassAlertDialog
import com.lochanpthakke.empee3.core.designsystem.component.TopBarNavigation

@Composable
fun LibraryBuilderScreen(
    viewModel: LibraryBuilderViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val forceReorganize by viewModel.forceReorganize.collectAsState()

    AppScaffold(
        topBar = {
            AppTopBar(
                navigation = TopBarNavigation.Detail(onBack = onNavigateBack),
                title = "Library Builder",
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Tool Description Banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderZip,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Library Builder",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Organises your audio files into hierarchical folders automatically",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Force Re-organize Switch
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Force Re-organize", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Text("Include files that were previously organized or skipped", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = forceReorganize,
                        onCheckedChange = viewModel::setForceReorganize,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (val state = uiState) {
                is LibraryBuilderState.Idle -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Button(onClick = { viewModel.startScanning() }) {
                            Text("Start Scanning Files")
                        }
                    }
                }
                is LibraryBuilderState.Scanning -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Scanning library files...")
                        }
                    }
                }
                is LibraryBuilderState.Review -> {
                    ReviewView(
                        moves = state.moves,
                        onSkipClick = viewModel::skipMove,
                        onResolveClick = viewModel::resolveManually,
                        onExecuteClick = viewModel::executeMoves,
                    )
                }
                is LibraryBuilderState.Executing -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Organizing files (${state.progress} / ${state.total})...")
                        }
                    }
                }
                is LibraryBuilderState.Complete -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Organization Complete!", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Successfully Moved: ${state.result.successCount}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                            Text("Failed: ${state.result.failedCount}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                            Text("Skipped: ${state.result.skippedCount}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(onClick = onNavigateBack) {
                                Text("Return to Library")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewView(
    moves: List<ProposedMove>,
    onSkipClick: (String) -> Unit,
    onResolveClick: (String, String) -> Unit,
    onExecuteClick: () -> Unit,
) {
    var showResolveDialogFor by remember { mutableStateOf<ProposedMove?>(null) }

    showResolveDialogFor?.let { move ->
        ResolveDialog(
            move = move,
            onDismiss = { showResolveDialogFor = null },
            onConfirm = { newDest ->
                onResolveClick(move.sourcePath, newDest)
                showResolveDialogFor = null
            },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "PROPOSED MOVES (${moves.size})",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (moves.isEmpty()) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    text = "No files need organizing. Enable 'Force Re-organize' to re-scan all files.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(moves) { move ->
                    ProposedMoveItem(
                        move = move,
                        onSkipClick = { onSkipClick(move.sourcePath) },
                        onResolveClick = { showResolveDialogFor = move },
                    )
                }
            }
        }

        Button(
            onClick = onExecuteClick,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
            enabled = moves.isNotEmpty(),
        ) {
            Text("Execute Moves")
        }
    }
}

@Composable
private fun ProposedMoveItem(
    move: ProposedMove,
    onSkipClick: () -> Unit,
    onResolveClick: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = move.title.ifBlank { "Unknown Title" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(text = "${move.artist.ifBlank { "Unknown Artist" }} - ${move.album.ifBlank { "Unknown Album" }}", style = MaterialTheme.typography.bodyMedium)

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "→ ${move.destinationPath}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(8.dp))

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
