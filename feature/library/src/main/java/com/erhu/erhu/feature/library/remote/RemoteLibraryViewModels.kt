package com.erhu.erhu.feature.library.remote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erhu.erhu.core.common.dispatcher.DispatcherProvider
import com.erhu.erhu.core.database.repository.RemoteLibraryRepository
import com.erhu.erhu.core.model.RemoteAccessException
import com.erhu.erhu.core.model.RemoteProvider
import com.erhu.erhu.core.model.RemoteSource
import com.erhu.erhu.core.model.RemoteTrack
import com.erhu.erhu.core.model.Song
import com.erhu.erhu.core.model.remote
import com.erhu.erhu.core.network.remote.RemoteCredentialStore
import com.erhu.erhu.core.network.remote.subsonic.SubsonicAlbum
import com.erhu.erhu.core.network.remote.subsonic.SubsonicArtist
import com.erhu.erhu.core.network.remote.subsonic.SubsonicClient
import com.erhu.erhu.core.network.remote.subsonic.SubsonicConnection
import com.erhu.erhu.core.network.remote.subsonic.SubsonicConnections
import com.erhu.erhu.core.network.remote.subsonic.SubsonicPlaylist
import com.erhu.erhu.core.network.remote.subsonic.parseServerUrl
import com.erhu.erhu.core.playback.repository.PlaybackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException

/** What went wrong, in words for the user. Never includes an address with credentials. */
internal fun Throwable.remoteMessage(): String =
    when (this) {
        is RemoteAccessException ->
            when (reason) {
                RemoteAccessException.Reason.AUTH_REQUIRED -> message ?: "Sign in again"
                RemoteAccessException.Reason.SOURCE_MISSING -> "This server is no longer connected"
                RemoteAccessException.Reason.NOT_FOUND -> "The server no longer has this item"
            }
        is UnknownHostException -> "The server could not be found. Check the address and your connection."
        is ConnectException, is SocketTimeoutException -> "The server did not answer. Check that it is running and reachable."
        is SSLException -> "A secure connection could not be made. The server's certificate was not accepted."
        is IOException -> message?.takeIf { it.isNotBlank() } ?: "The server could not be reached"
        else -> "Something went wrong"
    }

/** Favourites of remote tracks live on their server; the local row only mirrors them. */
@Singleton
class RemoteFavorites
    @Inject
    constructor(
        private val repository: RemoteLibraryRepository,
        private val connections: SubsonicConnections,
        private val subsonic: SubsonicClient,
        private val dispatchers: DispatcherProvider,
    ) {
        fun observe(songId: Long) = repository.observeIsFavorite(songId)

        /** Flips the stored state (the queue's copy of a song can be out of date). */
        suspend fun toggle(song: Song) = set(song, !repository.observeIsFavorite(song.id).first())

        /** Stars or unstars [song] on its server. Throws when the server cannot be told. */
        suspend fun set(
            song: Song,
            favorite: Boolean,
        ) {
            val ref = song.remote ?: return
            when (ref.provider) {
                RemoteProvider.SUBSONIC -> {
                    val connection = withContext(dispatchers.io) { connections.connection(ref.sourceId) }
                    subsonic.setStarred(connection, ref.remoteId, favorite)
                }
                // Drive has no favourites of its own that erhu may change (read-only access).
                RemoteProvider.GOOGLE_DRIVE -> Unit
            }
            repository.setFavorite(song.id, favorite)
        }
    }

data class AddServerState(
    val connecting: Boolean = false,
    val error: String? = null,
    /** Bumped after a server was added, so the form can clear itself. */
    val added: Int = 0,
)

