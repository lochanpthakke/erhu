package com.erhu.erhu.feature.library.remote

import android.app.PendingIntent
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.LibraryAddCheck
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.erhu.erhu.core.common.dispatcher.DispatcherProvider
import com.erhu.erhu.core.database.repository.RemoteLibraryRepository
import com.erhu.erhu.core.designsystem.component.ActionButton
import com.erhu.erhu.core.designsystem.component.AppScaffold
import com.erhu.erhu.core.designsystem.component.GroupDivider
import com.erhu.erhu.core.designsystem.component.GroupRow
import com.erhu.erhu.core.designsystem.component.LocalCollapsingTitle
import com.erhu.erhu.core.designsystem.component.OverlayHeader
import com.erhu.erhu.core.designsystem.component.ProgressBar
import com.erhu.erhu.core.designsystem.component.ReportTitleScroll
import com.erhu.erhu.core.designsystem.component.SearchField
import com.erhu.erhu.core.designsystem.component.SegmentedSelector
import com.erhu.erhu.core.designsystem.component.TopBarNavigation
import com.erhu.erhu.core.designsystem.component.forScrollingContent
import com.erhu.erhu.core.designsystem.component.groupSurface
import com.erhu.erhu.core.designsystem.component.rememberCollapsingTitleState
import com.erhu.erhu.core.designsystem.foundation.Radius
import com.erhu.erhu.core.designsystem.foundation.Spacing
import com.erhu.erhu.core.designsystem.foundation.TextStyles
import com.erhu.erhu.core.model.RemoteAccessException
import com.erhu.erhu.core.model.RemoteSource
import com.erhu.erhu.core.model.Song
import com.erhu.erhu.core.model.remote
import com.erhu.erhu.core.network.remote.drive.DriveClient
import com.erhu.erhu.core.network.remote.drive.DriveItem
import com.erhu.erhu.core.network.remote.drive.DriveMetadataReader
import com.erhu.erhu.core.network.remote.drive.DriveOrder
import com.erhu.erhu.core.network.remote.drive.DrivePage
import com.erhu.erhu.core.playback.repository.PlaybackRepository
import com.erhu.erhu.feature.library.SongRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** One step of the path from the account's top level down to the open folder. */
data class DriveCrumb(
    val id: String,
    val name: String,
    val driveId: String? = null,
)

private const val SHARED_WITH_ME = "@shared"
private const val SHARED_DRIVES = "@drives"

/** The path as stored text: one crumb per line, tab-separated (id, drive id, name). */
internal fun encodeCrumbs(crumbs: List<DriveCrumb>): String =
    crumbs.joinToString("\n") { "${it.id}\t${it.driveId.orEmpty()}\t${it.name.replace('\t', ' ').replace('\n', ' ')}" }

internal fun decodeCrumbs(text: String?): List<DriveCrumb> =
    text
        .orEmpty()
        .lines()
        .mapNotNull { line ->
            val parts = line.split('\t', limit = 3)
            if (parts.size == 3 && parts[0].isNotEmpty()) DriveCrumb(parts[0], parts[2], parts[1].takeIf { it.isNotEmpty() }) else null
        }

data class DriveBrowseState(
    val sourceName: String = "",
    val crumbs: List<DriveCrumb> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    /** Google wants the user to sign in or agree again before anything can be read. */
    val needsSignIn: Boolean = false,
    val folders: List<DriveItem> = emptyList(),
    val tracks: List<Song> = emptyList(),
    val canLoadMore: Boolean = false,
    val order: DriveOrder = DriveOrder.NAME,
    val searching: Boolean = false,
)

