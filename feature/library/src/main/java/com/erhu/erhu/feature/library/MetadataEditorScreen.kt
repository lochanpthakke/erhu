@file:Suppress("detekt:All")

package com.erhu.erhu.feature.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import com.erhu.erhu.core.designsystem.artwork.SongArtwork
import com.erhu.erhu.core.designsystem.component.ActionButton
import com.erhu.erhu.core.designsystem.component.AppScaffold
import com.erhu.erhu.core.designsystem.component.GlassAlertDialog
import com.erhu.erhu.core.designsystem.component.GlassMenu
import com.erhu.erhu.core.designsystem.component.GlassMenuItem
import com.erhu.erhu.core.designsystem.component.GroupDivider
import com.erhu.erhu.core.designsystem.component.GroupRow
import com.erhu.erhu.core.designsystem.component.GroupToggleRow
import com.erhu.erhu.core.designsystem.component.LocalCollapsingTitle
import com.erhu.erhu.core.designsystem.component.OverlayHeader
import com.erhu.erhu.core.designsystem.component.ProgressBar
import com.erhu.erhu.core.designsystem.component.ReportTitleScroll
import com.erhu.erhu.core.designsystem.component.SearchField
import com.erhu.erhu.core.designsystem.component.SectionHeading
import com.erhu.erhu.core.designsystem.component.TopBarNavigation
import com.erhu.erhu.core.designsystem.component.forScrollingContent
import com.erhu.erhu.core.designsystem.component.groupSurface
import com.erhu.erhu.core.designsystem.component.rememberCollapsingTitleState
import com.erhu.erhu.core.designsystem.foundation.ArtworkSize
import com.erhu.erhu.core.designsystem.foundation.Radius
import com.erhu.erhu.core.designsystem.foundation.Spacing
import com.erhu.erhu.core.model.Album
import com.erhu.erhu.core.model.Artist
import com.erhu.erhu.core.model.Song

/** The search results' own scroll area inside the page. */
private val SEARCH_RESULTS_MAX_HEIGHT = 280.dp