@HiltViewModel
class RemoteLibrariesViewModel
    @Inject
    constructor(
        private val repository: RemoteLibraryRepository,
        private val credentials: RemoteCredentialStore,
        private val connections: SubsonicConnections,
        private val subsonic: SubsonicClient,
        private val driveAuth: GoogleDriveAuth,
        private val drive: com.erhu.erhu.core.network.remote.drive.DriveClient,
        private val driveTags: com.erhu.erhu.core.network.remote.drive.DriveMetadataReader,
        private val dispatchers: DispatcherProvider,
    ) : ViewModel() {
        private val _driveState = MutableStateFlow(AddServerState())

        /** Progress and problems of connecting a Google account. */
        val driveState: StateFlow<AddServerState> = _driveState.asStateFlow()

        /** Starts Google's sign-in; [launchConsent] shows Google's screen when the user has to choose or agree. */
        fun connectDrive(launchConsent: (android.app.PendingIntent) -> Unit) {
            if (_driveState.value.connecting) return
            _driveState.value = AddServerState(connecting = true)
            viewModelScope.launch {
                try {
                    when (val outcome = withContext(dispatchers.io) { driveAuth.authorize(null) }) {
                        is DriveAuthorization.Granted -> finishDrive(outcome.token)
                        is DriveAuthorization.NeedsConsent -> launchConsent(outcome.consent)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (
                    @Suppress("TooGenericExceptionCaught") e: Exception,
                ) {
                    _driveState.value = AddServerState(error = e.remoteMessage())
                }
            }
        }

        fun onDriveConsent(
            resultCode: Int,
            data: android.content.Intent?,
            launchConsent: (android.app.PendingIntent) -> Unit,
        ) {
            when (val outcome = driveAuth.fromConsent(resultCode, data)) {
                is DriveConsentOutcome.Granted -> {
                    viewModelScope.launch {
                        try {
                            finishDrive(outcome.token)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (
                            @Suppress("TooGenericExceptionCaught") e: Exception,
                        ) {
                            _driveState.value = AddServerState(error = e.remoteMessage())
                        }
                    }
                }
                is DriveConsentOutcome.NeedsResolution -> {
                    launchConsent(outcome.consent)
                }
                is DriveConsentOutcome.Canceled -> {
                    _driveState.value = AddServerState(connecting = false)
                }
                is DriveConsentOutcome.Failed -> {
                    _driveState.value = AddServerState(error = outcome.message)
                }
            }
        }

        fun onDriveConsent(data: android.content.Intent?) {
            onDriveConsent(android.app.Activity.RESULT_OK, data) {}
        }

        /** Asks Drive whose account the token belongs to, then stores the account (never the token). */
        private suspend fun finishDrive(token: String) {
            val account = drive.about(token)
            val known = sources.value.firstOrNull { it.provider == RemoteProvider.GOOGLE_DRIVE && it.account.equals(account.email, ignoreCase = true) }
            if (known != null) {
                driveAuth.remember(known.id, token)
                _driveState.value = AddServerState(error = "${account.email} is already connected.")
                return
            }
            val id =
                repository.addSource(
                    RemoteSource(id = 0, provider = RemoteProvider.GOOGLE_DRIVE, name = account.email.ifBlank { "Google Drive" }, account = account.email),
                )
            driveAuth.remember(id, token)
            _driveState.value = AddServerState()
        }

        val sources: StateFlow<List<RemoteSource>> =
            repository.observeSources().stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

        private val _addServer = MutableStateFlow(AddServerState())
        val addServer: StateFlow<AddServerState> = _addServer.asStateFlow()

        /** Checks the server answers and accepts the sign-in before anything is stored. */
        @Suppress("ReturnCount")
        fun connect(
            address: String,
            username: String,
            password: String,
            allowInsecure: Boolean,
            useApiKey: Boolean = false,
        ) {
            if (_addServer.value.connecting) return
            val url = parseServerUrl(address) ?: return fail("Enter the server's address, for example music.example.com")
            if (useApiKey && password.isBlank()) return fail("Enter the API key")
            if (!useApiKey && (username.isBlank() || password.isEmpty())) return fail("Enter your user name and password")
            if (!url.isHttps && !allowInsecure) return fail("This address is not encrypted. Allow it below, or use an https:// address.")
            _addServer.update { it.copy(connecting = true, error = null) }
            viewModelScope.launch {
                try {
                    val user = if (useApiKey) "" else username.trim()
                    subsonic.ping(SubsonicConnection(url, user, password))
                    val id =
                        repository.addSource(
                            RemoteSource(
                                id = 0,
                                provider = RemoteProvider.SUBSONIC,
                                name = url.host,
                                baseUrl = url.toString(),
                                account = user,
                                allowInsecure = !url.isHttps,
                            ),
                        )
                    try {
                        withContext(dispatchers.io) { credentials.save(id, password) }
                    } catch (e: IOException) {
                        // A source without its password would only ever fail: do not keep it.
                        repository.removeSource(id)
                        throw e
                    }
                    _addServer.update { AddServerState(added = it.added + 1) }
                } catch (e: CancellationException) {
                    throw e
                } catch (
                    @Suppress("TooGenericExceptionCaught") e: Exception,
                ) {
                    _addServer.update { it.copy(connecting = false, error = e.remoteMessage()) }
                }
            }
        }

        /** Forgets the server, its stored password and its tracks. Nothing on the server changes. */
        fun remove(source: RemoteSource) {
            viewModelScope.launch {
                repository.removeSource(source.id)
                connections.forget(source.id)
                driveAuth.invalidate(source.id)
                driveTags.forget(source.id)
                withContext(dispatchers.io) { credentials.delete(source.id) }
            }
        }

        private fun fail(message: String) {
            _addServer.update { it.copy(connecting = false, error = message) }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }

/** The pages of a server's browser. [name] travels in the route. */
enum class RemotePageKind {
    HOME,
    ARTISTS,
    ARTIST,
    ALBUMS,
    ALBUM,
    PLAYLISTS,
    PLAYLIST,
    FAVORITES,
    ;

    companion object {
        fun of(name: String): RemotePageKind = entries.firstOrNull { it.name == name } ?: HOME
    }
}

data class RemotePageState(
    val sourceName: String = "",
    val loading: Boolean = true,
    val error: String? = null,
    val artists: List<SubsonicArtist> = emptyList(),
    val albums: List<SubsonicAlbum> = emptyList(),
    val playlists: List<SubsonicPlaylist> = emptyList(),
    val tracks: List<Song> = emptyList(),
    /** More albums can be loaded (the all-albums page only). */
    val canLoadMore: Boolean = false,
    /** A one-off note (a favourite that could not be saved). */
    val notice: String? = null,
) {
    val isEmpty: Boolean get() = artists.isEmpty() && albums.isEmpty() && playlists.isEmpty() && tracks.isEmpty()
}

@HiltViewModel
class RemoteBrowseViewModel
    @Inject
    constructor(
        private val repository: RemoteLibraryRepository,
        private val connections: SubsonicConnections,
        private val subsonic: SubsonicClient,
        private val favorites: RemoteFavorites,
        private val playback: PlaybackRepository,
        private val dispatchers: DispatcherProvider,
        private val librarySync: DriveLibrarySync,
    ) : ViewModel() {
        private val shownSource = MutableStateFlow(-1L)

        /** The Library entry the page on screen stands for (see [libraryKeyFor]); null where there is none. */
        private val shownKey = MutableStateFlow<String?>(null)
        private var shownTitle = ""

        /** The page's entry in erhu's Library and its sync, and whether the whole server already covers it. */
        val libraryEntry =
            kotlinx.coroutines.flow
                .combine(librarySync.folders, librarySync.status, shownSource, shownKey) { folders, status, source, key ->
                    val mine = folders.filter { it.sourceId == source }
                    LibraryRowState(
                        entry = mine.firstOrNull { it.folderId == key }?.let { it to (status[it.id] ?: FolderSyncStatus()) },
                        coveredByServer = key != DriveLibrarySync.WHOLE_SERVER && mine.any { it.folderId == DriveLibrarySync.WHOLE_SERVER },
                        available = key != null,
                    )
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LibraryRowState())

        fun addLibraryToErhu() {
            val id = shownSource.value.takeIf { it > 0 } ?: return
            val key = shownKey.value ?: return
            viewModelScope.launch {
                val name =
                    if (key == DriveLibrarySync.WHOLE_SERVER) {
                        repository.getSource(id)?.name ?: "Server library"
                    } else {
                        shownTitle.ifBlank { "Server music" }
                    }
                librarySync.add(id, key, null, name)
            }
        }

        fun syncLibraryNow(folder: com.erhu.erhu.core.database.entity.RemoteLibraryFolderEntity) = librarySync.syncNow(folder)

        fun removeLibraryFromErhu(folder: com.erhu.erhu.core.database.entity.RemoteLibraryFolderEntity) = librarySync.remove(folder)

        private val _state = MutableStateFlow(RemotePageState())
        val state: StateFlow<RemotePageState> = _state.asStateFlow()

        val currentSongId: StateFlow<Long?> =
            playback.playbackState
                .map { it.currentSong?.id }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

        private var sourceId = -1L
        private var kind = RemotePageKind.HOME
        private var itemId = ""
        private var loadJob: Job? = null
        private var started = false

        /** Called once per page; later calls (recomposition, rotation) are ignored. */
        fun start(
            sourceId: Long,
            kind: RemotePageKind,
            itemId: String,
            title: String = "",
        ) {
            shownSource.value = sourceId
            shownKey.value = libraryKeyFor(kind, itemId)
            shownTitle = title
            if (started) return
            started = true
            this.sourceId = sourceId
            this.kind = kind
            this.itemId = itemId
            reload()
        }

        fun reload() {
            if (kind == RemotePageKind.HOME) {
                // Home is the menu; it only talks to the server when the user searches.
                load(showSpinner = false) { _, _ -> RemotePageState() }
                return
            }
            load { source, connection ->
                when (kind) {
                    RemotePageKind.ARTISTS -> RemotePageState(artists = subsonic.artists(connection))
                    RemotePageKind.ARTIST -> RemotePageState(albums = subsonic.albumsOfArtist(connection, itemId))
                    RemotePageKind.ALBUMS -> {
                        val page = subsonic.albums(connection, 0, ALBUM_PAGE)
                        RemotePageState(albums = page, canLoadMore = page.size == ALBUM_PAGE)
                    }
                    RemotePageKind.ALBUM -> RemotePageState(tracks = songs(source, subsonic.albumTracks(connection, itemId)))
                    RemotePageKind.PLAYLISTS -> RemotePageState(playlists = subsonic.playlists(connection))
                    RemotePageKind.PLAYLIST -> RemotePageState(tracks = songs(source, subsonic.playlistTracks(connection, itemId)))
                    RemotePageKind.FAVORITES -> {
                        val starred = subsonic.starred(connection)
                        RemotePageState(artists = starred.artists, albums = starred.albums, tracks = songs(source, starred.tracks))
                    }
                    RemotePageKind.HOME -> RemotePageState()
                }
            }
        }

        /** Home page search; a blank query returns to the menu. Typing is debounced. */
        fun search(query: String) {
            if (kind != RemotePageKind.HOME) return
            val text = query.trim()
            if (text.isEmpty()) {
                reload()
                return
            }
            load(debounce = true) { source, connection ->
                val found = subsonic.search(connection, text)
                RemotePageState(artists = found.artists, albums = found.albums, tracks = songs(source, found.tracks))
            }
        }

        fun loadMoreAlbums() {
            val shown = _state.value
            if (kind != RemotePageKind.ALBUMS || !shown.canLoadMore || shown.loading || loadJob?.isActive == true) return
            loadJob =
                viewModelScope.launch {
                    try {
                        val connection = withContext(dispatchers.io) { connections.connection(sourceId) }
                        val page = subsonic.albums(connection, shown.albums.size, ALBUM_PAGE)
                        _state.update { it.copy(albums = (it.albums + page).distinctBy(SubsonicAlbum::id), canLoadMore = page.size == ALBUM_PAGE) }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (
                        @Suppress("TooGenericExceptionCaught") e: Exception,
                    ) {
                        _state.update { it.copy(canLoadMore = false, notice = e.remoteMessage()) }
                    }
                }
        }

        fun play(index: Int) {
            val tracks = _state.value.tracks
            if (index in tracks.indices) playback.playSongsFromQueue(tracks, index)
        }

        fun toggleFavorite(song: Song) {
            val favorite = !song.isFavorite
            viewModelScope.launch {
                try {
                    favorites.set(song, favorite)
                    _state.update { page -> page.copy(tracks = page.tracks.map { if (it.id == song.id) it.copy(isFavorite = favorite) else it }) }
                } catch (e: CancellationException) {
                    throw e
                } catch (
                    @Suppress("TooGenericExceptionCaught") e: Exception,
                ) {
                    _state.update { it.copy(notice = e.remoteMessage()) }
                }
            }
        }

        fun noticeShown() {
            _state.update { it.copy(notice = null) }
        }

        private suspend fun songs(
            source: RemoteSource,
            tracks: List<RemoteTrack>,
        ): List<Song> = repository.toSongs(source, tracks)

        private fun load(
            showSpinner: Boolean = true,
            debounce: Boolean = false,
            fetch: suspend (RemoteSource, SubsonicConnection) -> RemotePageState,
        ) {
            loadJob?.cancel()
            loadJob =
                viewModelScope.launch {
                    if (debounce) delay(SEARCH_DEBOUNCE_MS)
                    val name = _state.value.sourceName
                    if (showSpinner) _state.update { it.copy(loading = true, error = null) }
                    try {
                        val source =
                            repository.getSource(sourceId)
                                ?: throw RemoteAccessException(RemoteAccessException.Reason.SOURCE_MISSING, "")
                        val connection = withContext(dispatchers.io) { connections.connection(sourceId) }
                        _state.value = fetch(source, connection).copy(sourceName = source.name, loading = false)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (
                        @Suppress("TooGenericExceptionCaught") e: Exception,
                    ) {
                        _state.value = RemotePageState(sourceName = name, loading = false, error = e.remoteMessage())
                    }
                }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
            const val ALBUM_PAGE = 100
            const val SEARCH_DEBOUNCE_MS = 350L
        }
    }

/** What a server page's "Add to Library" row shows. */
data class LibraryRowState(
    val entry: Pair<com.erhu.erhu.core.database.entity.RemoteLibraryFolderEntity, FolderSyncStatus>? = null,
    val coveredByServer: Boolean = false,
    val available: Boolean = false,
)

/** The Library entry a server page adds: lists of everything add the whole server; a playlist list has none. */
internal fun libraryKeyFor(
    kind: RemotePageKind,
    itemId: String,
): String? =
    when (kind) {
        RemotePageKind.HOME, RemotePageKind.ARTISTS, RemotePageKind.ALBUMS -> DriveLibrarySync.WHOLE_SERVER
        RemotePageKind.ARTIST -> DriveLibrarySync.ARTIST_PREFIX + itemId
        RemotePageKind.ALBUM -> DriveLibrarySync.ALBUM_PREFIX + itemId
        RemotePageKind.PLAYLIST -> DriveLibrarySync.PLAYLIST_PREFIX + itemId
        RemotePageKind.FAVORITES -> DriveLibrarySync.FAVORITES
        RemotePageKind.PLAYLISTS -> null
    }