@HiltViewModel
@Suppress("TooManyFunctions")
class DriveBrowseViewModel
    @Inject
    constructor(
        private val repository: RemoteLibraryRepository,
        private val auth: GoogleDriveAuth,
        private val drive: DriveClient,
        private val tags: DriveMetadataReader,
        private val favorites: RemoteFavorites,
        private val playback: PlaybackRepository,
        private val dispatchers: DispatcherProvider,
        private val librarySync: DriveLibrarySync,
    ) : ViewModel() {
        /** This account's folders that are in the Library, with what each sync is doing. */
        private val shownSource = MutableStateFlow(-1L)

        val libraryFolders =
            combine(librarySync.folders, librarySync.status, shownSource) { folders, status, source ->
                folders.filter { it.sourceId == source }.map { it to (status[it.id] ?: FolderSyncStatus()) }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

        /** Adds the folder on screen (subfolders included) to the Library and starts syncing it. */
        fun addToLibrary() {
            val folder = _state.value.crumbs.lastOrNull() ?: return
            librarySync.add(sourceId, folder.id, folder.driveId ?: _state.value.crumbs.firstNotNullOfOrNull { it.driveId }, folder.name)
        }

        fun syncNow(folder: com.erhu.erhu.core.database.entity.RemoteLibraryFolderEntity) = librarySync.syncNow(folder)

        fun removeFromLibrary(folder: com.erhu.erhu.core.database.entity.RemoteLibraryFolderEntity) = librarySync.remove(folder)

        private val _state = MutableStateFlow(DriveBrowseState())
        val state: StateFlow<DriveBrowseState> = _state.asStateFlow()

        val currentSongId: StateFlow<Long?> =
            playback.playbackState
                .map { it.currentSong?.id }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

        private var source: RemoteSource? = null
        private var sourceId = -1L
        private var query = ""
        private var nextPage: String? = null
        private var loadJob: Job? = null
        private var tagJob: Job? = null
        private var started = false

        fun start(sourceId: Long) {
            if (started) return
            started = true
            this.sourceId = sourceId
            shownSource.value = sourceId
            viewModelScope.launch {
                source = repository.getSource(sourceId)
                // Back where the user left off.
                _state.update { it.copy(sourceName = source?.name.orEmpty(), crumbs = decodeCrumbs(repository.getLastLocation(sourceId))) }
                reload()
            }
        }

        fun open(folder: DriveItem) = moveTo(_state.value.crumbs + DriveCrumb(folder.id, folder.name, folder.driveId))

        fun openTop(
            id: String,
            name: String,
        ) = moveTo(listOf(DriveCrumb(id, name)))

        /** Shows the folder at [depth] of the path (0 = the account's top level). */
        fun jumpTo(depth: Int) = moveTo(_state.value.crumbs.take(depth))

        /** One level up, or out of a search. False when already at the top: the screen then closes. */
        fun back(): Boolean {
            val shown = _state.value
            return when {
                shown.searching -> {
                    search("")
                    true
                }
                shown.crumbs.isNotEmpty() -> {
                    moveTo(shown.crumbs.dropLast(1))
                    true
                }
                else -> false
            }
        }

        fun setOrder(order: DriveOrder) {
            if (order == _state.value.order) return
            _state.update { it.copy(order = order) }
            reload()
        }

        fun search(text: String) {
            query = text.trim()
            reload(debounce = query.isNotEmpty())
        }

        private fun moveTo(crumbs: List<DriveCrumb>) {
            query = ""
            _state.update { it.copy(crumbs = crumbs) }
            viewModelScope.launch { repository.setLastLocation(sourceId, encodeCrumbs(crumbs).takeIf { it.isNotEmpty() }) }
            reload()
        }

        fun reload(debounce: Boolean = false) {
            loadJob?.cancel()
            tagJob?.cancel()
            nextPage = null
            val searching = query.isNotEmpty()
            if (!searching && _state.value.crumbs.isEmpty()) {
                // The top level is a fixed menu; nothing to fetch.
                _state.update {
                    it.copy(
                        loading = false,
                        error = null,
                        folders = emptyList(),
                        tracks = emptyList(),
                        canLoadMore = false,
                        searching = false,
                    )
                }
                return
            }
            loadJob =
                viewModelScope.launch {
                    if (debounce) delay(SEARCH_DEBOUNCE_MS)
                    _state.update { it.copy(loading = true, error = null, needsSignIn = false, searching = searching) }
                    fetch(append = false)
                }
        }

        fun loadMore() {
            if (nextPage == null || loadJob?.isActive == true) return
            loadJob = viewModelScope.launch { fetch(append = true) }
        }

        @Suppress("TooGenericExceptionCaught")
        private suspend fun fetch(append: Boolean) {
            try {
                var page = request(nextPage.takeIf { append })
                // Pages are filtered to folders and audio, so one can come back empty while more follow.
                var skipped = 0
                while (page.items.isEmpty() && page.nextPageToken != null && skipped++ < MAX_EMPTY_PAGES) page = request(page.nextPageToken)
                nextPage = page.nextPageToken
                val folders = page.items.filter { it.isFolder }
                val owner = source ?: throw RemoteAccessException(RemoteAccessException.Reason.SOURCE_MISSING, "")
                val songs = repository.toSongs(owner, page.items.filterNot { it.isFolder }.map { it.toTrack() })
                _state.update {
                    it.copy(
                        loading = false,
                        folders = if (append) it.folders + folders else folders,
                        tracks = if (append) it.tracks + songs else songs,
                        canLoadMore = nextPage != null,
                    )
                }
                readTags()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val signIn = (e as? RemoteAccessException)?.reason == RemoteAccessException.Reason.AUTH_REQUIRED
                _state.update { it.copy(loading = false, canLoadMore = false, error = e.remoteMessage(), needsSignIn = signIn) }
            }
        }

        /** One page of what is on screen. A token Google rejects is dropped and asked for once more. */
        private suspend fun request(pageToken: String?): DrivePage {
            suspend fun attempt(): DrivePage {
                val token = withContext(dispatchers.io) { auth.token(sourceId) }
                val shown = _state.value
                val top = shown.crumbs.lastOrNull()
                return when {
                    query.isNotEmpty() -> drive.search(token, query, pageToken)
                    top == null -> DrivePage(emptyList(), null)
                    top.id == SHARED_WITH_ME -> drive.sharedWithMe(token, shown.order, pageToken)
                    top.id == SHARED_DRIVES -> drive.sharedDrives(token, pageToken)
                    else -> drive.children(token, top.id, top.driveId, shown.order, pageToken)
                }
            }
            return try {
                attempt()
            } catch (e: RemoteAccessException) {
                if (e.reason != RemoteAccessException.Reason.AUTH_REQUIRED) throw e
                auth.invalidate(sourceId)
                attempt()
            }
        }

        /**
         * Drive only knows file names. The files' own tags and covers are read one at a time in the
         * background, stored, and shown as they arrive; files read before are skipped.
         */
        private fun readTags() {
            tagJob?.cancel()
            tagJob =
                viewModelScope.launch {
                    _state.value.tracks.filter { it.durationMs == 0L }.take(TAG_PASS_LIMIT).forEach { song ->
                        val fileId = song.remote?.remoteId ?: return@forEach
                        val read = withContext(dispatchers.io) { tags.read(sourceId, fileId) } ?: return@forEach
                        val updated =
                            repository.applyTags(
                                song = song,
                                title = read.title,
                                artist = read.artist,
                                album = read.album,
                                durationMs = read.durationMs,
                                genre = read.genre,
                                year = read.year,
                            )
                        _state.update { page -> page.copy(tracks = page.tracks.map { if (it.id == updated.id) updated else it }) }
                    }
                }
        }

        fun play(index: Int) {
            val tracks = _state.value.tracks
            if (index in tracks.indices) playback.playSongsFromQueue(tracks, index)
        }

        /** A favourite of a Drive track is kept in erhu only: Drive itself is never changed. */
        fun toggleFavorite(song: Song) {
            viewModelScope.launch {
                runCatching { favorites.set(song, !song.isFavorite) }.onSuccess {
                    _state.update { page ->
                        page.copy(tracks = page.tracks.map { if (it.id == song.id) it.copy(isFavorite = !song.isFavorite) else it })
                    }
                }
            }
        }

        /** Asks Google again; [launchConsent] shows Google's screen when the user has to act. */
        @Suppress("TooGenericExceptionCaught")
        fun signIn(launchConsent: (PendingIntent) -> Unit) {
            viewModelScope.launch {
                try {
                    when (val outcome = withContext(dispatchers.io) { auth.authorize(source?.account) }) {
                        is DriveAuthorization.Granted -> {
                            auth.remember(sourceId, outcome.token)
                            reload()
                        }
                        is DriveAuthorization.NeedsConsent -> launchConsent(outcome.consent)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _state.update { it.copy(error = e.remoteMessage()) }
                }
            }
        }

        fun onConsent(
            resultCode: Int,
            data: Intent?,
            launchConsent: (PendingIntent) -> Unit,
        ) {
            when (val outcome = auth.fromConsent(resultCode, data)) {
                is DriveConsentOutcome.Granted -> {
                    auth.remember(sourceId, outcome.token)
                    reload()
                }
                is DriveConsentOutcome.NeedsResolution -> {
                    launchConsent(outcome.consent)
                }
                is DriveConsentOutcome.Canceled -> {
                    _state.update { it.copy(needsSignIn = true) }
                }
                is DriveConsentOutcome.Failed -> {
                    _state.update { it.copy(error = outcome.message, needsSignIn = true) }
                }
            }
        }

        fun onConsent(data: Intent?) {
            onConsent(android.app.Activity.RESULT_OK, data) {}
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
            const val SEARCH_DEBOUNCE_MS = 350L
            const val MAX_EMPTY_PAGES = 5
            const val TAG_PASS_LIMIT = 200
        }
    }

/** A Google Drive account's folders and audio files, in Drive's own structure. */
@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod")
fun DriveBrowseScreen(
    sourceId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DriveBrowseViewModel = hiltViewModel(),
) {
    LaunchedEffect(sourceId) { viewModel.start(sourceId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentSongId by viewModel.currentSongId.collectAsStateWithLifecycle()
    val libraryFolders by viewModel.libraryFolders.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var consentLauncher: androidx.activity.result.ActivityResultLauncher<androidx.activity.result.IntentSenderRequest>? = null
    val consent =
        rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            viewModel.onConsent(
                resultCode = result.resultCode,
                data = result.data,
                launchConsent = { pendingIntent ->
                    consentLauncher?.launch(
                        androidx.activity.result.IntentSenderRequest
                            .Builder(pendingIntent.intentSender)
                            .build(),
                    )
                },
            )
        }
    consentLauncher = consent
    val goBack = {
        if (state.searching) query = ""
        if (!viewModel.back()) onBack()
    }
    BackHandler(enabled = state.searching || state.crumbs.isNotEmpty()) { goBack() }

    val collapsingTitle = rememberCollapsingTitleState()
    AppScaffold(
        topBar = {
            OverlayHeader(
                navigation = TopBarNavigation.Detail(goBack),
                title = state.crumbs.lastOrNull()?.name ?: "Google Drive",
                subtitle = state.sourceName.takeIf { it.isNotBlank() },
                collapsingTitle = collapsingTitle,
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        val scroll = innerPadding.forScrollingContent(topOverlay = true)
        val listState = rememberLazyListState()
        CompositionLocalProvider(LocalCollapsingTitle provides collapsingTitle) { ReportTitleScroll(listState) }
        val atTop = state.crumbs.isEmpty() && !state.searching
        val shownFolder = state.crumbs.lastOrNull()?.takeIf { !state.searching && it.id != SHARED_WITH_ME && it.id != SHARED_DRIVES }
        val synced = shownFolder?.let { shown -> libraryFolders.firstOrNull { it.first.folderId == shown.id } }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(scroll.container),
            contentPadding = PaddingValues(top = scroll.contentTop + Spacing.sm, bottom = scroll.contentBottom + Spacing.xl),
        ) {
            item(key = "search") {
                SearchField(
                    value = query,
                    onValueChange = {
                        query = it
                        viewModel.search(it)
                    },
                    placeholder = "Search file names in this Drive",
                    modifier = Modifier.padding(horizontal = Spacing.screenGutter),
                )
            }
            if (state.crumbs.isNotEmpty() && !state.searching) {
                item(key = "path") { Breadcrumbs(state.crumbs, viewModel::jumpTo) }
                if (state.crumbs.last().id != SHARED_DRIVES) {
                    item(key = "order") {
                        SegmentedSelector(
                            options = DriveOrder.entries.map { it.label },
                            selectedIndex = state.order.ordinal,
                            onSelect = { viewModel.setOrder(DriveOrder.entries[it]) },
                            modifier = Modifier.padding(horizontal = Spacing.screenGutter).padding(bottom = Spacing.sm),
                        )
                    }
                }
            }
            if (shownFolder != null) {
                item(key = "library") {
                    Column(
                        Modifier
                            .padding(horizontal = Spacing.screenGutter)
                            .padding(bottom = Spacing.sm)
                            .fillMaxWidth()
                            .groupSurface(),
                    ) {
                        if (synced == null) {
                            GroupRow(
                                "Add to Library",
                                viewModel::addToLibrary,
                                summary = "Show this folder's music, subfolders included, with your other songs",
                                icon = Icons.Default.LibraryAdd,
                            )
                        } else {
                            val (folder, status) = synced
                            GroupRow(
                                "In your Library",
                                { viewModel.syncNow(folder) },
                                summary = librarySummary(status),
                                icon = Icons.Default.LibraryAddCheck,
                                enabled = !status.running,
                            )
                            GroupDivider()
                            GroupRow(
                                "Remove from Library",
                                { viewModel.removeFromLibrary(folder) },
                                summary = "Drive is not changed",
                                icon = Icons.Default.RemoveCircleOutline,
                            )
                        }
                    }
                }
            }
            if (atTop) {
                item(key = "top") {
                    Column(Modifier.padding(Spacing.screenGutter).fillMaxWidth().groupSurface()) {
                        val chevron = Icons.AutoMirrored.Filled.KeyboardArrowRight
                        GroupRow("My Drive", { viewModel.openTop("root", "My Drive") }, icon = Icons.Default.Cloud, trailing = chevron)
                        GroupDivider()
                        GroupRow(
                            "Shared with me",
                            { viewModel.openTop(SHARED_WITH_ME, "Shared with me") },
                            icon = Icons.Default.FolderShared,
                            trailing = chevron,
                        )
                        GroupDivider()
                        GroupRow(
                            "Shared drives",
                            { viewModel.openTop(SHARED_DRIVES, "Shared drives") },
                            icon = Icons.Default.Groups,
                            trailing = chevron,
                        )
                    }
                }
            }
            if (state.loading) {
                item(key = "loading") { ProgressBar(Modifier.padding(horizontal = Spacing.screenGutter, vertical = Spacing.lg)) }
            }
            state.error?.let { error ->
                item(key = "error") {
                    if (state.needsSignIn) {
                        Column(Modifier.padding(horizontal = Spacing.screenGutter, vertical = Spacing.lg)) {
                            Text(error, style = TextStyles.supporting, color = MaterialTheme.colorScheme.error)
                            ActionButton(
                                text = "Sign in again",
                                primary = true,
                                modifier = Modifier.padding(top = Spacing.md),
                                onClick = {
                                    viewModel.signIn { consent.launch(IntentSenderRequest.Builder(it.intentSender).build()) }
                                },
                            )
                        }
                    } else {
                        Problem(error, onRetry = { viewModel.reload() })
                    }
                }
            }
            if (!atTop && !state.loading && state.error == null && state.folders.isEmpty() && state.tracks.isEmpty()) {
                item(key = "empty") {
                    Note(if (state.searching) "No folders or audio files match." else "No folders or audio files here.")
                }
            }
            items(state.folders, key = { "folder-${it.id}" }) { folder ->
                BrowseRow(
                    title = folder.name,
                    subtitle = "",
                    artworkUri = null,
                    placeholder = Icons.Default.Folder,
                    shape = Radius.smallShape,
                    onClick = {
                        query = ""
                        viewModel.open(folder)
                    },
                )
            }
            // The duration in the key: once a file's tags are read, its row (and cover) is rebuilt.
            itemsIndexed(state.tracks, key = { index, song -> "song-$index-${song.id}-${song.durationMs}" }) { index, song ->
                SongRow(
                    song = song,
                    onClick = { viewModel.play(index) },
                    isHighlighted = song.id == currentSongId,
                    trailing = {
                        IconButton(onClick = { viewModel.toggleFavorite(song) }) {
                            Icon(
                                imageVector = if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = if (song.isFavorite) "Remove from favourites" else "Add to favourites",
                                tint =
                                    if (song.isFavorite) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                            )
                        }
                    },
                )
            }
            if (state.canLoadMore) {
                item(key = "more") {
                    LaunchedEffect(state.folders.size, state.tracks.size) { viewModel.loadMore() }
                    ProgressBar(Modifier.padding(horizontal = Spacing.screenGutter, vertical = Spacing.lg))
                }
            }
        }
    }
}

@Composable
private fun Breadcrumbs(
    crumbs: List<DriveCrumb>,
    onJump: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = { onJump(0) }) { Text("Google Drive") }
        crumbs.forEachIndexed { index, crumb ->
            Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { onJump(index + 1) }, enabled = index != crumbs.lastIndex) { Text(crumb.name, maxLines = 1) }
        }
    }
}

/** One line for a synced folder: progress while syncing, the failure, or how to sync again. */
internal fun librarySummary(status: FolderSyncStatus): String =
    when {
        status.running && status.found == 0 -> "Looking for music in Drive..."
        status.running -> "Syncing: ${status.found} songs found, ${status.tagged} read"
        status.message != null -> "Last sync failed: ${status.message}. Tap to try again"
        else -> "Tap to refresh now. Synced music stays cached until you refresh again"
    }
