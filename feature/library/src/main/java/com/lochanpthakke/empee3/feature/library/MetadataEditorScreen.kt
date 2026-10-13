@file:Suppress("detekt:All")

package com.lochanpthakke.empee3.feature.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lochanpthakke.empee3.core.designsystem.artwork.SongArtwork
import com.lochanpthakke.empee3.core.designsystem.component.AppScaffold
import com.lochanpthakke.empee3.core.designsystem.component.AppTopBar
import com.lochanpthakke.empee3.core.designsystem.component.GlassAlertDialog
import com.lochanpthakke.empee3.core.designsystem.component.TopBarNavigation
import com.lochanpthakke.empee3.core.designsystem.foundation.ArtworkSize
import com.lochanpthakke.empee3.core.designsystem.foundation.Radius
import com.lochanpthakke.empee3.core.designsystem.foundation.Spacing
import com.lochanpthakke.empee3.core.model.Album
import com.lochanpthakke.empee3.core.model.Artist
import com.lochanpthakke.empee3.core.model.Song

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod", "MagicNumber")
fun MetadataEditorScreen(
    viewModel: LibraryViewModel,
    onOpenDrawer: () -> Unit,
    onNavigateToMetadataSettings: () -> Unit,
    onNavigateToLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var searchQuery by remember { mutableStateOf("") }
    var showCompletionDialog by remember { mutableStateOf(false) }

    val repairQueue by viewModel.repairQueue.collectAsState()
    val forceFixTags by viewModel.forceFixTags.collectAsState()
    val isScanning by viewModel.isFindingBadTracks.collectAsState()
    val isFixingQueue by viewModel.isFixingQueue.collectAsState()

    val searchResults by viewModel.searchResults.collectAsState()
    val searchAlbums by viewModel.searchAlbums.collectAsState()
    val searchArtists by viewModel.searchArtists.collectAsState()

    val doneCount = repairQueue.count { it.status == "DONE" || it.status == "SUCCESS" }
    val reviewCount = repairQueue.count { it.status == "NEEDS_REVIEW" }
    val failedCount = repairQueue.count { it.status == "FAILED" }
    val totalCount = repairQueue.size
    val isQueueComplete = totalCount > 0 && repairQueue.none { it.status == "PENDING" || it.status == "FIXING" }
    val progressFloat = if (totalCount > 0) doneCount.toFloat() / totalCount.toFloat() else 0f

    LaunchedEffect(isQueueComplete) {
        if (isQueueComplete) {
            showCompletionDialog = true
        }
    }

    AppScaffold(
        topBar = { AppTopBar(navigation = TopBarNavigation.TopLevel(onOpenDrawer), title = "Metadata Editor") },
        bottomBar = {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(Spacing.screenGutter),
                contentAlignment = Alignment.Center,
            ) {
                Button(
                    onClick = onNavigateToMetadataSettings,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                    )
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Text("Metadata & Artwork Settings")
                }
            }
        },
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = Spacing.screenGutter),
        ) {
            Spacer(modifier = Modifier.height(Spacing.sm))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    viewModel.searchLocalLibrary(it)
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search tracks, albums, or artists to fix...") },
                leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
            )

            Spacer(modifier = Modifier.height(Spacing.sm))

            if (searchQuery.isNotBlank()) {
                Text(
                    text = "SEARCH RESULTS (Tap + to add to Queue)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(Spacing.xs))

                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                ) {
                    items(searchArtists) { artist ->
                        ArtistSquareRow(artist = artist, onAdd = { viewModel.enqueueArtistForRepair(artist) })
                    }
                    items(searchAlbums) { album ->
                        AlbumSquareRow(album = album, onAdd = { viewModel.enqueueAlbumForRepair(album) })
                    }
                    items(searchResults) { song ->
                        TrackRow(song = song, onAdd = { viewModel.enqueueSongForRepair(song) })
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.md))
            }

            Button(
                onClick = { viewModel.scanForBadTracks() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isScanning && !isFixingQueue,
            ) {
                Icon(imageVector = Icons.Default.AutoFixHigh, contentDescription = null)
                Spacer(modifier = Modifier.width(Spacing.sm))
                Text(if (isScanning) "Finding tracks..." else "Scan for Bad / Incomplete Tracks")
            }

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = forceFixTags,
                    onCheckedChange = { viewModel.setForceFixTags(it) },
                )
                Spacer(modifier = Modifier.width(Spacing.xs))
                Text(
                    text = "Force Fix Tags (Re-evaluate already fixed files)",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(modifier = Modifier.height(Spacing.xs))

            var providerExpanded by remember { mutableStateOf(false) }
            var actionExpanded by remember { mutableStateOf(false) }

            val selectedProvider by viewModel.selectedProvider.collectAsState()
            val selectedFields by viewModel.selectedFields.collectAsState()

            val providerOptions = listOf("ALL" to "All Sources", "Deezer" to "Deezer", "MusicBrainz" to "MusicBrainz", "LastFm" to "Last.fm")
            val allFields = com.lochanpthakke.empee3.core.model.MetadataField.entries
            val isAllSelected = selectedFields.size == allFields.size
            val fixButtonLabel = when {
                isAllSelected -> "Fix: All Fields ▾"
                selectedFields.size == 1 -> "Fix: ${selectedFields.first().displayName} ▾"
                else -> "Fix: ${selectedFields.size} Fields ▾"
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { providerExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "Source: ${providerOptions.find { it.first == selectedProvider }?.second ?: selectedProvider} ▾",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    DropdownMenu(
                        expanded = providerExpanded,
                        onDismissRequest = { providerExpanded = false },
                    ) {
                        providerOptions.forEach { (key, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    viewModel.setSelectedProvider(key)
                                    providerExpanded = false
                                },
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { actionExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = fixButtonLabel,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    DropdownMenu(
                        expanded = actionExpanded,
                        onDismissRequest = { actionExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Select All Fields", fontWeight = FontWeight.Bold) },
                            onClick = {
                                viewModel.selectAllFields()
                            },
                        )
                        allFields.forEach { field ->
                            val isChecked = selectedFields.contains(field)
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(checked = isChecked, onCheckedChange = null)
                                        Spacer(modifier = Modifier.width(Spacing.xs))
                                        Text(field.displayName)
                                    }
                                },
                                onClick = {
                                    viewModel.toggleField(field)
                                },
                            )
                        }
                    }
                }
            }

            var modeExpanded by remember { mutableStateOf(false) }
            val repairMode by viewModel.repairMode.collectAsState()

            Box(modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
                OutlinedButton(
                    onClick = { modeExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Mode: ${if (repairMode == LibraryViewModel.RepairMode.AUTOMATIC) "Automatic Tagging" else "Manual Selection"} ▾",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                DropdownMenu(
                    expanded = modeExpanded,
                    onDismissRequest = { modeExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Automatic Tagging") },
                        onClick = {
                            viewModel.setRepairMode(LibraryViewModel.RepairMode.AUTOMATIC)
                            modeExpanded = false
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Manual Selection") },
                        onClick = {
                            viewModel.setRepairMode(LibraryViewModel.RepairMode.MANUAL)
                            modeExpanded = false
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.md))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "IN QUEUE FOR FIXING ($doneCount / $totalCount Tracks)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(modifier = Modifier.height(Spacing.xs))

            LinearProgressIndicator(
                progress = { progressFloat },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp),
            )

            Spacer(modifier = Modifier.height(Spacing.md))

            if (isQueueComplete) {
                Button(
                    onClick = {
                        viewModel.clearRepairQueue()
                        onNavigateToLibrary()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Text("Finish Repair & Return to Library")
                }
            } else {
                Button(
                    onClick = { viewModel.startFixingQueue() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isFixingQueue && repairQueue.any { it.status == "PENDING" },
                ) {
                    Icon(imageVector = Icons.Default.AutoFixHigh, contentDescription = null)
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Text(if (isFixingQueue) "Fixing Tags in Progress..." else "Start Fixing Queue / Fix Tags")
                }
            }

            Spacer(modifier = Modifier.height(Spacing.md))

            if (repairQueue.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Queue is empty. Tap scan or search for tracks to populate the repair queue.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(repairQueue, key = { it.song.id }) { queueItem ->
                        Card(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = Spacing.xs),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                if (queueItem.status == "FIXING") {
                                    LinearProgressIndicator(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .height(3.dp),
                                    )
                                }

                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(Spacing.md),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        SongArtwork(
                                            song = queueItem.song,
                                            modifier =
                                                Modifier
                                                    .size(ArtworkSize.compact)
                                                    .clip(Radius.smallShape),
                                        )

                                        Spacer(modifier = Modifier.width(10.dp))

                                        Column {
                                            Text(
                                                text = queueItem.song.title,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "${queueItem.song.artist} • ${queueItem.song.album}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            if (queueItem.status == "FAILED") {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = "⚠️ ${formatFailureReason(queueItem.failureReason)}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.error,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        when (queueItem.status) {
                                            "FIXING" ->
                                                Icon(
                                                    imageVector = Icons.Default.HourglassTop,
                                                    contentDescription = "Fixing",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                )
                                            "DONE", "SUCCESS" ->
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = "Done",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                )
                                            "NEEDS_REVIEW" ->
                                                Icon(
                                                    imageVector = Icons.Default.Search,
                                                    contentDescription = "Needs Review",
                                                    tint = MaterialTheme.colorScheme.secondary,
                                                )
                                            "FAILED" ->
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = Icons.Default.Error,
                                                        contentDescription = "Failed",
                                                        tint = MaterialTheme.colorScheme.error,
                                                    )
                                                    IconButton(
                                                        onClick = {
                                                            viewModel.retryRepairQueueItem(queueItem.song)
                                                        },
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Refresh,
                                                            contentDescription = "Retry track repair",
                                                            tint = MaterialTheme.colorScheme.primary,
                                                        )
                                                    }
                                                }
                                            else ->
                                                Text(
                                                    text = "Pending",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                        }

                                        Spacer(modifier = Modifier.width(Spacing.sm))

                                        IconButton(
                                            onClick = {
                                                viewModel.removeFromRepairQueue(queueItem.song)
                                            },
                                        ) {
                                            Icon(imageVector = Icons.Default.Close, contentDescription = "Remove from Queue")
                                        }
                                    }
                                }

                                var expanded by remember { mutableStateOf(false) }
                                val diag = queueItem.diagnostics
                                if (diag != null) {
                                    TextButton(
                                        onClick = { expanded = !expanded },
                                        modifier = Modifier.padding(horizontal = Spacing.md),
                                    ) {
                                        Icon(
                                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Spacer(modifier = Modifier.width(Spacing.xs))
                                        Text(if (expanded) "Show less" else "Show more", style = MaterialTheme.typography.labelSmall)
                                    }

                                    AnimatedVisibility(
                                        visible = expanded,
                                        enter = fadeIn() + expandVertically(),
                                        exit = fadeOut() + shrinkVertically(),
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = Spacing.md)
                                                .padding(bottom = Spacing.md),
                                        ) {
                                            Text("Search Details", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                            Text("Original file: ${diag.originalFilename}", style = MaterialTheme.typography.bodySmall)
                                            Text("Searched title: \"${diag.searchTitle}\"", style = MaterialTheme.typography.bodySmall)
                                            Text("Searched artist: ${diag.searchArtist ?: "Unknown / not provided"}", style = MaterialTheme.typography.bodySmall)
                                            diag.targetDurationMs?.let { dur ->
                                                Text("Target duration: ${dur / 1000}s", style = MaterialTheme.typography.bodySmall)
                                            }

                                            Spacer(modifier = Modifier.height(Spacing.xs))

                                            Text("Provider Results", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                            diag.providerOutcomes.forEach { outcome ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                ) {
                                                    Text("• ${outcome.providerName}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                                    val statusText = when (outcome.status) {
                                                        com.lochanpthakke.empee3.core.model.ProviderStatus.SUCCESS -> "✓ Selected candidate"
                                                        com.lochanpthakke.empee3.core.model.ProviderStatus.CANDIDATES_FOUND -> "${outcome.candidateCount} candidate(s)"
                                                        com.lochanpthakke.empee3.core.model.ProviderStatus.LOW_CONFIDENCE -> "${outcome.candidateCount} low confidence"
                                                        com.lochanpthakke.empee3.core.model.ProviderStatus.NO_RESULTS -> "No results found"
                                                        com.lochanpthakke.empee3.core.model.ProviderStatus.TIMED_OUT -> "Request timed out"
                                                        com.lochanpthakke.empee3.core.model.ProviderStatus.FAILED -> "Failed: ${outcome.errorMessage ?: "Request failed"}"
                                                        com.lochanpthakke.empee3.core.model.ProviderStatus.SKIPPED -> "Provider not queried"
                                                    }
                                                    Text(statusText, style = MaterialTheme.typography.bodySmall, color = if (outcome.status == com.lochanpthakke.empee3.core.model.ProviderStatus.SUCCESS) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }

                                            if (diag.topCandidates.isNotEmpty()) {
                                                Spacer(modifier = Modifier.height(Spacing.xs))
                                                Text("Candidates Returned", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                                diag.topCandidates.forEach { candidate ->
                                                    val scorePct = (candidate.confidenceScore * 100).toInt()
                                                    val isAccepted = scorePct >= 85
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                    ) {
                                                        Text("${candidate.title} — ${candidate.artist}", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                                        Text("$scorePct% (${candidate.sourceName}) ${if (isAccepted) "✓" else "✗"}", style = MaterialTheme.typography.labelSmall, color = if (isAccepted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(Spacing.xs))
                                            Text("Final Outcome", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                            Text(formatFailureReason(diag.finalReason ?: queueItem.failureReason), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }

                                val succ = queueItem.successDetails
                                if (succ != null) {
                                    var showChangesExpanded by remember { mutableStateOf(false) }
                                    TextButton(
                                        onClick = { showChangesExpanded = !showChangesExpanded },
                                        modifier = Modifier.padding(horizontal = Spacing.md),
                                    ) {
                                        Icon(
                                            imageVector = if (showChangesExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Spacer(modifier = Modifier.width(Spacing.xs))
                                        Text(if (showChangesExpanded) "Show less" else "Show changes", style = MaterialTheme.typography.labelSmall)
                                    }

                                    AnimatedVisibility(
                                        visible = showChangesExpanded,
                                        enter = fadeIn() + expandVertically(),
                                        exit = fadeOut() + shrinkVertically(),
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = Spacing.md)
                                                .padding(bottom = Spacing.md),
                                        ) {
                                            Text("Metadata Changes", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                            if (succ.changes.isEmpty()) {
                                                Text("• Tags already up to date (no tag changes needed)", style = MaterialTheme.typography.bodySmall)
                                            } else {
                                                succ.changes.forEach { change ->
                                                    Text("• ${change.fieldName}: ${change.oldValue?.takeIf { it.isNotBlank() } ?: "Blank"} → ${change.newValue}", style = MaterialTheme.typography.bodySmall)
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(Spacing.xs))
                                            Text("File Renaming Result", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                            if (succ.isRenamed && succ.newFilename != null) {
                                                Text("✓ Renamed: ${succ.oldFilename} → ${succ.newFilename}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                            } else if (succ.renameError != null) {
                                                Text("⚠️ Rename skipped: ${succ.renameError}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                            } else {
                                                Text("• Filename unchanged: ${succ.oldFilename}", style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCompletionDialog) {
        GlassAlertDialog(
            onDismissRequest = { showCompletionDialog = false },
            title = { Text("Metadata Repair Complete") },
            text = {
                Column {
                    Text("✓ $doneCount tracks fixed successfully", fontWeight = FontWeight.Bold)
                    if (reviewCount > 0) {
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        Text("🔍 $reviewCount tracks need manual review")
                    }
                    if (failedCount > 0) {
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        Text("❌ $failedCount tracks failed:", fontWeight = FontWeight.Bold)
                        val failedReasons = repairQueue.filter { it.status == "FAILED" }
                            .groupBy { formatFailureReason(it.failureReason) }
                        failedReasons.forEach { (reason, items) ->
                            Text("  • ${items.size} × $reason", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCompletionDialog = false
                        viewModel.clearRepairQueue()
                        onNavigateToLibrary()
                    },
                ) {
                    Text("Finish & Return to Library")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCompletionDialog = false }) {
                    Text("Keep Inspecting Queue")
                }
            },
        )
    }
}

private fun formatFailureReason(reason: String?): String =
    when {
        reason.isNullOrBlank() -> "No online match found"
        reason == "NO_MATCH" -> "No online match found"
        reason == "FILE_NOT_FOUND" -> "File not found on storage"
        reason == "NETWORK_ERROR" -> "Network error / Offline"
        reason.contains("permission", ignoreCase = true) || reason.contains("writeAccess", ignoreCase = true) -> "Storage write permission denied"
        reason.contains("read-only", ignoreCase = true) -> "File is read-only"
        else -> reason
    }

@Composable
private fun ArtistSquareRow(
    artist: Artist,
    onAdd: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(Spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier =
                        Modifier
                            .size(ArtworkSize.compact)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(text = artist.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(text = "Artist • ${artist.songCount} tracks", style = MaterialTheme.typography.bodySmall)
                }
            }
            OutlinedButton(onClick = onAdd) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Artist")
                Spacer(modifier = Modifier.width(Spacing.xs))
                Text("Add")
            }
        }
    }
}

@Composable
private fun AlbumSquareRow(
    album: Album,
    onAdd: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(Spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier =
                        Modifier
                            .size(ArtworkSize.compact)
                            .clip(Radius.smallShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(text = album.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(text = "Album • ${album.artist}", style = MaterialTheme.typography.bodySmall)
                }
            }
            OutlinedButton(onClick = onAdd) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Album")
                Spacer(modifier = Modifier.width(Spacing.xs))
                Text("Add")
            }
        }
    }
}

@Composable
private fun TrackRow(
    song: Song,
    onAdd: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(Spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = song.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(text = "${song.artist} • ${song.album}", style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
            OutlinedButton(onClick = onAdd) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Track")
                Spacer(modifier = Modifier.width(Spacing.xs))
                Text("Add")
            }
        }
    }
}
