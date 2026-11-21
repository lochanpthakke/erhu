package com.erhu.erhu.feature.library.remote

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.erhu.erhu.core.common.dispatcher.DispatcherProvider
import com.erhu.erhu.core.common.result.AppResult
import com.erhu.erhu.core.database.entity.RemoteDetails
import com.erhu.erhu.core.database.entity.RemoteLibraryFolderEntity
import com.erhu.erhu.core.database.entity.RemoteTrackEntity
import com.erhu.erhu.core.database.repository.LibraryRepository
import com.erhu.erhu.core.database.repository.RemoteLibraryRepository
import com.erhu.erhu.core.datastore.repository.ThemeRepository
import com.erhu.erhu.core.model.RemoteAccessException
import com.erhu.erhu.core.model.RemoteProvider
import com.erhu.erhu.core.model.RemoteTrack
import com.erhu.erhu.core.model.Song
import com.erhu.erhu.core.model.remote
import com.erhu.erhu.core.network.remote.drive.DriveClient
import com.erhu.erhu.core.network.remote.drive.DriveItem
import com.erhu.erhu.core.network.remote.drive.DriveMetadataReader
import com.erhu.erhu.core.network.remote.drive.DriveOrder
import com.erhu.erhu.core.network.remote.drive.DrivePage
import com.erhu.erhu.core.network.remote.drive.DriveTags
import com.erhu.erhu.core.network.repository.GenreLookup
import com.erhu.erhu.core.network.repository.MetadataRepository
import com.erhu.erhu.feature.library.effectiveThreshold
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** What a synced folder is doing now; [message] says why the last sync stopped, if it failed. */
data class FolderSyncStatus(
    val running: Boolean = false,
    val found: Int = 0,
    val tagged: Int = 0,
    val message: String? = null,
)

/**
 * Keeps Google Drive folders added to the Library in step with Drive: lists each folder with its
 * subfolders, records which tracks it holds, reads the files' own tags and covers (Drive itself
 * knows only file names), and asks for a library scan, which shows them with the device's music.
 * One folder syncs at a time; Drive is only read, never changed.
 */
