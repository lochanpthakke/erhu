@file:Suppress("detekt:All")

package com.erhu.erhu.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.erhu.erhu.core.designsystem.component.ActionButton
import com.erhu.erhu.core.designsystem.component.AppScaffold
import com.erhu.erhu.core.designsystem.component.GlassAlertDialog
import com.erhu.erhu.core.designsystem.component.GroupDivider
import com.erhu.erhu.core.designsystem.component.GroupRow
import com.erhu.erhu.core.designsystem.component.LocalCollapsingTitle
import com.erhu.erhu.core.designsystem.component.OverlayHeader
import com.erhu.erhu.core.designsystem.component.ProgressBar
import com.erhu.erhu.core.designsystem.component.ReportTitleScroll
import com.erhu.erhu.core.designsystem.component.SearchField
import com.erhu.erhu.core.designsystem.component.SectionHeading
import com.erhu.erhu.core.designsystem.component.SegmentedSelector
import com.erhu.erhu.core.designsystem.component.TopBarNavigation
import com.erhu.erhu.core.designsystem.component.forScrollingContent
import com.erhu.erhu.core.designsystem.component.groupSurface
import com.erhu.erhu.core.designsystem.component.rememberCollapsingTitleState
import com.erhu.erhu.core.designsystem.foundation.Spacing
import com.erhu.erhu.core.model.Album
import com.erhu.erhu.core.model.Artist
import com.erhu.erhu.core.model.ProviderStatus

/** The resolutions offered as the minimum artwork size, px. */
private val RESOLUTION_TARGETS = listOf(300, 500, 1000)

/** Leading artwork placeholder of a queue or search row. */
private val ROW_THUMB_SIZE = 40.dp

/** Tint behind a row's placeholder icon, in the theme accent. */
private const val ROW_THUMB_ALPHA = 0.14f

/** The candidates' size badge: a dense backing so it stays readable over any artwork. */
private const val BADGE_FILL_ALPHA = 0.85f

private const val USER_AGENT = "erhu/1.0.0 (https://github.com/lochanpthakke/empee3)"

/**
 * Artwork Fetcher: find missing album covers and artist photos and apply chosen artwork. The page
 * is grouped like Settings (accent section headings over tinted cards) under the app's floating
 * header, with the queue's search as its one prominent action.
 */
