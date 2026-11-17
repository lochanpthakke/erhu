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
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.erhu.erhu.core.common.dispatcher.DispatcherProvider
import com.erhu.erhu.core.common.result.AppResult
import com.erhu.erhu.core.database.entity.RemoteDetails
import com.erhu.erhu.core.datastore.repository.ThemeRepository
import com.erhu.erhu.core.network.repository.GenreLookup
import com.erhu.erhu.core.network.repository.MetadataRepository
import com.erhu.erhu.feature.library.effectiveThreshold
import kotlinx.coroutines.delay
import com.erhu.erhu.core.database.entity.RemoteLibraryFolderEntity
import com.erhu.erhu.core.database.repository.LibraryRepository
import com.erhu.erhu.core.database.repository.RemoteLibraryRepository
import com.erhu.erhu.core.model.RemoteAccessException
import com.erhu.erhu.core.model.RemoteProvider
import com.erhu.erhu.core.model.Song
import com.erhu.erhu.core.model.remote
import com.erhu.erhu.core.network.remote.drive.DriveClient
import com.erhu.erhu.core.network.remote.drive.DriveItem
import com.erhu.erhu.core.network.remote.drive.DriveMetadataReader
import com.erhu.erhu.core.network.remote.drive.DriveOrder
import com.erhu.erhu.core.network.remote.drive.DrivePage
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.TimeUnit

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
    ) {
        private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)
        private val lock = Mutex()
        private val _status = MutableStateFlow<Map<Long, FolderSyncStatus>>(emptyMap())

        /** Per synced folder id. */
        val status: StateFlow<Map<Long, FolderSyncStatus>> = _status.asStateFlow()

        val folders = repository.observeLibraryFolders()

        private var syncedThisRun = false

        fun add(
            sourceId: Long,
            folderId: String,
            driveId: String?,
            name: String,
        ) {
            scope.launch {
                val folder = repository.addLibraryFolder(sourceId, folderId, driveId, name)
                set(folder.id) { FolderSyncStatus(running = true) }
                scheduleBackgroundSync()
                runInBackground()
            }
        }

        fun remove(folder: RemoteLibraryFolderEntity) {
            scope.launch {
                lock.withLock {
                    repository.removeLibraryFolder(folder.id)
                    _status.update { it - folder.id }
                }
                if (repository.getLibraryFolders().isEmpty()) WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
                library.requestRescanAfterChange()
            }
        }

        fun syncNow(folder: RemoteLibraryFolderEntity) {
            set(folder.id) { FolderSyncStatus(running = true) }
            runInBackground()
        }

        /** Once per app run (the Library opening): picks up files added to or removed from Drive since. */
        fun syncAllOnce() {
            if (syncedThisRun) return
            syncedThisRun = true
            scope.launch {
                if (repository.getLibraryFolders().isEmpty()) return@launch
                scheduleBackgroundSync()
                runInBackground()
            }
        }

        /** Every folder in the Library, one after another; returns when all are done (the background worker). */
        suspend fun syncAll() = repository.getLibraryFolders().forEach { sync(it) }

        /**
         * Runs a sync now as WorkManager work with its own notification, so listing and reading 200+
         * songs carries on when erhu is minimised or another app is open. A request made while one
         * runs is queued after it (a folder just added is synced too).
         */
        private fun runInBackground() {
            val request =
                OneTimeWorkRequestBuilder<DriveLibrarySyncWorker>()
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NOW_WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }

        /**
         * Syncs the Library's Drive folders in the background every few hours while there is a
         * connection and the battery is not low. Kept (not restarted) when already scheduled.
         */
        private fun scheduleBackgroundSync() {
            val request =
                PeriodicWorkRequestBuilder<DriveLibrarySyncWorker>(BACKGROUND_INTERVAL_HOURS, TimeUnit.HOURS)
                    .setConstraints(
                        Constraints
                            .Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .setRequiresBatteryNotLow(true)
                            .build(),
                    ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        @Suppress("TooGenericExceptionCaught")
        private suspend fun sync(folder: RemoteLibraryFolderEntity) =
            lock.withLock {
                val source = repository.getSource(folder.sourceId)?.takeIf { it.provider == RemoteProvider.GOOGLE_DRIVE } ?: return@withLock
                set(folder.id) { FolderSyncStatus(running = true) }
                try {
                    val found = list(folder)
                    set(folder.id) { it.copy(found = found.size) }
                    val songs = repository.toSongs(source, found.map { it.first.toTrack() })
                    repository.setLibraryFolderTracks(folder.id, songs.zip(found).associate { (song, item) -> song.id to item.second })
                    // Shown at once by file name; the tag pass below fills in titles, artists, albums and covers.
                    library.requestRescanAfterChange()
                    readTags(folder, songs)
                    set(folder.id) { it.copy(running = false) }
                } catch (e: CancellationException) {
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

        /**
         * One pass per track, in order, so a long folder gives the same result every time:
         * 1. the file's own tags (title, artist, album, length, genre, year, cover) from the start of
         *    the file, unless already read;
         * 2. once per track: a genre the file lacks from a confident Fix Tags match (MusicBrainz,
         *    Deezer, iTunes; at least the Fix Tags confidence setting), and plain lyrics from LRCLIB.
         * A track whose lookup failed (offline) is tried again next sync; one that simply has no genre
         * or lyrics online is not asked again. The Library refreshes as tracks are done.
         */
        @Suppress("CyclomaticComplexMethod")
        private suspend fun readTags(
            folder: RemoteLibraryFolderEntity,
            songs: List<Song>,
        ) {
            val stored = repository.getTracks(songs.map { it.id })
            var sinceRescan = 0
            songs.forEach { listed ->
                val fileId = listed.remote?.remoteId ?: return@forEach
                val row = stored[listed.id]
                val needsFile = listed.durationMs == 0L || tags.cachedCover(folder.sourceId, fileId) == null || (row?.detailsVersion ?: 0) < RemoteDetails.VERSION
                val needsDetails = (row?.detailsVersion ?: 0) < RemoteDetails.VERSION
                if (!needsFile && !needsDetails) return@forEach
                var song = listed
                var fileGenre = row?.genre
                var year = row?.year ?: 0
                if (needsFile) {
                    val read = tags.read(folder.sourceId, fileId)
                    if (read != null) {
                        song = repository.applyTags(listed, read.title, read.artist, read.album, read.durationMs)
                        fileGenre = read.genre ?: fileGenre
                        if (read.year > 0) year = read.year
                    }
                }
                if (needsDetails) lookUpDetails(song, fileGenre, year)
                set(folder.id) { it.copy(tagged = it.tagged + 1) }
                if (++sinceRescan >= RESCAN_EVERY) {
                    sinceRescan = 0
                    library.requestRescanAfterChange()
                }
            }
            if (sinceRescan > 0) library.requestRescanAfterChange()
        }

        /** Step 2 of [readTags]. Stored only when every lookup answered, so a network failure is retried. */
        private suspend fun lookUpDetails(
            song: Song,
            fileGenre: String?,
            fileYear: Int,
        ) {
            if (song.title.isBlank()) return
            val artist = song.artist.takeUnless { it.isBlank() || it.equals("Unknown Artist", true) }
            var answered = true
            var genre = fileGenre
            var year = fileYear
            if (genre == null || year == 0) {
                val accept = effectiveThreshold(theme.metadataConfidenceThreshold.value)
                when (val found = metadata.searchAndScoreTrack(song.title, artist, song.album.takeIf { it.isNotBlank() }, song.durationMs.takeIf { it > 0 })) {
                    is AppResult.Success -> {
                        val match = found.data.maxByOrNull { it.confidenceScore }?.takeIf { it.confidenceScore >= accept }
                        if (match != null) {
                            if (year == 0) year = match.year.takeIf { it in YEARS } ?: 0
                            if (genre == null) {
                                when (val lookup = metadata.lookupGenre(match, found.data)) {
                                    is GenreLookup.Found -> genre = lookup.genre
                                    is GenreLookup.NotFound -> Unit
                                    is GenreLookup.Failed -> answered = false
                                }
                            }
                        }
                    }
                    is AppResult.Error -> answered = false
                }
                // The metadata services ask clients for about one request a second.
                delay(ONLINE_GAP_MS)
            }
            val lyrics =
                if (artist == null) {
                    null
                } else {
                    when (val found = metadata.fetchLyrics(song.title, artist, song.album.takeIf { it.isNotBlank() }, (song.durationMs / 1000).toInt().takeIf { it > 0 })) {
                        is AppResult.Success -> found.data?.let { it.plainLyrics?.takeIf(String::isNotBlank) ?: it.syncedLyrics?.let(::withoutTimestamps) }
                        is AppResult.Error -> null.also { answered = false }
                    }
                }
            if (answered) repository.applyDetails(song.id, genre, year, lyrics)
        }

        private fun set(
            folderId: Long,
            change: (FolderSyncStatus) -> FolderSyncStatus,
        ) = _status.update { it + (folderId to change(it[folderId] ?: FolderSyncStatus())) }

        internal companion object {
            const val WORK_NAME = "drive-library-sync"
            const val NOW_WORK_NAME = "drive-library-sync-now"
            const val BACKGROUND_INTERVAL_HOURS = 6L
            const val MAX_DEPTH = 8
            const val MAX_FILES = 5_000
            const val RESCAN_EVERY = 40
            const val ONLINE_GAP_MS = 1_100L
            val YEARS = 1900..2099
        }
    }

/** The periodic background sync of the Library's Google Drive folders (see [DriveLibrarySync]). */
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
                // the background. A periodic run the system will not promote simply runs without it.
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
                    sync.syncAll()
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
                    .setContentTitle("Syncing Google Drive music")
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