@Singleton
class DriveLibrarySync
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val repository: RemoteLibraryRepository,
        private val library: LibraryRepository,
        private val auth: GoogleDriveAuth,
        private val drive: DriveClient,
        private val tags: DriveMetadataReader,
        private val dispatchers: DispatcherProvider,
        private val metadata: MetadataRepository,
        private val theme: ThemeRepository,
        private val subsonic: com.erhu.erhu.core.network.remote.subsonic.SubsonicClient,
        private val subsonicConnections: com.erhu.erhu.core.network.remote.subsonic.SubsonicConnections,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)
        private val lock = Mutex()
        private val _status = MutableStateFlow<Map<Long, FolderSyncStatus>>(emptyMap())

        /** Per synced folder id. */
        val status: StateFlow<Map<Long, FolderSyncStatus>> = _status.asStateFlow()

        val folders = repository.observeLibraryFolders()

        private var periodicCancelled = false

        fun add(
            sourceId: Long,
            folderId: String,
            driveId: String?,
            name: String,
        ) {
            scope.launch {
                val folder = repository.addLibraryFolder(sourceId, folderId, driveId, name)
                set(folder.id) { FolderSyncStatus(running = true) }
                runInBackground(folderId = folder.id)
            }
        }

        fun remove(folder: RemoteLibraryFolderEntity) {
            scope.launch {
                lock.withLock {
                    repository.removeLibraryFolder(folder.id)
                    _status.update { it - folder.id }
                }
                if (repository.getLibraryFolders().isNotEmpty()) {
                    // Tracks the removed entry shared with another (an artist inside the whole server) come back.
                    runInBackground()
                }
                library.requestRescanAfterChange()
            }
        }

        fun syncNow(folder: RemoteLibraryFolderEntity) {
            if (_status.value[folder.id]?.running == true) return
            set(folder.id) { FolderSyncStatus(running = true) }
            runInBackground(folderId = folder.id)
        }

        /** Manually refreshes every Library folder belonging to [sourceId]. */
        fun syncSource(sourceId: Long) {
            scope.launch {
                val targets = repository.getLibraryFolders().filter { it.sourceId == sourceId }
                if (targets.isEmpty()) return@launch
                var anyStarted = false
                targets.forEach { folder ->
                    if (_status.value[folder.id]?.running != true) {
                        set(folder.id) { FolderSyncStatus(running = true) }
                        anyStarted = true
                    }
                }
                if (anyStarted) runInBackground(sourceId = sourceId)
            }
        }

        /** Drops tracks of a disconnected remote source from the local library index. */
        fun onSourceRemoved() {
            scope.launch {
                library.requestRescanAfterChange()
            }
        }

        /** Cancels any legacy 6-hour periodic remote sync job left behind by older builds. */
        fun cancelBackgroundSync() {
            if (periodicCancelled) return
            periodicCancelled = true
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }

        /** Every folder in the Library, one after another; returns when all are done (the background worker). */
        suspend fun syncAll() = repository.getLibraryFolders().forEach { sync(it) }

        /** One folder in the Library (for manual refresh or initial add). */
        suspend fun syncFolder(folderId: Long) {
            repository.getLibraryFolders().firstOrNull { it.id == folderId }?.let { sync(it) }
        }

        /** Every Library folder belonging to [sourceId]. */
        suspend fun syncSourceFolders(sourceId: Long) {
            repository.getLibraryFolders().filter { it.sourceId == sourceId }.forEach { sync(it) }
        }

        /**
         * Runs a sync now as WorkManager work with its own notification, so listing and reading 200+
         * songs carries on when erhu is minimised or another app is open. A request made while one
         * runs is queued after it (a folder just added is synced too).
         */
        private fun runInBackground(
            folderId: Long? = null,
            sourceId: Long? = null,
        ) {
            val builder =
                OneTimeWorkRequestBuilder<DriveLibrarySyncWorker>()
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            if (folderId != null || sourceId != null) {
                val data =
                    Data
                        .Builder()
                        .apply {
                            if (folderId != null) putLong(KEY_FOLDER_ID, folderId)
                            if (sourceId != null) putLong(KEY_SOURCE_ID, sourceId)
                        }.build()
                builder.setInputData(data)
            }
            WorkManager.getInstance(context).enqueueUniqueWork(NOW_WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, builder.build())
        }

        @Suppress("TooGenericExceptionCaught")
        private suspend fun sync(folder: RemoteLibraryFolderEntity) =
            lock.withLock {
                val source = repository.getSource(folder.sourceId)
                if (source == null) {
                    set(folder.id) { it.copy(running = false) }
                    return@withLock
                }
                set(folder.id) { FolderSyncStatus(running = true) }
                try {
                    val found =
                        if (source.provider ==
                            RemoteProvider.SUBSONIC
                        ) {
                            listServer(folder)
                        } else {
                            list(folder).map { (item, parent) -> item.toTrack() to parent }
                        }
                    set(folder.id) { it.copy(found = found.size) }
                    val synced = repository.syncSongs(source, found.map { it.first })
                    val membershipChanged =
                        repository.setLibraryFolderTracks(
                            folder.id,
                            synced.songs.zip(found).associate { (song, item) -> song.id to item.second },
                        )
                    val listingChanged = synced.changed || membershipChanged
                    readTags(
                        folder = folder,
                        songs = synced.songs,
                        readsFiles = source.provider == RemoteProvider.GOOGLE_DRIVE,
                        listingChanged = listingChanged,
                    )
                    set(folder.id) { it.copy(running = false) }
                } catch (e: CancellationException) {
                    set(folder.id) { it.copy(running = false) }
                    throw e
                } catch (e: Exception) {
                    set(folder.id) { it.copy(running = false, message = e.remoteMessage()) }
                }
            }

        /** Every playable file under [folder] with the id of the folder directly holding it. */
        private suspend fun list(folder: RemoteLibraryFolderEntity): List<Pair<DriveItem, String>> {
            val files = mutableListOf<Pair<DriveItem, String>>()
            val seen = mutableSetOf(folder.folderId)
            val pending = ArrayDeque(listOf(folder.folderId to 0))
            while (pending.isNotEmpty() && files.size < MAX_FILES) {
                val (parent, depth) = pending.removeFirst()
                var pageToken: String? = null
                do {
                    val page = page(folder, parent, pageToken)
                    page.items.forEach { item ->
                        when {
                            !item.isFolder -> files += item to parent
                            depth < MAX_DEPTH && seen.add(item.id) -> pending += item.id to depth + 1
                        }
                    }
                    pageToken = page.nextPageToken
                } while (pageToken != null)
            }
            return files.distinctBy { it.first.id }
        }

        /**
         * Every song of a Subsonic/Navidrome server (the whole library is its one "folder"), album by
         * album, each paired with its album id: albums then group as on the server.
         */
        private suspend fun listServer(folder: RemoteLibraryFolderEntity): List<Pair<RemoteTrack, String>> {
            val connection = withContext(dispatchers.io) { subsonicConnections.connection(folder.sourceId) }
            val tracks = mutableListOf<Pair<RemoteTrack, String>>()
            val key = folder.folderId
            when {
                key.startsWith(ARTIST_PREFIX) ->
                    subsonic.albumsOfArtist(connection, key.removePrefix(ARTIST_PREFIX)).forEach { album ->
                        subsonic.albumTracks(connection, album.id).forEach { tracks += it to album.id }
                    }
                key.startsWith(ALBUM_PREFIX) -> {
                    val albumId = key.removePrefix(ALBUM_PREFIX)
                    subsonic.albumTracks(connection, albumId).forEach { tracks += it to albumId }
                }
                // A playlist or the favourites mix albums: each track is grouped by its own album name.
                key.startsWith(PLAYLIST_PREFIX) ->
                    subsonic.playlistTracks(connection, key.removePrefix(PLAYLIST_PREFIX)).forEach {
                        tracks +=
                            it to "album-name:${it.album}"
                    }
                key == FAVORITES -> subsonic.starred(connection).tracks.forEach { tracks += it to "album-name:${it.album}" }
                else -> {
                    var offset = 0
                    while (tracks.size < MAX_FILES) {
                        val albums = subsonic.albums(connection, offset, SERVER_PAGE)
                        if (albums.isEmpty()) break
                        albums.forEach { album -> subsonic.albumTracks(connection, album.id).forEach { tracks += it to album.id } }
                        offset += albums.size
                    }
                }
            }
            set(folder.id) { it.copy(found = tracks.size) }
            return tracks.distinctBy { it.first.remoteId }
        }

        /** A token Google rejects is dropped and asked for once more. */
        private suspend fun page(
            folder: RemoteLibraryFolderEntity,
            parent: String,
            pageToken: String?,
        ): DrivePage {
            suspend fun attempt(): DrivePage {
                val token = withContext(dispatchers.io) { auth.token(folder.sourceId) }
                return drive.children(token, parent, folder.driveId, DriveOrder.NAME, pageToken)
            }
            return try {
                attempt()
            } catch (e: RemoteAccessException) {
                if (e.reason != RemoteAccessException.Reason.AUTH_REQUIRED) throw e
                auth.invalidate(folder.sourceId)
                attempt()
            }
        }

        private class PendingRemoteRead(
            val song: Song,
            val fileId: String,
            val needsFile: Boolean,
            val needsDetails: Boolean,
        )

        /**
         * Two passes, so covers and names show together without flooding the local scanner:
         * 1. the file's own tags (title, artist, album, length, genre, year, cover) from the start of
         *    each file, unless already read; a few files at a time (see [DriveMetadataReader]);
         * 2. then, once per track, in order: a genre the file lacks from a confident Fix Tags match
         *    (MusicBrainz, Deezer, iTunes; at least the Fix Tags confidence setting), and plain lyrics
         *    from LRCLIB. These are rate-limited (about one request a second).
         * A track whose lookup failed (offline) is tried again next manual sync without re-reading its
         * audio file; one that simply has no genre or lyrics online is not asked again.
         */
        private suspend fun readTags(
            folder: RemoteLibraryFolderEntity,
            songs: List<Song>,
            readsFiles: Boolean,
            listingChanged: Boolean,
        ) {
            val stored = repository.getTracks(songs.map { it.id })
            val pending = collectPendingReads(folder, songs, stored, readsFiles)
            set(folder.id) { it.copy(tagged = songs.size - pending.size) }
            if (pending.isEmpty()) {
                if (listingChanged) library.requestRescanAfterChange()
                return
            }
            val fileGenres = HashMap<Long, String?>(pending.size)
            val years = HashMap<Long, Int>(pending.size)
            val tagged = HashMap<Long, Song>(pending.size)
            pending.forEach { item ->
                fileGenres[item.song.id] = stored[item.song.id]?.genre
                years[item.song.id] = stored[item.song.id]?.year ?: 0
            }
            var pass1NeedsRescan = listingChanged
            var pass2NeedsRescan = false
            try {
                runFileTagPass(folder, pending, fileGenres, years, tagged) { pass1NeedsRescan = true }
                if (pass1NeedsRescan) {
                    library.requestRescanAfterChange()
                    pass1NeedsRescan = false
                }
                runOnlineDetailsPass(folder, pending, tagged, fileGenres, years) { pass2NeedsRescan = true }
                if (pass2NeedsRescan) {
                    library.requestRescanAfterChange()
                    pass2NeedsRescan = false
                }
            } finally {
                if (pass1NeedsRescan || pass2NeedsRescan) {
                    withContext(NonCancellable) { library.requestRescanAfterChange() }
                }
            }
        }

        private fun collectPendingReads(
            folder: RemoteLibraryFolderEntity,
            songs: List<Song>,
            stored: Map<Long, RemoteTrackEntity>,
            readsFiles: Boolean,
        ): List<PendingRemoteRead> =
            songs.mapNotNull { listed ->
                val fileId = listed.remote?.remoteId ?: return@mapNotNull null
                val row = stored[listed.id]
                val hasCover = readsFiles && tags.hasCachedCover(folder.sourceId, fileId)
                val (needsFile, needsDetails) =
                    planPendingRemoteRead(
                        readsFiles = readsFiles,
                        durationMs = listed.durationMs,
                        hasCachedCover = hasCover,
                        detailsVersion = row?.detailsVersion ?: 0,
                    )
                PendingRemoteRead(listed, fileId, needsFile, needsDetails).takeIf { needsFile || needsDetails }
            }

        private suspend fun runFileTagPass(
            folder: RemoteLibraryFolderEntity,
            pending: List<PendingRemoteRead>,
            fileGenres: MutableMap<Long, String?>,
            years: MutableMap<Long, Int>,
            tagged: MutableMap<Long, Song>,
            onChanged: () -> Unit,
        ) {
            // Pass 1: files. Chunks keep the order of results (and of the stored tags) stable.
            for (chunk in pending.filter { it.needsFile }.chunked(PARALLEL_FILE_READS)) {
                val reads = coroutineScope { chunk.map { item -> async { item to tags.read(folder.sourceId, item.fileId) } }.awaitAll() }
                for ((item, read) in reads) {
                    applyFileRead(folder, item, read, fileGenres, years, tagged, onChanged)
                }
            }
        }

        private suspend fun applyFileRead(
            folder: RemoteLibraryFolderEntity,
            item: PendingRemoteRead,
            read: DriveTags?,
            fileGenres: MutableMap<Long, String?>,
            years: MutableMap<Long, Int>,
            tagged: MutableMap<Long, Song>,
            onChanged: () -> Unit,
        ) {
            if (read != null) {
                tagged[item.song.id] =
                    repository.applyTags(
                        song = item.song,
                        title = read.title,
                        artist = read.artist,
                        album = read.album,
                        durationMs = read.durationMs,
                        genre = read.genre,
                        year = read.year,
                    )
                read.genre?.let { fileGenres[item.song.id] = it }
                if (read.year > 0) years[item.song.id] = read.year
                onChanged()
            }
            if (!item.needsDetails) set(folder.id) { it.copy(tagged = it.tagged + 1) }
        }

        private suspend fun runOnlineDetailsPass(
            folder: RemoteLibraryFolderEntity,
            pending: List<PendingRemoteRead>,
            tagged: Map<Long, Song>,
            fileGenres: Map<Long, String?>,
            years: Map<Long, Int>,
            onChanged: () -> Unit,
        ) {
            // Pass 2: online details, rate-limited.
            pending.filter { it.needsDetails }.forEach { item ->
                val changed = lookUpDetails(tagged[item.song.id] ?: item.song, fileGenres[item.song.id], years[item.song.id] ?: 0)
                if (changed) onChanged()
                set(folder.id) { it.copy(tagged = it.tagged + 1) }
            }
        }

        private data class MetadataLookup(
            val genre: String?,
            val year: Int,
            val answered: Boolean,
        )

        private data class LyricsLookup(
            val lyrics: String?,
            val answered: Boolean,
        )

        /** Step 2 of [readTags]. Stored only when every lookup answered, so a network failure is retried. */
        private suspend fun lookUpDetails(
            song: Song,
            fileGenre: String?,
            fileYear: Int,
        ): Boolean {
            if (song.title.isBlank()) {
                repository.applyDetails(song.id, fileGenre, fileYear, null)
                return false
            }
            val artist = song.artist.takeUnless { it.isBlank() || it.equals("Unknown Artist", true) }
            val metadataResult = lookUpGenreAndYear(song, artist, fileGenre, fileYear)
            val lyricsResult = fetchPlainLyrics(song, artist)
            val saved = metadataResult.answered && lyricsResult.answered
            if (saved) {
                repository.applyDetails(song.id, metadataResult.genre, metadataResult.year, lyricsResult.lyrics)
            }
            return saved &&
                (
                    (metadataResult.genre != null && metadataResult.genre != fileGenre) ||
                        (metadataResult.year > 0 && metadataResult.year != fileYear) ||
                        !lyricsResult.lyrics.isNullOrBlank()
                )
        }

        private suspend fun lookUpGenreAndYear(
            song: Song,
            artist: String?,
            fileGenre: String?,
            fileYear: Int,
        ): MetadataLookup {
            if (fileGenre != null && fileYear > 0) return MetadataLookup(fileGenre, fileYear, answered = true)
            var answered = true
            var genre = fileGenre
            var year = fileYear
            val accept = effectiveThreshold(theme.metadataConfidenceThreshold.value)
            val found =
                metadata.searchAndScoreTrack(
                    song.title,
                    artist,
                    song.album.takeIf { it.isNotBlank() },
                    song.durationMs.takeIf { it > 0 },
                )
            val candidates = (found as? AppResult.Success)?.data
            val match = candidates?.maxByOrNull { it.confidenceScore }?.takeIf { it.confidenceScore >= accept }
            if (found is AppResult.Error) answered = false
            if (match != null && year == 0) year = match.year.takeIf { it in YEARS } ?: 0
            if (candidates != null && match != null && genre == null) {
                val lookup = metadata.lookupGenre(match, candidates)
                if (lookup is GenreLookup.Found) genre = lookup.genre
                if (lookup is GenreLookup.Failed) answered = false
            }
            // The metadata services ask clients for about one request a second.
            delay(ONLINE_GAP_MS)
            return MetadataLookup(genre, year, answered)
        }

        private suspend fun fetchPlainLyrics(
            song: Song,
            artist: String?,
        ): LyricsLookup {
            if (artist == null) return LyricsLookup(lyrics = null, answered = true)
            return when (
                val found =
                    metadata.fetchLyrics(
                        song.title,
                        artist,
                        song.album.takeIf { it.isNotBlank() },
                        (song.durationMs / MS_PER_SECOND).toInt().takeIf { it > 0 },
                    )
            ) {
                is AppResult.Success -> {
                    val text =
                        found.data?.let {
                            it.plainLyrics?.takeIf(String::isNotBlank)
                                ?: it.syncedLyrics?.let(::withoutTimestamps)
                        }
                    LyricsLookup(lyrics = text, answered = true)
                }
                is AppResult.Error -> LyricsLookup(lyrics = null, answered = false)
            }
        }

        private fun set(
            folderId: Long,
            change: (FolderSyncStatus) -> FolderSyncStatus,
        ) = _status.update { it + (folderId to change(it[folderId] ?: FolderSyncStatus())) }

        internal companion object {
            const val WORK_NAME = "drive-library-sync"
            const val NOW_WORK_NAME = "drive-library-sync-now"
            const val KEY_FOLDER_ID = "folder_id"
            const val KEY_SOURCE_ID = "source_id"
            const val MAX_DEPTH = 8
            const val MAX_FILES = 5_000

            /** Files whose tags and cover are read at once (the reader itself allows this many). */
            const val PARALLEL_FILE_READS = 3
            const val SERVER_PAGE = 100

            /** Library entry ids of a Subsonic server: the whole library, or one artist, album, playlist, the favourites. */
            const val WHOLE_SERVER = "@library"
            const val FAVORITES = "@favorites"
            const val ARTIST_PREFIX = "artist:"
            const val ALBUM_PREFIX = "album:"
            const val PLAYLIST_PREFIX = "playlist:"
            const val ONLINE_GAP_MS = 1_100L
            const val MS_PER_SECOND = 1_000L
            val YEARS = 1900..2099
        }
    }

