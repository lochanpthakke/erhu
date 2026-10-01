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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
    var searchQuery by remember { mutableStateOf("") }
    var showCompletionDialog by remember { mutableStateOf(false) }

    val artworkQueue by viewModel.artworkQueue.collectAsState()
    val searchAlbums by viewModel.searchAlbums.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val isSearchingArtwork by viewModel.isSearchingArtwork.collectAsState()
    val artworkCandidates by viewModel.artworkCandidates.collectAsState()
    val currentProcessingAlbum by viewModel.currentProcessingAlbum.collectAsState()
    val selectedEnlargedArtworkUrl by viewModel.selectedEnlargedArtworkUrl.collectAsState()

    val doneCount = artworkQueue.count { it.status == "COMPLETED" || it.status == "DONE" || it.status == "SUCCESS" }
    val failedCount = artworkQueue.count { it.status == "FAILED" }
    val totalCount = artworkQueue.size
    val isQueueComplete =
        totalCount > 0 && artworkQueue.none { it.status == "PENDING" || it.status == "SEARCHING" || it.status == "WRITING" }
    val progressFloat = if (totalCount > 0) doneCount.toFloat() / totalCount.toFloat() else 0f

    LaunchedEffect(isQueueComplete) {
        if (isQueueComplete) {
            showCompletionDialog = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Artwork Manager") },
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
        bottomBar = {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
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
                    Spacer(modifier = Modifier.padding(horizontal = 4.dp))
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
                    .padding(horizontal = 16.dp),
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    viewModel.searchLocalLibrary(it)
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search albums...") },
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
                    items(searchAlbums) { album ->
                        AlbumSearchCard(
                            album = album,
                            onAdd = { viewModel.enqueueAlbumForArtwork(album) },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            Button(
                onClick = { viewModel.scanForMissingCovers() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isScanning && !isSearchingArtwork,
            ) {
                Icon(imageVector = Icons.Default.AutoFixHigh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isScanning) "Scanning Storage..." else "Scan for Missing Album Covers")
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (currentProcessingAlbum != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "ARTWORK CANDIDATES: ${currentProcessingAlbum!!.title}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = currentProcessingAlbum!!.artist,
                            style = MaterialTheme.typography.bodySmall,
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        if (isSearchingArtwork) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                LinearProgressIndicator(modifier = Modifier.weight(1f))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Searching covers...", style = MaterialTheme.typography.bodySmall)
                            }
                        } else if (artworkCandidates.isNotEmpty()) {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                modifier = Modifier.height(180.dp),
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
                                text = "No artwork candidates found for this album.",
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
                    text = "IN QUEUE FOR ARTWORK ($doneCount / $totalCount ALBUMS)",
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
                        viewModel.clearArtworkQueue()
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
                        val firstPending = artworkQueue.firstOrNull { it.status == "PENDING" }
                        if (firstPending != null) {
                            viewModel.searchArtworkForAlbum(firstPending.album)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSearchingArtwork && artworkQueue.any { it.status == "PENDING" },
                ) {
                    Icon(imageVector = Icons.Default.ImageSearch, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isSearchingArtwork) "Searching Artwork..." else "Search for Artwork")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (artworkQueue.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Queue is empty. Search for an album or scan for missing artwork to populate the artwork queue.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(artworkQueue, key = { it.album.id }) { queueItem ->
                        Card(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                if (queueItem.status == "SEARCHING" || queueItem.status == "WRITING") {
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
                                                text = queueItem.album.title,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = queueItem.album.artist,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        when (queueItem.status) {
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

                                        IconButton(
                                            onClick = {
                                                viewModel.removeFromArtworkQueue(queueItem.album)
                                            },
                                        ) {
                                            Icon(imageVector = Icons.Default.Close, contentDescription = "Remove from Queue")
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

    if (selectedEnlargedArtworkUrl != null && currentProcessingAlbum != null) {
        AlertDialog(
            onDismissRequest = { viewModel.selectEnlargedArtwork(null) },
            title = { Text("Inspect Cover: ${currentProcessingAlbum!!.title}") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AsyncImage(
                        model =
                            ImageRequest
                                .Builder(LocalContext.current)
                                .data(selectedEnlargedArtworkUrl)
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
                        text = "Do you want to embed this cover art into all tracks of ${currentProcessingAlbum!!.title}?",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.applyArtworkToAlbum(currentProcessingAlbum!!, selectedEnlargedArtworkUrl!!)
                    },
                ) {
                    Text("Add Artwork")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.selectEnlargedArtwork(null) }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showCompletionDialog) {
        AlertDialog(
            onDismissRequest = { showCompletionDialog = false },
            title = { Text("Artwork Manager Complete") },
            text = {
                Column {
                    Text("✓ $doneCount albums updated successfully", fontWeight = FontWeight.Bold)
                    if (failedCount > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("❌ $failedCount albums failed")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCompletionDialog = false
                        viewModel.clearArtworkQueue()
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
        AsyncImage(
            model =
                ImageRequest
                    .Builder(LocalContext.current)
                    .data(imageUrl)
                    .crossfade(true)
                    .build(),
            contentDescription = "Artwork Candidate",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
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
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                Column {
                    Text(text = album.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(text = album.artist, style = MaterialTheme.typography.bodySmall)
                }
            }
            OutlinedButton(onClick = onAdd) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Album")
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add")
            }
        }
    }
}
