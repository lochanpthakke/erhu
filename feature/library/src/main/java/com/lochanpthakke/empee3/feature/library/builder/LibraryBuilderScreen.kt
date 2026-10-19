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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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

    AppScaffold(
        topBar = {
            AppTopBar(
                navigation = TopBarNavigation.Detail(onBack = onNavigateBack),
                title = "Library Builder"
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = uiState) {
                is LibraryBuilderState.Idle -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Button(onClick = { viewModel.startScanning() }) {
                            Text("Start Scanning")
                        }
                    }
                }
                is LibraryBuilderState.Scanning -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Scanning files...")
                        }
                    }
                }
                is LibraryBuilderState.Review -> {
                    ReviewView(
                        moves = state.moves,
                        onSkipClick = viewModel::skipMove,
                        onResolveClick = viewModel::resolveManually,
                        onExecuteClick = viewModel::executeMoves
                    )
                }
                is LibraryBuilderState.Executing -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Executing moves (${state.movesToExecute.size} files)...")
                        }
                    }
                }
                is LibraryBuilderState.Complete -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Execution Complete!", style = MaterialTheme.typography.titleLarge)
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = onNavigateBack) {
                                Text("Go Back")
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
    onExecuteClick: () -> Unit
) {
    var showResolveDialogFor by remember { mutableStateOf<ProposedMove?>(null) }

    showResolveDialogFor?.let { move ->
        ResolveDialog(
            move = move,
            onDismiss = { showResolveDialogFor = null },
            onConfirm = { newDest -> 
                onResolveClick(move.sourcePath, newDest)
                showResolveDialogFor = null
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(moves) { move ->
                ProposedMoveItem(
                    move = move,
                    onSkipClick = { onSkipClick(move.sourcePath) },
                    onResolveClick = { showResolveDialogFor = move }
                )
            }
        }
        
        Button(
            onClick = onExecuteClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            enabled = moves.isNotEmpty()
        ) {
            Text("Execute Moves")
        }
    }
}

@Composable
private fun ProposedMoveItem(
    move: ProposedMove,
    onSkipClick: () -> Unit,
    onResolveClick: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = move.title.ifBlank { "Unknown Title" }, style = MaterialTheme.typography.titleMedium)
            Text(text = "${move.artist.ifBlank { "Unknown Artist" }} - ${move.album.ifBlank { "Unknown Album" }}", style = MaterialTheme.typography.bodyMedium)
            
            Spacer(modifier = Modifier.height(8.dp))
            
            val statusColor = when (move.state) {
                ProposedMove.State.VALID -> MaterialTheme.colorScheme.primary
                ProposedMove.State.ALREADY_ORGANIZED -> MaterialTheme.colorScheme.secondary
                ProposedMove.State.MISSING_METADATA -> MaterialTheme.colorScheme.error
                ProposedMove.State.COLLISION -> MaterialTheme.colorScheme.error
            }
            
            Text(
                text = move.state.name,
                style = MaterialTheme.typography.labelMedium,
                color = statusColor
            )

            if (move.state == ProposedMove.State.MISSING_METADATA || move.state == ProposedMove.State.COLLISION) {
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
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
    onConfirm: (String) -> Unit
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
                modifier = Modifier.fillMaxWidth()
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
        }
    )
}