/**
 * Decides which remote sync passes a track needs. Online details never re-trigger an audio file read,
 * and Subsonic tracks (`readsFiles = false`) never run file reads.
 */
internal fun planPendingRemoteRead(
    readsFiles: Boolean,
    durationMs: Long,
    hasCachedCover: Boolean,
    detailsVersion: Int,
): Pair<Boolean, Boolean> {
    val needsDetails = detailsVersion < RemoteDetails.VERSION
    val needsFile = readsFiles && (durationMs == 0L || !hasCachedCover)
    return needsFile to needsDetails
}

/** Manual background sync of the Library's remote folders (see [DriveLibrarySync]). */
@HiltWorker
class DriveLibrarySyncWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted parameters: WorkerParameters,
        private val sync: DriveLibrarySync,
    ) : CoroutineWorker(context, parameters) {
        // A folder that fails (offline, Google asks to sign in again) records why; the next run tries again.
        override suspend fun doWork(): Result =
            coroutineScope {
                // Foreground with a progress notification, so the system keeps it running while erhu is in
                // the background.
                runCatching { setForeground(getForegroundInfo()) }
                val progress =
                    launch {
                        sync.status.collect { status ->
                            val running = status.values.filter { it.running }
                            if (running.isNotEmpty()) {
                                runCatching { setForeground(foregroundInfo(running.sumOf { it.found }, running.sumOf { it.tagged })) }
                            }
                        }
                    }
                try {
                    val folderId = inputData.getLong(DriveLibrarySync.KEY_FOLDER_ID, -1L)
                    val sourceId = inputData.getLong(DriveLibrarySync.KEY_SOURCE_ID, -1L)
                    when {
                        folderId > 0L -> sync.syncFolder(folderId)
                        sourceId > 0L -> sync.syncSourceFolders(sourceId)
                        else -> sync.syncAll()
                    }
                } finally {
                    progress.cancel()
                }
                Result.success()
            }

        override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(0, 0)

        private fun foregroundInfo(
            found: Int,
            read: Int,
        ): ForegroundInfo {
            val manager = applicationContext.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Google Drive sync", NotificationManager.IMPORTANCE_LOW))
            val notification =
                NotificationCompat
                    .Builder(applicationContext, CHANNEL)
                    .setSmallIcon(android.R.drawable.stat_notify_sync)
                    .setContentTitle("Syncing remote music")
                    .setContentText(if (found == 0) "Looking for songs..." else "$read of $found songs read")
                    .setProgress(found, read, found == 0)
                    .setOngoing(true)
                    .setSilent(true)
                    .build()
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                ForegroundInfo(NOTIFICATION_ID, notification)
            }
        }

        private companion object {
            const val CHANNEL = "drive_library_sync"
            const val NOTIFICATION_ID = 7305
        }
    }

/** Plain text from synced (LRC) lyrics: the [mm:ss.xx] timestamps and tag lines removed. */
internal fun withoutTimestamps(lrc: String): String? =
    lrc
        .lines()
        .map { it.replace(Regex("""\[[^\]]*]"""), "").trim() }
        .filter { it.isNotEmpty() }
        .joinToString("\n")
        .takeIf { it.isNotBlank() }
