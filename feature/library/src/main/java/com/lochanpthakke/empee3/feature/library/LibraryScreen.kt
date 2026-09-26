@file:Suppress("detekt:All")

package com.lochanpthakke.empee3.feature.library

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.lochanpthakke.empee3.core.model.Album
import com.lochanpthakke.empee3.core.model.Artist
import com.lochanpthakke.empee3.core.model.Song

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod", "MagicNumber")
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val songs = viewModel.songsPaged.collectAsLazyPagingItems()
    val favoriteSongs = viewModel.favoriteSongsPaged.collectAsLazyPagingItems()
    val albums = viewModel.albumsPaged.collectAsLazyPagingItems()
    val artists = viewModel.artistsPaged.collectAsLazyPagingItems()
    val candidates by viewModel.metadataCandidates.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()

    var selectedTabIndex by remember { mutableIntStateOf(1) }
    var songForFixTags by remember { mutableStateOf<Song?>(null) }

    val tabs = listOf("Play Now", "Tracks", "Favorites", "Albums", "Artists", "Genres")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Library") },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Open Navigation Drawer",
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { /* Open Search */ }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search Library",
                        )
                    }
                },
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isScanning,
            onRefresh = { viewModel.triggerScan() },
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                ScrollableTabRow(
                    selectedTabIndex = selectedTabIndex,
                    edgePadding = 16.dp,
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = { Text(title) },
                        )
                    }
                }

                when (selectedTabIndex) {
                    0 -> PlayNowTab(songs = songs, albums = albums, artists = artists)
                    1 ->
                        SongsTab(
                            songs = songs,
                            onSongClickAtIndex = { songList, index ->
                                viewModel.playSongsFromQueue(songList, index)
                            },
                            onToggleFavorite = { viewModel.toggleFavorite(it) },
                            onFixTagsClick = { song ->
                                songForFixTags = song
                                viewModel.searchMetadataForSong(song)
                            },
                        )
                    2 ->
                        SongsTab(
                            songs = favoriteSongs,
                            emptyMessage = "No favorite tracks added yet. Tap the heart icon on any song to add it to Favorites!",
                            onSongClickAtIndex = { songList, index ->
                                viewModel.playSongsFromQueue(songList, index)
                            },
                            onToggleFavorite = { viewModel.toggleFavorite(it) },
                            onFixTagsClick = { song ->
                                songForFixTags = song
                                viewModel.searchMetadataForSong(song)
                            },
                        )
                    3 -> AlbumsTab(albums = albums, onAlbumClick = { viewModel.playAlbum(it) })
                    4 -> ArtistsTab(artists = artists, onArtistClick = { viewModel.playArtist(it) })
                    5 ->
                        EmptyLibraryView(message = "Genres will automatically group here as tracks are scanned.")
                }
            }
        }
    }

    songForFixTags?.let { song ->
        FixTagsDialog(
            song = song,
            candidates = candidates,
            onDismiss = { songForFixTags = null },
            onSaveTags = { title, artist, album, year, genre ->
                viewModel.writeTags(song, title, artist, album, year, genre)
                songForFixTags = null
            },
        )
    }
}

@Composable
private fun PlayNowTab(
    songs: LazyPagingItems<Song>,
    albums: LazyPagingItems<Album>,
    artists: LazyPagingItems<Artist>,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
    ) {
        item {
            SectionHeader(title = "Popular Artists")
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {},
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("PLAY")
                }
                OutlinedButton(
                    onClick = {},
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(imageVector = Icons.Default.Shuffle, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("SHUFFLE PLAY")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            SectionHeader(title = "Popular Albums")
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {},
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("PLAY")
                }
                OutlinedButton(
                    onClick = {},
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(imageVector = Icons.Default.Shuffle, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("SHUFFLE PLAY")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            SectionHeader(title = "Favourites Smart Playlist")
            Card(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Liked Songs Playlist",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Your auto-generated favorites collection",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {}) { Text("PLAY") }
                        OutlinedButton(onClick = {}) { Text("SHUFFLE PLAY") }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            SectionHeader(title = "Most Played")
            Spacer(modifier = Modifier.height(8.dp))
        }

        item {
            SectionHeader(title = "Never Played")
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            Card(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "All-Time Listening Stats",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Total Tracks: ${songs.itemCount} • Total Albums: ${albums.itemCount} • Total Artists: ${artists.itemCount}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Played this month: 0 hrs • All-Time: 0 hrs",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun SongsTab(
    songs: LazyPagingItems<Song>,
    onSongClickAtIndex: (List<Song>, Int) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onFixTagsClick: (Song) -> Unit,
    modifier: Modifier = Modifier,
    emptyMessage: String = "No songs found. Drag down to refresh and scan local storage.",
) {
    if (songs.itemCount == 0) {
        EmptyLibraryView(message = emptyMessage)
    } else {
        val songList =
            remember(songs.itemCount) {
                val list = mutableListOf<Song>()
                for (i in 0 until songs.itemCount) {
                    songs[i]?.let { list.add(it) }
                }
                list
            }

        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
        ) {
            items(
                count = songs.itemCount,
                key = songs.itemKey { it.id },
            ) { index ->
                val song = songs[index]
                if (song != null) {
                    SongItem(
                        song = song,
                        onClick = { onSongClickAtIndex(songList, index) },
                        onToggleFavorite = { onToggleFavorite(song) },
                        onFixTagsClick = { onFixTagsClick(song) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumsTab(
    albums: LazyPagingItems<Album>,
    onAlbumClick: (Album) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (albums.itemCount == 0) {
        EmptyLibraryView(message = "No albums found. Drag down to refresh and scan local storage.")
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
        ) {
            items(
                count = albums.itemCount,
                key = albums.itemKey { it.id },
            ) { index ->
                val album = albums[index]
                if (album != null) {
                    AlbumItem(album = album, onClick = { onAlbumClick(album) })
                }
            }
        }
    }
}

@Composable
private fun ArtistsTab(
    artists: LazyPagingItems<Artist>,
    onArtistClick: (Artist) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (artists.itemCount == 0) {
        EmptyLibraryView(message = "No artists found. Drag down to refresh and scan local storage.")
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
        ) {
            items(
                count = artists.itemCount,
                key = artists.itemKey { it.id },
            ) { index ->
                val artist = artists[index]
                if (artist != null) {
                    ArtistItem(artist = artist, onClick = { onArtistClick(artist) })
                }
            }
        }
    }
}

@Composable
private fun SongItem(
    song: Song,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onFixTagsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
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
                if (song.artworkData != null && song.artworkData!!.isNotEmpty()) {
                    val bitmap =
                        android.graphics.BitmapFactory.decodeByteArray(
                            song.artworkData,
                            0,
                            song.artworkData!!.size,
                        )
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Song Artwork",
                            modifier =
                                Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                        )
                    } else {
                        FallbackSongArt()
                    }
                } else {
                    FallbackSongArt()
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row {
                        Text(
                            text = song.artist,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "• ${song.album}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Toggle Favorite",
                        tint = if (song.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Song Options",
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Fix Tags / Metadata") },
                            onClick = {
                                menuExpanded = false
                                onFixTagsClick()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FallbackSongArt() {
    Box(
        modifier =
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun AlbumItem(
    album: Album,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier =
            modifier
                .padding(8.dp)
                .fillMaxWidth()
                .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = album.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = album.artist,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${album.songCount} songs",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ArtistItem(
    artist: Artist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = artist.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${artist.songCount} songs • ${artist.albumCount} albums",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyLibraryView(
    message: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(32.dp),
        )
    }
}
