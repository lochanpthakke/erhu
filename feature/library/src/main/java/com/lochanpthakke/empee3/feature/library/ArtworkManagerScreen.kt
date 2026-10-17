@file:Suppress("detekt:All")

package com.lochanpthakke.empee3.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lochanpthakke.empee3.core.model.Album
import com.lochanpthakke.empee3.core.model.Artist

@OptIn(ExperimentalMaterial3Api::class)
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

    val sourceOptions by viewModel.metadataSourceOptions.collectAsState()
    val selectedProviderKey by viewModel.selectedProvider.collectAsState()
    val providerOptions = remember(sourceOptions) { sourceOptions.map { it.key to it.label } }
    val currentProviderLabel =
        remember(selectedProviderKey, sourceOptions) {
            sourceOptions.firstOrNull { it.key == selectedProviderKey }?.label ?: selectedProviderKey
        }
    var providerDropdownExpanded by remember { mutableStateOf(false) }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Artwork Fetcher") },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Open Drawer",
                        )
                    }
                },
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = {
                        if (selectedTab != 0) {
                            selectedTab = 0
                            searchQuery = ""
                            viewModel.clearArtworkDiagnostics()
                        }
                    },
                    text = { Text("Albums") },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = {
                        if (selectedTab != 1) {
                            selectedTab = 1
                            searchQuery = ""
                            viewModel.clearArtworkDiagnostics()
                        }
                    },
                    text = { Text("Artists") },
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Source Provider:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Box {
                    TextButton(onClick = { providerDropdownExpanded = true }) {
                        Text(currentProviderLabel, fontWeight = FontWeight.Medium)
                        Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = providerDropdownExpanded,
                        onDismissRequest = { providerDropdownExpanded = false },
                    ) {
                        providerOptions.forEach { (key, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    viewModel.setSelectedProvider(key)
                                    providerDropdownExpanded = false
                                },
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    viewModel.searchLocalLibrary(it)
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(if (selectedTab == 0) "Search albums..." else "Search artists...") },
                leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (searchQuery.isNotBlank()) {
                Text(
                    text = "SEARCH RESULTS (Tap + to add to Queue)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(4.dp))

                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                ) {
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

                Spacer(modifier = Modifier.height(12.dp))
            }

            Button(
                onClick = {
                    if (selectedTab == 0) {
                        viewModel.scanForMissingCovers()
                    } else {
                        viewModel.scanForMissingArtistPhotos()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isScanning && !isSearchingArtwork,
            ) {
                Icon(imageVector = Icons.Default.AutoFixHigh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (isScanning) {
                        "Scanning Storage..."
                    } else if (selectedTab == 0) {
                        "Scan for Missing Album Covers"
                    } else {
                        "Scan for Missing Artist Photos"
                    },
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            val minResolution by viewModel.minArtworkResolution.collectAsState()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Resolution Target: $minResolution×$minResolution px",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row {
                    listOf(300, 500, 1000).forEach { res ->
                        TextButton(
                            onClick = { viewModel.setMinArtworkResolution(res) },
                            enabled = minResolution != res,
                        ) {
                            Text("${res}px", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val currentContextTitle = if (selectedTab == 0) currentProcessingAlbum?.title else currentProcessingArtist?.name
            val currentContextSub =
                if (selectedTab ==
                    0
                ) {
                    currentProcessingAlbum?.artist.orEmpty()
                } else {
                    "${currentProcessingArtist?.songCount ?: 0} tracks"
                }
            var showDiagnosticsExpanded by remember { mutableStateOf(false) }
            val diagnostics by viewModel.artworkSearchDiagnostics.collectAsState()

            if (diagnostics != null && currentContextTitle != null) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { showDiagnosticsExpanded = !showDiagnosticsExpanded },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(
                                    text = "Provider Results & Diagnostics",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
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
                                        color = MaterialTheme.colorScheme.tertiary,
                                    )
                                }
                            }
                            Icon(
                                imageVector = if (showDiagnosticsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                            )
                        }

                        if (showDiagnosticsExpanded) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Provider Results", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            diagnostics!!.providerOutcomes.forEach { outcome ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text("• ${outcome.providerName}", style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        text =
                                            when (outcome.status) {
                                                com.lochanpthakke.empee3.core.model.ProviderStatus.SUCCESS -> "✓ Found ${outcome.candidateCount} candidates"
                                                com.lochanpthakke.empee3.core.model.ProviderStatus.NOT_CONFIGURED -> "Not configured"
                                                com.lochanpthakke.empee3.core.model.ProviderStatus.UNAVAILABLE -> "Not available here"
                                                else -> outcome.status.label
                                            },
                                        style = MaterialTheme.typography.bodySmall,
                                        color =
                                            if (outcome.status ==
                                                com.lochanpthakke.empee3.core.model.ProviderStatus.SUCCESS
                                            ) {
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

            if (currentContextTitle != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "ARTWORK CANDIDATES: $currentContextTitle",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = currentContextSub,
                            style = MaterialTheme.typography.bodySmall,
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        if (isSearchingArtwork) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                LinearProgressIndicator(modifier = Modifier.weight(1f))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Searching artwork...", style = MaterialTheme.typography.bodySmall)
                            }
                        } else if (artworkCandidates.isNotEmpty()) {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                modifier = Modifier.heightIn(max = 280.dp),
                                contentPadding = PaddingValues(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(artworkCandidates) { candidateUrl ->
                                    SquareArtworkCandidateCard(
                                        imageUrl = candidateUrl,
                                        onClick = { viewModel.selectEnlargedArtwork(candidateUrl) },
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "No artwork candidates found.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text =
                        if (selectedTab == 0) {
                            "IN QUEUE FOR ARTWORK ($doneCount / $activeQueueCount ALBUMS)"
                        } else {
                            "IN QUEUE FOR ARTIST PHOTOS ($doneCount / $activeQueueCount ARTISTS)"
                        },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            LinearProgressIndicator(
                progress = { progressFloat },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp),
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (isQueueComplete) {
                Button(
                    onClick = {
                        if (selectedTab == 0) viewModel.clearArtworkQueue() else viewModel.clearArtistArtworkQueue()
                        onNavigateToLibrary()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Finish & Return to Library")
                }
            } else {
                Button(
                    onClick = {
                        if (selectedTab == 0) {
                            val firstPending = artworkQueue.firstOrNull { it.status == "PENDING" }
                            if (firstPending != null) viewModel.searchArtworkForAlbum(firstPending.album)
                        } else {
                            val firstPending = artistArtworkQueue.firstOrNull { it.status == "PENDING" }
                            if (firstPending != null) viewModel.searchArtworkForArtist(firstPending.artist)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled =
                        !isSearchingArtwork &&
                            if (selectedTab == 0) {
                                artworkQueue.any { it.status == "PENDING" }
                            } else {
                                artistArtworkQueue.any { it.status == "PENDING" }
                            },
                ) {
                    Icon(imageVector = Icons.Default.ImageSearch, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isSearchingArtwork) "Searching Artwork..." else "Search for Artwork")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (selectedTab == 0) {
                if (artworkQueue.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Queue is empty. Search for an album or scan for missing artwork to populate the queue.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(artworkQueue, key = { it.album.id }) { queueItem ->
                            AlbumQueueCard(
                                title = queueItem.album.title,
                                subtitle = queueItem.album.artist,
                                status = queueItem.status,
                                onRemove = { viewModel.removeFromArtworkQueue(queueItem.album) },
                            )
                        }
                    }
                }
            } else {
                if (artistArtworkQueue.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Queue is empty. Search for an artist or scan for missing photos to populate the queue.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(artistArtworkQueue, key = { it.artist.id }) { queueItem ->
                            AlbumQueueCard(
                                title = queueItem.artist.name,
                                subtitle = "${queueItem.artist.songCount} tracks",
                                status = queueItem.status,
                                onRemove = { viewModel.removeFromArtistArtworkQueue(queueItem.artist) },
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onNavigateToMetadataSettings,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Utility Settings")
            }

            Spacer(modifier = Modifier.height(110.dp))
        }
    }

    if (selectedEnlargedArtworkUrl != null && (currentProcessingAlbum != null || currentProcessingArtist != null)) {
        val targetTitle = currentProcessingAlbum?.title ?: currentProcessingArtist?.name ?: ""
        AlertDialog(
            onDismissRequest = { viewModel.selectEnlargedArtwork(null) },
            title = { Text("Inspect Artwork: $targetTitle") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AsyncImage(
                        model =
                            ImageRequest
                                .Builder(LocalContext.current)
                                .data(selectedEnlargedArtworkUrl)
                                .setHeader("User-Agent", "empee3/1.0.0 (https://github.com/lochanpthakke/empee3)")
                                .crossfade(true)
                                .build(),
                        contentDescription = "Enlarged Artwork",
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(12.dp)),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text =
                            if (currentProcessingAlbum != null) {
                                "Do you want to embed this cover art into all tracks of $targetTitle?"
                            } else {
                                "Do you want to set this photo as $targetTitle's profile picture?"
                            },
                        style = MaterialTheme.typography.bodySmall,
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
        AlertDialog(
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
private fun SquareArtworkCandidateCard(
    imageUrl: String,
    onClick: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model =
                    ImageRequest
                        .Builder(LocalContext.current)
                        .data(imageUrl)
                        .setHeader("User-Agent", "empee3/1.0.0 (https://github.com/lochanpthakke/empee3)")
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
                        .padding(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
            ) {
                Text(
                    text = if (imageUrl.contains("cover_xl") || imageUrl.contains("picture_xl")) "1000×1000" else "High Res",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun AlbumQueueCard(
    title: String,
    subtitle: String,
    status: String,
    onRemove: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (status == "SEARCHING" || status == "WRITING") {
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
                        .padding(12.dp),
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
                                .size(40.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
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
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(onClick = onRemove) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Remove from Queue")
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumSearchCard(
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
                    .padding(8.dp),
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
                            .size(40.dp)
                            .clip(RoundedCornerShape(4.dp))
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = album.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = album.artist,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onAdd) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Album")
            }
        }
    }
}

@Composable
private fun ArtistSearchCard(
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
                    .padding(8.dp),
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
                            .size(40.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
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
                        text = "${artist.songCount} tracks",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onAdd) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Artist")
            }
        }
    }
}
