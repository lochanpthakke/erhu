/*
 * Copyright (C) 2026 Kshitij H Kekunnaya
 *
 * This file is part of erhu.
 * Licensed under the GNU General Public License v3.0.
 * See the LICENSE file in the project root.
 */

package com.erhu.erhu.feature.library

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import com.erhu.erhu.core.common.logging.Logger
import com.erhu.erhu.core.common.result.AppResult
import com.erhu.erhu.core.database.dao.LearnedWatermarkDao
import com.erhu.erhu.core.database.repository.LibraryRepository
import com.erhu.erhu.core.datastore.RepairBatchPersistence
import com.erhu.erhu.core.model.PersistedRepairBatch
import com.erhu.erhu.core.model.PersistedRepairItem
import com.erhu.erhu.core.model.ProviderOutcome
import com.erhu.erhu.core.model.ProviderStatus
import com.erhu.erhu.core.model.Song
import com.erhu.erhu.core.network.api.AcousticBrainzApi
import com.erhu.erhu.core.network.bpm.BpmFinder
import com.erhu.erhu.core.network.bpm.PcmSource
import com.erhu.erhu.core.network.cleaner.FilenameCleaner
import com.erhu.erhu.core.network.cleaner.TitleCandidateGenerator
import com.erhu.erhu.core.network.connectivity.NetworkStatus
import com.erhu.erhu.core.network.connectivity.NetworkStatusSource
import com.erhu.erhu.core.network.repository.DetailedSearchResponse
import com.erhu.erhu.core.network.repository.MetadataRepository
import com.erhu.erhu.core.network.repository.TrackMatcher
import com.erhu.erhu.core.network.scorer.MetadataScorer
import com.erhu.erhu.core.network.tagger.FieldFixPlan
import com.erhu.erhu.core.network.tagger.FieldSelection
import com.erhu.erhu.core.network.tagger.FixField
import com.erhu.erhu.core.network.tagger.TagWriteRequest
import com.erhu.erhu.core.network.tagger.EmbeddedTagReader
import com.erhu.erhu.core.network.tagger.FileRenamer
import com.erhu.erhu.core.network.tagger.WriteAuthorization
import com.erhu.erhu.feature.library.service.MetadataRepairService
import java.io.File
import java.lang.reflect.Proxy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MetadataBackgroundTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val scopes = mutableListOf<CoroutineScope>()
    private val songs = listOf(
        Song(1, "First", "Artist", "Album", 1000, "content://media/1"),
        Song(2, "Second", "Artist", "Album", 1000, "content://media/2"),
    )
    private var saved: PersistedRepairBatch? = null
    private var writeCount = 0
    private var searches = 0
    private var consentChecks = 0
    private var authorized = false

    @Before fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        File(context.noBackupFilesDir, "metadata-repair").deleteRecursively()
    }

    @After fun cleanup() {
        scopes.forEach { it.cancel() }
        Dispatchers.resetMain()
    }

    private fun <T> proxy(type: Class<T>, call: (String, Array<out Any?>) -> Any?): T = type.cast(
        Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, args ->
            if (method.name == "toString") "Metadata test fixture" else call(method.name, args ?: emptyArray())
        },
    )

    private val logger get() = proxy(Logger::class.java) { _, _ -> null }
    private fun metadata() = proxy(MetadataRepository::class.java) { name, _ -> when (name) {
        "searchAndScoreTrackDetailed" -> {
            searches++
            AppResult.Success(DetailedSearchResponse(emptyList(), listOf(ProviderOutcome("iTunes", status = ProviderStatus.NO_RESULTS))))
        }
        "checkTagWriteAccess" -> {
            consentChecks++
            if (authorized) WriteAuthorization.Granted else WriteAuthorization.NeedsPermission("android.permission.WRITE_EXTERNAL_STORAGE")
        }
        "writeTagsToFile" -> { writeCount++; AppResult.Success(Unit) }
        else -> error("Unexpected metadata operation: $name")
    } }

    private fun operation(): Pair<MetadataRepairOperation, MetadataWriteAccess> {
        val repository = metadata()
        val access = MetadataWriteAccess(repository)
        val library = proxy(LibraryRepository::class.java) { name, _ -> when (name) {
            "getAllSongs" -> flowOf(songs)
            "getSongFilePath" -> File(context.cacheDir, "test-metadata.mp3").path
            "requestRescanAfterChange" -> Unit
            else -> error("Unexpected library operation: $name")
        } }
        val persistence = proxy(RepairBatchPersistence::class.java) { name, args -> when (name) {
            "load" -> saved
            "save" -> { saved = args[0] as PersistedRepairBatch; AppResult.Success(Unit) }
            else -> error(name)
        } }
        val network = object : NetworkStatusSource {
            override val status = MutableStateFlow(NetworkStatus.AVAILABLE)
            override suspend fun resolves(hosts: Collection<String>) = hosts.associateWith { true }
        }
        val watermarks = proxy(LearnedWatermarkDao::class.java) { _, _ -> emptyList<String>() }
        val bpm = BpmFinder(proxy(AcousticBrainzApi::class.java) { _, _ -> error("No extra providers expected") },
            PcmSource { error("No audio analysis expected") }, logger)
        val operation = MetadataRepairOperation(context, library, repository, watermarks, FileRenamer(context),
            TitleCandidateGenerator(FilenameCleaner()), EmbeddedTagReader(logger), TrackMatcher(MetadataScorer()),
            network, persistence, bpm, access, logger)
        scopes += ReflectionHelpers.getField<CoroutineScope>(operation, "scope")
        scopes += ReflectionHelpers.getField<CoroutineScope>(access, "scope")
        return operation to access
    }

    private fun seedPending() {
        saved = PersistedRepairBatch(listOf(PersistedRepairItem(1, RepairStatus.READY, null)), null)
        PendingRepairStore(context).save(PendingRepairs(writes = mapOf(1L to TagWriteRequest(songs[0].uri, "/test.mp3", title = "Correct title"))))
    }

    @Test fun durablePlanRoundTripsArtworkClearFlagsAndReviewDetails() {
        val request = TagWriteRequest("content://media/1", "/test.flac", title = "Title", releaseDate = "2020-01", bpm = 120,
            artworkBytes = byteArrayOf(1, 2, 3, 4), clearGenre = true, clearAlbum = true)
        val plan = FieldFixPlan(request, listOf(FieldFixPlan.Change(FixField.BPM, "90", "120")), listOf("note"), listOf("unfilled"))
        val store = PendingRepairStore(context)
        store.save(PendingRepairs(mapOf(1L to request), mapOf(1L to "Title.flac"), mapOf(1L to plan)))
        val restored = PendingRepairStore(context).load()
        assertEquals(request, restored.writes[1])
        assertEquals(plan, restored.plans[1])
        assertEquals("Title.flac", restored.renames[1])
        store.save(PendingRepairs())
        assertTrue(store.load().ids.isEmpty())
        assertFalse(File(context.noBackupFilesDir, "metadata-repair").listFiles()!!.any { it.name.endsWith(".cover") })
    }

    @Test fun damagedCheckpointIsReportedRatherThanDiscarded() {
        val store = PendingRepairStore(context)
        store.save(PendingRepairs())
        val manifest = File(context.noBackupFilesDir, "metadata-repair/pending.json")
        manifest.writeText("broken")
        assertThrows(Exception::class.java) { store.load() }
        assertEquals("broken", manifest.readText())
    }

    @Test fun oneRunnerProcessesWithoutAnyViewModelOrUiSubscriberAndRejectsDuplicateStart() = runTest {
        val (op, _) = operation()
        op.ready()
        op.queue.value = songs.map(::RepairQueueItem)
        assertTrue(op.prepare("ITUNES", FieldSelection(setOf(FixField.TITLE), false, false), 0.85f))
        assertFalse(op.prepare("ITUNES", FieldSelection(setOf(FixField.TITLE), false, false), 0.85f))
        val updates = mutableListOf<Int>()
        val observer = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            op.progress.collect { if (it.phase == RepairPhase.FETCHING) updates += it.processed }
        }
        op.execute()
        assertEquals(RepairPhase.COMPLETE, op.progress.value.phase)
        assertEquals(2, op.progress.value.processed)
        assertEquals(2, op.progress.value.total)
        assertTrue(updates.containsAll(listOf(0, 1, 2)))
        assertFalse(op.busy.value)
        assertTrue(searches > 0)
        assertEquals(0, writeCount)
        observer.cancel()
    }

    @Test fun restoredResultsWaitForExplicitSystemConsentAndApplyOnlyOnce() = runTest {
        seedPending()
        val (op, access) = operation()
        op.ready()
        assertTrue(op.awaitingApproval.value)
        assertEquals(0, writeCount)
        op.approvePending()
        op.approvePending()
        access.request.first { it != null }
        assertEquals(1, consentChecks)
        assertTrue(access.claimLaunch())
        assertFalse("Activity recreation must not launch a duplicate dialog", access.claimLaunch())
        assertEquals(0, writeCount)
        authorized = true
        access.onResult(true)
        op.awaitingApproval.first { !it }
        assertEquals(1, writeCount)
        assertEquals(2, consentChecks)
        assertTrue(PendingRepairStore(context).load().ids.isEmpty())
        assertEquals(RepairStatus.DONE, op.queue.value.single().status)
    }

    @Test fun rejectedPermissionNeverWritesAndResolvesPendingDecision() = runTest {
        seedPending()
        val (op, access) = operation()
        op.ready()
        op.approvePending()
        access.request.first { it != null }
        access.onResult(false)
        op.awaitingApproval.first { !it }
        assertEquals(0, writeCount)
        assertEquals(RepairStatus.FAILED, op.queue.value.single().status)
        op.busy.first { !it }
        assertFalse(op.busy.value)
    }

    @Test fun notificationsUseRealProgressAndSeparateHighImportanceCompletionChannel() {
        val component = android.content.ComponentName(context.packageName, "com.erhu.erhu.MainActivity")
        val packageManager = shadowOf(context.packageManager)
        packageManager.addActivityIfNotPresent(component)
        packageManager.addIntentFilterForActivity(component, android.content.IntentFilter(android.content.Intent.ACTION_MAIN).apply {
            addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        })
        val service = Robolectric.buildService(MetadataRepairService::class.java).get()
        val progress = service.progressNotification(RepairProgress(RepairPhase.FETCHING, 14, 34))
        assertEquals(14, progress.extras.getInt(Notification.EXTRA_PROGRESS))
        assertEquals(34, progress.extras.getInt(Notification.EXTRA_PROGRESS_MAX))
        assertTrue(progress.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertFalse(progress.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE))
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        service.createChannels()
        service.completionNotification(RepairProgress(RepairPhase.COMPLETE, 34, 34, 30))
        val completed = shadowOf(manager).getNotification(MetadataRepairService.COMPLETION_NOTIFICATION_ID)
        assertEquals("Metadata fetching complete", completed.extras.getCharSequence(Notification.EXTRA_TITLE))
        assertEquals(NotificationManager.IMPORTANCE_HIGH, manager.getNotificationChannel(MetadataRepairService.COMPLETION_CHANNEL).importance)
        assertEquals("Allow changes", completed.actions.single().title)
        assertTrue(shadowOf(completed.contentIntent).savedIntent.getBooleanExtra(MetadataRepairService.EXTRA_OPEN_REPAIR, false))
        assertTrue(shadowOf(completed.contentIntent).savedIntent.flags and android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
        assertTrue(completed.flags and Notification.FLAG_AUTO_CANCEL != 0)
    }
}