@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod", "MagicNumber")
fun ArtworkManagerScreen(
    viewModel: LibraryViewModel,
    onOpenDrawer: () -> Unit,
    onNavigateToMetadataSettings: () -> Unit,
    onNavigateToLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var appliedItemSuccessTitle by remember { mutableStateOf<String?>(null) }

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            viewModel.clearArtworkDiagnostics()
        }
    }

    val artworkQueue by viewModel.artworkQueue.collectAsState()
    val artistArtworkQueue by viewModel.artistArtworkQueue.collectAsState()
    val searchAlbums by viewModel.searchAlbums.collectAsState()
    val searchArtists by viewModel.searchArtists.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val isSearchingArtwork by viewModel.isSearchingArtwork.collectAsState()
    val artworkCandidates by viewModel.artworkCandidates.collectAsState()
    val currentProcessingAlbum by viewModel.currentProcessingAlbum.collectAsState()
    val currentProcessingArtist by viewModel.currentProcessingArtist.collectAsState()
    val selectedEnlargedArtworkUrl by viewModel.selectedEnlargedArtworkUrl.collectAsState()
    val minResolution by viewModel.minArtworkResolution.collectAsState()

    val activeQueueCount = if (selectedTab == 0) artworkQueue.size else artistArtworkQueue.size
    val doneCount =
        if (selectedTab == 0) {
            artworkQueue.count { it.status == "COMPLETED" || it.status == "DONE" || it.status == "SUCCESS" }
        } else {
            artistArtworkQueue.count { it.status == "COMPLETED" || it.status == "DONE" || it.status == "SUCCESS" }
        }
    val isQueueComplete =
        activeQueueCount > 0 &&
            if (selectedTab == 0) {
                artworkQueue.none { it.status == "PENDING" || it.status == "SEARCHING" || it.status == "WRITING" }
            } else {
                artistArtworkQueue.none { it.status == "PENDING" || it.status == "SEARCHING" || it.status == "WRITING" }
            }
    val progressFloat = if (activeQueueCount > 0) doneCount.toFloat() / activeQueueCount.toFloat() else 0f

    // The page title merges into the header button as the page scrolls.
    val collapsingTitle = rememberCollapsingTitleState()
    AppScaffold(
        topBar = {
            OverlayHeader(
                navigation = TopBarNavigation.TopLevel(onOpenDrawer),
                title = "Artwork Fetcher",
                collapsingTitle = collapsingTitle,
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        val scroll = innerPadding.forScrollingContent(topOverlay = true)
        val scrollState = rememberScrollState()
        CompositionLocalProvider(LocalCollapsingTitle provides collapsingTitle) { ReportTitleScroll(scrollState) }
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(scroll.container)
                    .verticalScroll(scrollState)
                    .padding(horizontal = Spacing.screenGutter)
                    .padding(top = scroll.contentTop + Spacing.sm, bottom = scroll.contentBottom + Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
        ) {
            SegmentedSelector(
                options = listOf("Albums", "Artists"),
                selectedIndex = selectedTab,
                onSelect = { index ->
                    if (selectedTab != index) {
                        selectedTab = index
                        searchQuery = ""
                        viewModel.clearArtworkDiagnostics()
                    }
                },
            )

            // ---- Find: where artwork comes from, and what to fetch it for.
            Column(modifier = Modifier.fillMaxWidth()) {
                SectionHeading(title = "Find artwork")
                Column(modifier = Modifier.fillMaxWidth().groupSurface()) {
                    Text(
                        if (selectedTab ==
                            0
                        ) {
                            "MusicBrainz / Cover Art Archive → Deezer → iTunes → TheAudioDB"
                        } else {
                            "Deezer artist catalogue"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(Spacing.lg),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    GroupDivider()
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        SearchField(
                            value = searchQuery,
                            onValueChange = {
                                searchQuery = it
                                viewModel.searchLocalLibrary(it)
                            },
                            placeholder = if (selectedTab == 0) "Search albums..." else "Search artists...",
                        )

                        if (searchQuery.isNotBlank()) {
                            Text(
                                text = "Search results · tap + to add to the queue",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            val resultsEmpty = if (selectedTab == 0) searchAlbums.isEmpty() else searchArtists.isEmpty()
                            if (resultsEmpty) {
                                Text(
                                    text =
                                        if (selectedTab == 0) {
                                            "No matching albums in your library."
                                        } else {
                                            "No matching artists in your library."
                                        },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
                                    if (selectedTab == 0) {
                                        items(searchAlbums) { album ->
                                            AlbumSearchCard(
                                                album = album,
                                                onAdd = {
                                                    viewModel.enqueueAlbumForArtwork(album)
                                                    searchQuery = ""
                                                    viewModel.searchLocalLibrary("")
                                                },
                                            )
                                        }
                                    } else {
                                        items(searchArtists) { artist ->
                                            ArtistSearchCard(
                                                artist = artist,
                                                onAdd = {
                                                    viewModel.enqueueArtistForArtwork(artist)
                                                    searchQuery = ""
                                                    viewModel.searchLocalLibrary("")
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        ActionButton(
                            text =
                                if (isScanning) {
                                    "Scanning Storage..."
                                } else if (selectedTab == 0) {
                                    "Scan for Missing Album Covers"
                                } else {
                                    "Scan for Missing Artist Photos"
                                },
                            onClick = {
                                if (selectedTab == 0) {
                                    viewModel.scanForMissingCovers()
                                } else {
                                    viewModel.scanForMissingArtistPhotos()
                                }
                            },
                            icon = Icons.Default.AutoFixHigh,
                            enabled = !isScanning && !isSearchingArtwork,
                        )
                    }
                }
            }

            // ---- Resolution target.
            Column(modifier = Modifier.fillMaxWidth()) {
                SectionHeading(title = "Resolution target", trailing = "$minResolution×$minResolution px")
                SegmentedSelector(
                    options = RESOLUTION_TARGETS.map { "$it px" },
                    selectedIndex = RESOLUTION_TARGETS.indexOf(minResolution),
                    onSelect = { index ->
                        val res = RESOLUTION_TARGETS[index]
                        if (minResolution != res) viewModel.setMinArtworkResolution(res)
                    },
                )
            }

            // ---- The item being fetched: provider diagnostics and the candidates found.
            val currentContextTitle = if (selectedTab == 0) currentProcessingAlbum?.title else currentProcessingArtist?.name
            val currentContextSub =
                if (selectedTab == 0) {
                    currentProcessingAlbum?.artist.orEmpty()
                } else {
                    "${currentProcessingArtist?.songCount ?: 0} tracks"
                }
            var showDiagnosticsExpanded by remember { mutableStateOf(false) }
            val diagnostics by viewModel.artworkSearchDiagnostics.collectAsState()

            if (currentContextTitle != null) {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    SectionHeading(title = "Artwork candidates")

                    if (diagnostics != null) {
                        Column(modifier = Modifier.fillMaxWidth().groupSurface()) {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable(role = Role.Button) { showDiagnosticsExpanded = !showDiagnosticsExpanded }
                                        .padding(Spacing.lg),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Provider Results & Diagnostics",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    if (diagnostics?.searchQuery.orEmpty().isNotBlank()) {
                                        Text(
                                            text = "Search Query: ${diagnostics!!.searchQuery}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (diagnostics?.resolvedArtistInfo.orEmpty().isNotBlank()) {
                                        Text(
                                            text = diagnostics!!.resolvedArtistInfo!!,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = if (showDiagnosticsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (showDiagnosticsExpanded) "Hide provider results" else "Show provider results",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            if (showDiagnosticsExpanded) {
                                GroupDivider()
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
                                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                                ) {
                                    Text(
                                        "Provider Results",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    diagnostics!!.providerOutcomes.forEach { outcome ->
                                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                                            Text(
                                                text = outcome.providerName,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.weight(1f).padding(end = Spacing.sm),
                                            )
                                            Text(
                                                text =
                                                    when (outcome.status) {
                                                        ProviderStatus.SUCCESS -> "✓ Found ${outcome.candidateCount} candidates"
                                                        ProviderStatus.NOT_CONFIGURED -> "Not configured"
                                                        ProviderStatus.UNAVAILABLE -> "Not available here"
                                                        else -> outcome.status.label
                                                    },
                                                style = MaterialTheme.typography.bodySmall,
                                                color =
                                                    if (outcome.status == ProviderStatus.SUCCESS) {
                                                        MaterialTheme.colorScheme.primary
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                    },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth().groupSurface().padding(Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        Column {
                            Text(
                                text = currentContextTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            if (currentContextSub.isNotBlank()) {
                                Text(
                                    text = currentContextSub,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        if (isSearchingArtwork) {
                            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                ProgressBar()
                                Text(
                                    "Searching artwork...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else if (artworkCandidates.isNotEmpty()) {
                            Text(
                                "Tap a candidate to inspect and apply it.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                modifier = Modifier.heightIn(max = 320.dp),
                                contentPadding = PaddingValues(0.dp),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                            ) {
                                items(artworkCandidates) { candidateUrl ->
                                    SquareArtworkCandidateCard(
                                        imageUrl = candidateUrl,
                                        detail = diagnostics?.candidates?.firstOrNull { it.url == candidateUrl },
                                        selected =
                                            selectedEnlargedArtworkUrl == candidateUrl ||
                                                (
                                                    selectedEnlargedArtworkUrl == null &&
                                                        diagnostics?.candidates?.any { it.url == candidateUrl && it.recommended } == true
                                                ),
                                        onClick = { viewModel.selectEnlargedArtwork(candidateUrl) },
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "No artwork candidates found.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // ---- Queue: progress, the page's primary action, and the queued items.
            Column(modifier = Modifier.fillMaxWidth()) {
                SectionHeading(
                    title = if (selectedTab == 0) "Queue for artwork" else "Queue for artist photos",
                    trailing =
                        if (selectedTab == 0) {
                            "$doneCount / $activeQueueCount albums"
                        } else {
                            "$doneCount / $activeQueueCount artists"
                        },
                )
                Column(modifier = Modifier.fillMaxWidth().groupSurface()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        ProgressBar(progress = { progressFloat })

                        if (isQueueComplete) {
                            ActionButton(
                                text = "Finish & Return to Library",
                                onClick = {
                                    if (selectedTab == 0) viewModel.clearArtworkQueue() else viewModel.clearArtistArtworkQueue()
                                    onNavigateToLibrary()
                                },
                                icon = Icons.Default.CheckCircle,
                                primary = true,
                            )
                        } else {
                            ActionButton(
                                text = if (isSearchingArtwork) "Searching Artwork..." else "Search for Artwork",
                                onClick = {
                                    if (selectedTab == 0) {
                                        val firstPending = artworkQueue.firstOrNull { it.status == "PENDING" }
                                        if (firstPending != null) viewModel.searchArtworkForAlbum(firstPending.album)
                                    } else {
                                        val firstPending = artistArtworkQueue.firstOrNull { it.status == "PENDING" }
                                        if (firstPending != null) viewModel.searchArtworkForArtist(firstPending.artist)
                                    }
                                },
                                icon = Icons.Default.ImageSearch,
                                primary = true,
                                enabled =
                                    !isSearchingArtwork &&
                                        if (selectedTab == 0) {
                                            artworkQueue.any { it.status == "PENDING" }
                                        } else {
                                            artistArtworkQueue.any { it.status == "PENDING" }
                                        },
                            )
                        }
                    }

                    GroupDivider()

                    if (selectedTab == 0) {
                        if (artworkQueue.isEmpty()) {
                            QueueEmpty("Queue is empty. Search for an album or scan for missing artwork to populate the queue.")
                        } else {
                            LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                                items(artworkQueue, key = { it.album.id }) { queueItem ->
                                    AlbumQueueCard(
                                        title = queueItem.album.title,
                                        subtitle = queueItem.album.artist,
                                        status = queueItem.status,
                                        icon = Icons.Default.MusicNote,
                                        onRemove = { viewModel.removeFromArtworkQueue(queueItem.album) },
                                    )
                                }
                            }
                        }
                    } else {
                        if (artistArtworkQueue.isEmpty()) {
                            QueueEmpty("Queue is empty. Search for an artist or scan for missing photos to populate the queue.")
                        } else {
                            LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                                items(artistArtworkQueue, key = { it.artist.id }) { queueItem ->
                                    AlbumQueueCard(
                                        title = queueItem.artist.name,
                                        subtitle = "${queueItem.artist.songCount} tracks",
                                        status = queueItem.status,
                                        icon = Icons.Default.Person,
                                        onRemove = { viewModel.removeFromArtistArtworkQueue(queueItem.artist) },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ---- More: a quiet row, not a competing button.
            Column(modifier = Modifier.fillMaxWidth()) {
                SectionHeading(title = "More")
                Column(modifier = Modifier.fillMaxWidth().groupSurface()) {
                    GroupRow(
                        icon = Icons.Default.Settings,
                        title = "Utility Settings",
                        summary = "Metadata & artwork options",
                        onClick = onNavigateToMetadataSettings,
                        trailing = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    )
                }
            }
        }
    }

    if (selectedEnlargedArtworkUrl != null && (currentProcessingAlbum != null || currentProcessingArtist != null)) {
        val targetTitle = currentProcessingAlbum?.title ?: currentProcessingArtist?.name ?: ""
        GlassAlertDialog(
            onDismissRequest = { viewModel.selectEnlargedArtwork(null) },
            title = { Text("Inspect Artwork: $targetTitle") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AsyncImage(
                        model =
                            ImageRequest
                                .Builder(LocalContext.current)
                                .data(selectedEnlargedArtworkUrl?.let(viewModel::artworkPreviewUrl))
                                .setHeader("User-Agent", USER_AGENT)
                                .crossfade(true)
                                .build(),
                        contentDescription = "Enlarged Artwork",
                        contentScale = ContentScale.Fit,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(MaterialTheme.shapes.medium),
                    )
                    val inspected =
                        viewModel.artworkSearchDiagnostics.collectAsState().value?.candidates?.firstOrNull {
                            it.url ==
                                selectedEnlargedArtworkUrl
                        }
                    inspected?.let { candidate ->
                        Text(
                            listOf(candidate.matchedAlbum, candidate.matchedArtist).filter(String::isNotBlank).joinToString(" \u00b7 "),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            listOf(
                                candidate.sourceProvider,
                                candidate.edition,
                                if (candidate.width >
                                    0
                                ) {
                                    "${candidate.width}\u00d7${candidate.height}"
                                } else {
                                    ""
                                },
                            ).filter(String::isNotBlank).joinToString(" \u00b7 "),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    Spacer(modifier = Modifier.height(Spacing.md))
                    Text(
                        text =
                            if (currentProcessingAlbum != null) {
                                "Do you want to embed this cover art into all tracks of $targetTitle?"
                            } else {
                                "Do you want to set this photo as $targetTitle's profile picture?"
                            },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val albumToApply = currentProcessingAlbum
                        val artistToApply = currentProcessingArtist
                        val urlToApply = selectedEnlargedArtworkUrl
                        val title = albumToApply?.title ?: artistToApply?.name ?: ""

                        if (albumToApply != null && urlToApply != null) {
                            viewModel.applyArtworkToAlbum(albumToApply, urlToApply)
                        } else if (artistToApply != null && urlToApply != null) {
                            viewModel.applyArtistArtwork(artistToApply, urlToApply)
                        }

                        viewModel.selectEnlargedArtwork(null)
                        appliedItemSuccessTitle = title
                    },
                ) {
                    Text("Apply Artwork")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.selectEnlargedArtwork(null) }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (appliedItemSuccessTitle != null) {
        val appliedTitle = appliedItemSuccessTitle.orEmpty()
        GlassAlertDialog(
            onDismissRequest = { appliedItemSuccessTitle = null },
            title = { Text("Artwork Applied Successfully") },
            text = {
                Text(
                    text = "Artwork for \"$appliedTitle\" has been updated.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        appliedItemSuccessTitle = null
                    },
                ) {
                    Text("Fetch More Artwork")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        appliedItemSuccessTitle = null
                        if (selectedTab == 0) viewModel.clearArtworkQueue() else viewModel.clearArtistArtworkQueue()
                        onNavigateToLibrary()
                    },
                ) {
                    Text("Return to Library")
                }
            },
        )
    }
}

@Composable
private fun ColumnScope.QueueEmpty(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
    )
}

/** Placeholder artwork of a row: the item's kind on a soft accent tile. */
@Composable
private fun RowThumb(
    icon: ImageVector,
    shape: androidx.compose.ui.graphics.Shape,
) {
    Box(
        modifier =
            Modifier
                .size(ROW_THUMB_SIZE)
                .clip(shape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = ROW_THUMB_ALPHA)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun SquareArtworkCandidateCard(
    imageUrl: String,
    detail: ArtworkCandidateDetail?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(
                        if (selected) 2.dp else 0.dp,
                        if (selected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                        MaterialTheme.shapes.small,
                    ).clickable(role = Role.Button, onClick = onClick),
        ) {
            AsyncImage(
                model =
                    ImageRequest
                        .Builder(LocalContext.current)
                        .data(detail?.previewUrl ?: imageUrl)
                        .setHeader("User-Agent", USER_AGENT)
                        .crossfade(true)
                        .build(),
                contentDescription = "Artwork Candidate",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(Spacing.xs)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = BADGE_FILL_ALPHA))
                        .padding(horizontal = Spacing.sm, vertical = 2.dp),
            ) {
                Text(
                    text = if (detail != null && detail.width > 0) "${detail.width}×${detail.height}" else "Preview",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        detail?.let { candidate ->
            Text(candidate.sourceProvider, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            if (candidate.matchedAlbum.isNotBlank()) Text(candidate.matchedAlbum, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
            if (candidate.matchedArtist.isNotBlank()) {
                Text(
                    candidate.matchedArtist,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                )
            }
            val edition =
                listOfNotNull(
                    candidate.year.takeIf { it > 0 }?.toString(),
                    candidate.edition.takeIf(String::isNotBlank),
                ).joinToString(" · ")
            if (edition.isNotBlank()) Text(edition, style = MaterialTheme.typography.labelSmall)
            Text(
                if (candidate.recommended) "Recommended match" else candidate.reviewReasons.firstOrNull() ?: "Choose manually",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AlbumQueueCard(
    title: String,
    subtitle: String,
    status: String,
    icon: ImageVector,
    onRemove: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = Spacing.lg, end = Spacing.xs, top = Spacing.sm, bottom = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RowThumb(icon = icon, shape = MaterialTheme.shapes.small)
            Spacer(modifier = Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.width(Spacing.sm))
            when (status) {
                "SEARCHING", "WRITING" ->
                    Icon(
                        imageVector = Icons.Default.HourglassTop,
                        contentDescription = "Searching",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                "COMPLETED", "DONE", "SUCCESS" ->
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Done",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                "FAILED" ->
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = "Failed",
                        tint = MaterialTheme.colorScheme.error,
                    )
                else ->
                    Text(
                        text = "Pending",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove from Queue",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (status == "SEARCHING" || status == "WRITING") {
            ProgressBar(modifier = Modifier.padding(horizontal = Spacing.lg).padding(bottom = Spacing.sm))
        }
    }
}

@Composable
private fun SearchResultRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    thumbShape: androidx.compose.ui.graphics.Shape,
    addDescription: String,
    onAdd: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowThumb(icon = icon, shape = thumbShape)
        Spacer(modifier = Modifier.width(Spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onAdd) {
            Icon(imageVector = Icons.Default.Add, contentDescription = addDescription, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun AlbumSearchCard(
    album: Album,
    onAdd: () -> Unit,
) {
    SearchResultRow(
        title = album.title,
        subtitle = album.artist,
        icon = Icons.Default.MusicNote,
        thumbShape = MaterialTheme.shapes.small,
        addDescription = "Add Album",
        onAdd = onAdd,
    )
}

@Composable
private fun ArtistSearchCard(
    artist: Artist,
    onAdd: () -> Unit,
) {
    SearchResultRow(
        title = artist.name,
        subtitle = "${artist.songCount} tracks",
        icon = Icons.Default.Person,
        thumbShape = CircleShape,
        addDescription = "Add Artist",
        onAdd = onAdd,
    )
}