/** Tint behind a search result's placeholder icon, in the theme accent. */
private const val THUMB_TINT_ALPHA = 0.14f

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

    val pendingApproval by viewModel.metadataRepair.awaitingApproval.collectAsState()
    val notificationContext = androidx.compose.ui.platform.LocalContext.current
    val notificationPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { viewModel.startFixingQueue() }
    val startFetching = {
        if (android.os.Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(
                notificationContext, android.Manifest.permission.POST_NOTIFICATIONS,
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        else viewModel.startFixingQueue()
    }
    val repairQueue by viewModel.repairQueue.collectAsState()
    val forceFixTags by viewModel.forceFixTags.collectAsState()
    val isScanning by viewModel.isFindingBadTracks.collectAsState()
    val isFixingQueue by viewModel.isFixingQueue.collectAsState()

    val searchResults by viewModel.searchResults.collectAsState()
    val searchAlbums by viewModel.searchAlbums.collectAsState()
    val searchArtists by viewModel.searchArtists.collectAsState()
    val selectAll by viewModel.selectAllForRepair.collectAsState()

    val summary = RepairSummary.of(repairQueue)
    val batchPause by viewModel.batchPause.collectAsState()
    val sourceOptions by viewModel.metadataSourceOptions.collectAsState()
    val toProcessCount = repairQueue.count { it.status in RepairStatus.TO_PROCESS }
    val totalCount = repairQueue.size
    val fetchProgress by viewModel.metadataRepair.progress.collectAsState()
    val processedCount = if (isFixingQueue && fetchProgress.phase in setOf(RepairPhase.FETCHING, RepairPhase.PAUSED)) {
        fetchProgress.processed
    } else repairQueue.count { it.status != RepairStatus.PENDING && it.status != RepairStatus.FIXING }
    val isQueueComplete = totalCount > 0 && processedCount == totalCount
    val progressFloat = if (totalCount > 0) processedCount.toFloat() / totalCount.toFloat() else 0f
    var selectedRevertIds by remember { mutableStateOf(setOf<Long>()) }
    val revertCompletionMessage by viewModel.revertCompletionMessage.collectAsState()
    val isReverting by viewModel.isReverting.collectAsState()

    var songForFixTags by remember { mutableStateOf<Song?>(null) }
    val metadataCandidates by viewModel.metadataCandidates.collectAsState()
    var showAmbiguousFixedDialog by remember { mutableStateOf(false) }

    // The summary appears whenever a run ends: finished, paused by the connection or by the user.
    var wasFixing by remember { mutableStateOf(false) }
    LaunchedEffect(isFixingQueue) {
        if (wasFixing && !isFixingQueue && !pendingApproval && !isReverting && revertCompletionMessage == null && totalCount > 0) {
            showCompletionDialog = true
        }
        wasFixing = isFixingQueue
    }

    var providerExpanded by remember { mutableStateOf(false) }
    var actionExpanded by remember { mutableStateOf(false) }
    var modeExpanded by remember { mutableStateOf(false) }
    val selectedProvider by viewModel.selectedProvider.collectAsState()
    val selectedFields by viewModel.selectedFields.collectAsState()
    val repairMode by viewModel.repairMode.collectAsState()
    val providerOptions = sourceOptions.map { it.key to it.label }
    // Lochan's multi-select: every field can be switched on or off; all tag fields = "All Fields".
    val allFields = com.erhu.erhu.core.model.MetadataField.supported
    val fieldsLabel =
        when {
            selectedFields.containsAll(allFields) -> "All Fields"
            selectedFields.size == 1 -> selectedFields.first().displayName
            else -> "${selectedFields.size} Fields"
        }

    // The app's floating header (round glass drawer button); the title merges into it on scroll.
    val collapsingTitle = rememberCollapsingTitleState()
    AppScaffold(
        topBar = {
            OverlayHeader(
                navigation = TopBarNavigation.TopLevel(onOpenDrawer),
                title = "Auto Fix Tags",
                collapsingTitle = collapsingTitle,
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        val scroll = innerPadding.forScrollingContent(topOverlay = true)
        val listState = rememberLazyListState()
        CompositionLocalProvider(LocalCollapsingTitle provides collapsingTitle) { ReportTitleScroll(listState) }
        // One list for the whole page: the controls scroll away and the queue gets the full height.
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
        ) {
            // ---- Find: search the library or scan it for tracks with bad tags.
            item(key = "find") {
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.xl)) {
                    SectionHeading(title = "Find tracks")
                    Column(
                        modifier = Modifier.fillMaxWidth().groupSurface().padding(Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        SearchField(
                            value = searchQuery,
                            onValueChange = {
                                searchQuery = it
                                viewModel.searchLocalLibrary(it)
                            },
                            placeholder = "Search tracks, albums, or artists",
                            actions = {
                                // Selects everything available: the search's songs, albums and artists, or the whole library.
                                IconToggleButton(
                                    checked = selectAll != null,
                                    onCheckedChange = { viewModel.setSelectAllForRepair(it, searchQuery) },
                                    enabled = !isFixingQueue,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DoneAll,
                                        contentDescription =
                                            if (searchQuery.isBlank()) "Select all songs to fix" else "Select all results to fix",
                                        tint =
                                            if (selectAll != null) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                    )
                                }
                            },
                        )

                        selectAll?.let { selection ->
                            Text(
                                text = selectAllSummary(selection),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }

                        if (searchQuery.isNotBlank()) {
                            Text(
                                text = "Search results · tap + to add to the queue",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = SEARCH_RESULTS_MAX_HEIGHT)) {
                                items(searchArtists) { artist ->
                                    ArtistSquareRow(
                                        artist = artist,
                                        onAdd = {
                                            viewModel.enqueueArtistForRepair(artist)
                                            searchQuery = ""
                                            viewModel.searchLocalLibrary("")
                                        },
                                    )
                                }
                                items(searchAlbums) { album ->
                                    AlbumSquareRow(
                                        album = album,
                                        onAdd = {
                                            viewModel.enqueueAlbumForRepair(album)
                                            searchQuery = ""
                                            viewModel.searchLocalLibrary("")
                                        },
                                    )
                                }
                                items(searchResults) { song ->
                                    TrackRow(
                                        song = song,
                                        onAdd = {
                                            viewModel.enqueueSongForRepair(song)
                                            searchQuery = ""
                                            viewModel.searchLocalLibrary("")
                                        },
                                    )
                                }
                            }
                        }

                        ActionButton(
                            text = if (isScanning) "Finding tracks..." else "Scan for Bad / Incomplete Tracks",
                            onClick = { viewModel.scanForBadTracks() },
                            icon = Icons.Default.AutoFixHigh,
                            enabled = !isScanning && !isFixingQueue,
                        )
                    }
                }
            }

            // ---- Options: how the fix runs.
            item(key = "options") {
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.xl)) {
                    SectionHeading(title = "Fix options")
                    Column(modifier = Modifier.fillMaxWidth().groupSurface()) {
                        Box {
                            GroupRow(
                                title = "Source",
                                summary = providerOptions.find { it.first == selectedProvider }?.second ?: selectedProvider,
                                icon = Icons.Default.Cloud,
                                trailing = Icons.Default.ArrowDropDown,
                                onClick = { providerExpanded = true },
                            )
                            GlassMenu(
                                expanded = providerExpanded,
                                onDismissRequest = { providerExpanded = false },
                            ) {
                                providerOptions.forEach { (key, label) ->
                                    GlassMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            viewModel.setSelectedProvider(key)
                                            providerExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                        GroupDivider()
                        Box {
                            GroupRow(
                                title = "Fields to fix",
                                summary = fieldsLabel,
                                icon = Icons.Default.Tag,
                                trailing = Icons.Default.ArrowDropDown,
                                onClick = { actionExpanded = true },
                            )
                            GlassMenu(
                                expanded = actionExpanded,
                                dismissOnSelection = false,
                                onDismissRequest = { actionExpanded = false },
                            ) {
                                GlassMenuItem(
                                    text = { Text("Select All Fields", fontWeight = FontWeight.Bold) },
                                    onClick = { viewModel.selectAllFields() },
                                )
                                allFields.forEach { field ->
                                    GlassMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Checkbox(checked = field in selectedFields, onCheckedChange = null)
                                                Spacer(modifier = Modifier.width(Spacing.xs))
                                                Text(field.displayName)
                                            }
                                        },
                                        onClick = { viewModel.toggleField(field) },
                                    )
                                }
                            }
                        }
                        GroupDivider()
                        Box {
                            GroupRow(
                                title = "Mode",
                                summary =
                                    if (repairMode == LibraryViewModel.RepairMode.AUTOMATIC) {
                                        "Automatic Tagging"
                                    } else {
                                        "Manual Selection"
                                    },
                                icon = Icons.Default.Tune,
                                trailing = Icons.Default.ArrowDropDown,
                                onClick = { modeExpanded = true },
                            )
                            GlassMenu(
                                expanded = modeExpanded,
                                onDismissRequest = { modeExpanded = false },
                            ) {
                                GlassMenuItem(
                                    text = { Text("Automatic Tagging") },
                                    onClick = {
                                        viewModel.setRepairMode(LibraryViewModel.RepairMode.AUTOMATIC)
                                        modeExpanded = false
                                    },
                                )
                                GlassMenuItem(
                                    text = { Text("Manual Selection") },
                                    onClick = {
                                        viewModel.setRepairMode(LibraryViewModel.RepairMode.MANUAL)
                                        modeExpanded = false
                                    },
                                )
                            }
                        }
                        GroupDivider()
                        GroupToggleRow(
                            title = "Force Fix Tags",
                            summary = "Re-evaluate already fixed files",
                            checked = forceFixTags,
                            onCheckedChange = { viewModel.setForceFixTags(it) },
                        )
                    }
                }
            }

            // ---- Queue: progress and the page's primary action, then every queued track.
            item(key = "queue-header") {
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.sm)) {
                    SectionHeading(title = "Queue for fixing", trailing = "$processedCount / $totalCount tracks")
                    Column(
                        modifier = Modifier.fillMaxWidth().groupSurface().padding(Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        ProgressBar(progress = { progressFloat })

                        batchPause?.let { pause ->
                            if (!isFixingQueue && toProcessCount > 0) {
                                Text(
                                    text =
                                        "Paused: ${pause.reason}. ${summary.fixed} fixed so far, $toProcessCount still to check" +
                                            if (pause.kind.resumesWithNetwork) " (resumes when the connection is back)." else ".",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }

                        if (isQueueComplete) {
                            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                                ) {
                                    ActionButton(
                                        text =
                                            if (isReverting) {
                                                "Reverting..."
                                            } else if (selectedRevertIds.isEmpty()) {
                                                "Revert Changes"
                                            } else {
                                                "Revert (${selectedRevertIds.size})"
                                            },
                                        onClick = {
                                            viewModel.revertSelectedChanges(selectedRevertIds)
                                        },
                                        icon = Icons.Default.Refresh,
                                        enabled = selectedRevertIds.isNotEmpty() && !isReverting && !isFixingQueue,
                                        modifier = Modifier.weight(1f),
                                    )
                                    ActionButton(
                                        text = "Return to Library",
                                        onClick = {
                                            viewModel.clearRepairQueue()
                                            onNavigateToLibrary()
                                        },
                                        primary = true,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                val revertableQueue = repairQueue.filter { it.successDetails != null }
                                val isAllSelected = revertableQueue.isNotEmpty() && selectedRevertIds.size == revertableQueue.size
                                ActionButton(
                                    text = if (isAllSelected) "Deselect All Tracks" else "Select All Tracks",
                                    onClick = {
                                        selectedRevertIds =
                                            if (isAllSelected) {
                                                emptySet()
                                            } else {
                                                revertableQueue.map { it.song.id }.toSet()
                                            }
                                    },
                                    icon = Icons.Default.DoneAll,
                                    enabled = !isReverting && !isFixingQueue && revertableQueue.isNotEmpty(),
                                )
                            }
                        } else if (pendingApproval && !isFixingQueue) {
                            ActionButton(
                                text = "Allow changes",
                                onClick = { viewModel.metadataRepair.approvePending() },
                                icon = Icons.Default.CheckCircle,
                                primary = true,
                            )
                        } else if (isFixingQueue) {
                            ActionButton(
                                text = "Pause after this track",
                                onClick = { viewModel.pauseFixingQueue() },
                                icon = Icons.Default.Pause,
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                ActionButton(
                                    text =
                                        if (processedCount > 0 && toProcessCount > 0) {
                                            "Resume ($toProcessCount left)"
                                        } else {
                                            "Start Fixing Queue / Fix Tags"
                                        },
                                    onClick = startFetching,
                                    icon = Icons.Default.AutoFixHigh,
                                    primary = true,
                                    enabled = toProcessCount > 0,
                                )
                                if (repairQueue.isNotEmpty()) {
                                    ActionButton(
                                        text = "Clear Queue",
                                        onClick = { viewModel.clearRepairQueue() },
                                        icon = Icons.Default.DeleteOutline,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (repairQueue.isEmpty()) {
                item(key = "queue-empty") {
                    Text(
                        text = "Queue is empty. Tap scan or search for tracks to populate the repair queue.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().groupSurface().padding(Spacing.lg),
                    )
                }
            } else {
                val (needsReview, others) = repairQueue.partition { it.status == RepairStatus.NEEDS_REVIEW }

                if (needsReview.isNotEmpty()) {
                    item(key = "needs-review-header") {
                        Column(modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.sm, top = Spacing.md)) {
                            SectionHeading(title = "Requires Manual Selection")
                        }
                    }
                    items(needsReview, key = { it.song.id }) { queueItem ->
                        RepairQueueCard(
                            queueItem = queueItem,
                            selectedForRevert = queueItem.song.id in selectedRevertIds,
                            onSelectForRevert = { checked ->
                                selectedRevertIds =
                                    if (checked) {
                                        selectedRevertIds + queueItem.song.id
                                    } else {
                                        selectedRevertIds - queueItem.song.id
                                    }
                            },
                            onReviewMatches = {
                                songForFixTags = queueItem.song
                                viewModel.searchMetadataForSong(queueItem.song)
                            },
                            onRetry = { viewModel.retryRepairQueueItem(queueItem.song) },
                            onRemove = { viewModel.removeFromRepairQueue(queueItem.song) },
                            modifier = Modifier.padding(vertical = Spacing.xs),
                        )
                    }
                }

                if (others.isNotEmpty()) {
                    item(key = "others-header") {
                        Column(modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.sm, top = Spacing.md)) {
                            SectionHeading(title = "Processed / Pending")
                        }
                    }
                    items(others, key = { it.song.id }) { queueItem ->
                        RepairQueueCard(
                            queueItem = queueItem,
                            selectedForRevert = queueItem.song.id in selectedRevertIds,
                            onSelectForRevert = { checked ->
                                selectedRevertIds =
                                    if (checked) {
                                        selectedRevertIds + queueItem.song.id
                                    } else {
                                        selectedRevertIds - queueItem.song.id
                                    }
                            },
                            onReviewMatches = {
                                songForFixTags = queueItem.song
                                viewModel.searchMetadataForSong(queueItem.song)
                            },
                            onRetry = { viewModel.retryRepairQueueItem(queueItem.song) },
                            onRemove = { viewModel.removeFromRepairQueue(queueItem.song) },
                            modifier = Modifier.padding(vertical = Spacing.xs),
                        )
                    }
                }
            }

            // ---- More: a quiet row, not a competing button.
            item(key = "more") {
                Column(modifier = Modifier.fillMaxWidth().padding(top = Spacing.xl)) {
                    SectionHeading(title = "More")
                    Column(modifier = Modifier.fillMaxWidth().groupSurface()) {
                        GroupRow(
                            title = "Utility Settings",
                            summary = "Metadata & artwork options",
                            icon = Icons.Default.Settings,
                            trailing = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            onClick = onNavigateToMetadataSettings,
                        )
                    }
                }
            }
        }
    }

    revertCompletionMessage?.let { msg ->
        GlassAlertDialog(
            onDismissRequest = {
                viewModel.dismissRevertCompletionMessage()
                selectedRevertIds = emptySet()
            },
            title = { Text("Revert Complete", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
            text = { Text(msg, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.dismissRevertCompletionMessage()
                        selectedRevertIds = emptySet()
                        viewModel.clearRepairQueue()
                        onNavigateToLibrary()
                    },
                ) {
                    Text("Return to Library")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.dismissRevertCompletionMessage()
                        selectedRevertIds = emptySet()
                    },
                ) {
                    Text("Keep Inspecting Queue")
                }
            },
        )
    }

    if (showCompletionDialog) {
        GlassAlertDialog(
            onDismissRequest = { showCompletionDialog = false },
            title = { Text(if (toProcessCount > 0) "Metadata Repair Paused" else "Metadata Repair Complete") },
            text = {
                Column {
                    Text("✓ ${summary.fixed} tracks fixed successfully", fontWeight = FontWeight.Bold)
                    if (summary.unchanged > 0) {
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        Text("• ${summary.unchanged} tracks matched but nothing was written (see each track)")
                    }
                    if (summary.needsReview > 0) {
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        Text("🔍 ${summary.needsReview} tracks need manual review")
                    }
                    if (summary.noMatch > 0) {
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        Text("• ${summary.noMatch} tracks had no online match")
                    }
                    if (summary.networkFailures + summary.notChecked > 0) {
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        Text(
                            "📡 ${summary.networkFailures + summary.notChecked} tracks not checked yet (connection): " +
                                "they stay in the queue for Resume",
                            fontWeight = FontWeight.Bold,
                        )
                        batchPause?.let { pause ->
                            Text(pause.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    if (summary.otherFailures > 0) {
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        Text("❌ ${summary.otherFailures} tracks failed:", fontWeight = FontWeight.Bold)
                        val failedReasons =
                            repairQueue
                                .filter { it.status == RepairStatus.FAILED }
                                .groupBy { formatFailureReason(it.failureReason) }
                        failedReasons.forEach { (reason, items) ->
                            Text(
                                "  • ${items.size} × $reason",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                if (toProcessCount > 0) {
                    Button(
                        onClick = {
                            showCompletionDialog = false
                            viewModel.startFixingQueue()
                        },
                    ) {
                        Text("Resume ($toProcessCount left)")
                    }
                } else if (summary.needsReview > 0) {
                    Button(
                        onClick = {
                            showCompletionDialog = false
                        },
                    ) {
                        Text("Review Ambiguous Tracks")
                    }
                } else {
                    Button(
                        onClick = {
                            showCompletionDialog = false
                            viewModel.clearRepairQueue()
                            onNavigateToLibrary()
                        },
                    ) {
                        Text("Finish & Return to Library")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showCompletionDialog = false }) {
                    Text("Keep Inspecting Queue")
                }
            },
        )
    }

    songForFixTags?.let { song ->
        FixTagsDialog(
            song = song,
            candidates = metadataCandidates,
            onDismiss = { songForFixTags = null },
            onSaveTags = { title, artist, album, year, genre, bpm ->
                viewModel.writeTags(song, title, artist, album, year, genre, bpm)
                viewModel.removeFromRepairQueue(song)
                songForFixTags = null
                showAmbiguousFixedDialog = true
            },
        )
    }

    if (showAmbiguousFixedDialog) {
        GlassAlertDialog(
            onDismissRequest = { showAmbiguousFixedDialog = false },
            title = { Text("Success", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
            text = { Text("Ambiguous track fixed", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                Button(onClick = { showAmbiguousFixedDialog = false }) {
                    Text("OK")
                }
            },
        )
    }
}

/** One queued track: its artwork, title and status, with its search details and changes on demand. */
@Composable
private fun RepairQueueCard(
    queueItem: RepairQueueItem,
    selectedForRevert: Boolean,
    onSelectForRevert: (Boolean) -> Unit,
    onReviewMatches: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().groupSurface()) {
        if (queueItem.status == "FIXING") {
            ProgressBar(modifier = Modifier.padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.sm))
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
                val isCompleted =
                    queueItem.status == "DONE" ||
                        queueItem.status == "PARTIAL_SUCCESS" ||
                        queueItem.successDetails != null
                if (isCompleted) {
                    Checkbox(
                        checked = selectedForRevert,
                        onCheckedChange = onSelectForRevert,
                        enabled = queueItem.successDetails != null,
                    )
                    Spacer(modifier = Modifier.width(Spacing.xs))
                }

                SongArtwork(
                    song = queueItem.song,
                    modifier =
                        Modifier
                            .size(ArtworkSize.list)
                            .clip(MaterialTheme.shapes.small),
                )

                Spacer(modifier = Modifier.width(Spacing.md))

                // Weighted so the title and subtitle get the row's full width.
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = queueItem.song.title,
                        modifier = Modifier.basicMarquee(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${queueItem.song.artist} • ${queueItem.song.album}",
                        modifier = Modifier.basicMarquee(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (queueItem.status == "UNCHANGED") {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Not changed: ${queueItem.failureReason.orEmpty()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (queueItem.status == RepairStatus.PARTIAL_SUCCESS &&
                        !queueItem.failureReason.isNullOrBlank()
                    ) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Partly fixed: ${queueItem.failureReason}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (queueItem.status == RepairStatus.NETWORK_FAILED) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Not checked (network): ${queueItem.failureReason.orEmpty()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (queueItem.status == RepairStatus.FAILED || queueItem.status == RepairStatus.NO_MATCH) {
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
                    RepairStatus.READY -> Text("Waiting for file approval", style = MaterialTheme.typography.bodySmall)
                    "FIXING" ->
                        Icon(
                            imageVector = Icons.Default.HourglassTop,
                            contentDescription = "Fixing",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    "DONE", "SUCCESS", "PARTIAL_SUCCESS" ->
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Done",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    "UNCHANGED" ->
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Not changed",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    "NEEDS_REVIEW" ->
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Needs Review",
                            tint = MaterialTheme.colorScheme.secondary,
                        )
                    RepairStatus.FAILED, RepairStatus.NO_MATCH, RepairStatus.NETWORK_FAILED ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector =
                                    if (queueItem.status == RepairStatus.NETWORK_FAILED) {
                                        Icons.Default.CloudOff
                                    } else {
                                        Icons.Default.Error
                                    },
                                contentDescription =
                                    when (queueItem.status) {
                                        RepairStatus.NETWORK_FAILED -> "Network failure"
                                        RepairStatus.NO_MATCH -> "No match"
                                        else -> "Failed"
                                    },
                                tint = MaterialTheme.colorScheme.error,
                            )
                            IconButton(
                                onClick = onRetry,
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
                    onClick = onRemove,
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove from Queue",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        var expanded by remember { mutableStateOf(false) }
        val diag = queueItem.diagnostics

        Row(
            modifier = Modifier.fillMaxWidth().padding(start = Spacing.md, end = Spacing.md, bottom = if (expanded) 0.dp else Spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (diag != null) {
                TextButton(
                    onClick = { expanded = !expanded },
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text(if (expanded) "Show less" else "Show more", style = MaterialTheme.typography.labelSmall)
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            if (queueItem.status == RepairStatus.NEEDS_REVIEW) {
                Button(
                    onClick = onReviewMatches,
                    contentPadding = PaddingValues(horizontal = Spacing.md, vertical = 0.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("Review Matches", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        if (diag != null) {
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.md)
                            .padding(bottom = Spacing.md),
                ) {
                    Text(
                        "Search Details",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    // One text per field: a value can never run into the next label.
                    searchDetailLines(diag).forEach { line ->
                        Row(modifier = Modifier.fillMaxWidth().padding(start = (line.indent * 12).dp)) {
                            Text(
                                "${line.label}: ",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                line.value,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.xs))

                    Text(
                        "Provider Results",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    diag.providerOutcomes.forEach { outcome ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                "• ${outcome.providerName}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                            )
                            val statusText = providerStatusText(outcome)
                            Text(
                                statusText,
                                style = MaterialTheme.typography.bodySmall,
                                color =
                                    if (outcome.status ==
                                        com.erhu.erhu.core.model.ProviderStatus.SUCCESS
                                    ) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                            )
                        }
                    }

                    if (diag.topCandidates.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        Text(
                            "Candidates Returned",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        diag.topCandidates.forEach { candidate ->
                            val scorePct = (candidate.confidenceScore * 100).toInt()
                            val isAccepted = scorePct >= 85
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    candidateLine(candidate),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    "$scorePct% (${candidate.sourceName}) ${if (isAccepted) "✓" else "✗"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isAccepted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(
                        "Final Outcome",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        formatFailureReason(diag.finalReason ?: queueItem.failureReason),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
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
                Text(
                    if (showChangesExpanded) "Show less" else "Show changes",
                    style = MaterialTheme.typography.labelSmall,
                )
            }

            AnimatedVisibility(
                visible = showChangesExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.md)
                            .padding(bottom = Spacing.md),
                ) {
                    Text(
                        "Metadata Changes",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (succ.changes.isEmpty()) {
                        Text(
                            "• Tags already up to date (no tag changes needed)",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        succ.changes.forEach { change ->
                            Text(
                                "• ${change.fieldName}: ${change.oldValue?.takeIf {
                                    it.isNotBlank()
                                } ?: "Blank"} → ${change.newValue}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(
                        "File Renaming Result",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (succ.isRenamed && succ.newFilename != null) {
                        Text(
                            "✓ Renamed: ${succ.oldFilename} → ${succ.newFilename}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else if (succ.renameError != null) {
                        Text(
                            "⚠️ Rename skipped: ${succ.renameError}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        Text(
                            "• Filename unchanged: ${succ.oldFilename}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

/** e.g. "All 153 songs in your library selected" or "12 songs matching “igor” selected (3 already queued)". */
private fun selectAllSummary(selection: SelectAllSelection): String {
    val scope = if (selection.query.isEmpty()) "in your library" else "matching “${selection.query}”"
    val already = selection.matched - selection.songIds.size
    val count = if (selection.query.isEmpty()) "All ${selection.matched}" else "${selection.matched}"
    val note = if (already > 0) " ($already already queued)" else ""
    return "$count ${if (selection.matched == 1) "song" else "songs"} $scope selected$note"
}

private fun formatFailureReason(reason: String?): String =
    when {
        reason.isNullOrBlank() -> "No online match found"
        reason == "NO_MATCH" -> "No online match found"
        reason == "FILE_NOT_FOUND" -> "File not found on storage"
        reason == "NETWORK_ERROR" -> "Network error / Offline"
        reason.contains(
            "permission",
            ignoreCase = true,
        ) ||
            reason.contains("writeAccess", ignoreCase = true) -> "Storage write permission denied"
        reason.contains("read-only", ignoreCase = true) -> "File is read-only"
        else -> reason
    }

@Composable
private fun ArtistSquareRow(
    artist: Artist,
    onAdd: () -> Unit,
) {
    Box {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.xs),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(ArtworkSize.compact)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = THUMB_TINT_ALPHA)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = artist.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "Artist • ${artist.songCount} tracks",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onAdd) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Artist", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun AlbumSquareRow(
    album: Album,
    onAdd: () -> Unit,
) {
    Box {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.xs),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(ArtworkSize.compact)
                            .clip(Radius.smallShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = THUMB_TINT_ALPHA)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = album.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "Album • ${album.artist}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onAdd) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Album", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun TrackRow(
    song: Song,
    onAdd: () -> Unit,
) {
    Box {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.xs),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${song.artist} • ${song.album}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onAdd) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Track", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
