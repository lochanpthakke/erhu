package com.erhu.erhu.feature.library.remote

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.erhu.erhu.core.common.dispatcher.DispatcherProvider
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
                scheduleBackgroundSync()
                sync(folder)
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
            scope.launch { sync(folder) }
        }

        /** Once per app run (the Library opening): picks up files added to or removed from Drive since. */
        fun syncAllOnce() {
            if (syncedThisRun) return
            syncedThisRun = true
            scope.launch {
                val folders = repository.getLibraryFolders()
                if (folders.isNotEmpty()) scheduleBackgroundSync()
                folders.forEach { sync(it) }
            }
        }

        /** Every folder in the Library, one after another; returns when all are done (the background worker). */
        suspend fun syncAll() = repository.getLibraryFolders().forEach { sync(it) }

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

        /** Files never read (or whose cached cover was cleared) are read one at a time; the Library refreshes as they arrive. */
        private suspend fun readTags(
            folder: RemoteLibraryFolderEntity,
            songs: List<Song>,
        ) {
            val unread =
                songs.filter { song ->
                    val fileId = song.remote?.remoteId ?: return@filter false
                    song.durationMs == 0L || tags.cachedCover(folder.sourceId, fileId) == null
                }
            var sinceRescan = 0
            unread.forEach { song ->
                val fileId = song.remote?.remoteId ?: return@forEach
                val read = tags.read(folder.sourceId, fileId) ?: return@forEach
                repository.applyTags(song, read.title, read.artist, read.album, read.durationMs)
                set(folder.id) { it.copy(tagged = it.tagged + 1) }
                if (++sinceRescan >= RESCAN_EVERY) {
                    sinceRescan = 0
                    library.requestRescanAfterChange()
                }
            }
            if (sinceRescan > 0) library.requestRescanAfterChange()
        }

        private fun set(
            folderId: Long,
            change: (FolderSyncStatus) -> FolderSyncStatus,
        ) = _status.update { it + (folderId to change(it[folderId] ?: FolderSyncStatus())) }

        internal companion object {
            const val WORK_NAME = "drive-library-sync"
            const val BACKGROUND_INTERVAL_HOURS = 6L
            const val MAX_DEPTH = 8
            const val MAX_FILES = 5_000
            const val RESCAN_EVERY = 40
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
        override suspend fun doWork(): Result {
            sync.syncAll()
            return Result.success()
        }
    }
