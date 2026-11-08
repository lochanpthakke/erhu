package com.erhu.erhu.feature.library

import com.erhu.erhu.core.common.logging.Logger
import com.erhu.erhu.core.common.result.AppResult
import com.erhu.erhu.core.common.result.getOrDefault
import com.erhu.erhu.core.database.repository.LibraryRepository
import com.erhu.erhu.core.datastore.RepairBatchPersistence
import com.erhu.erhu.core.designsystem.artwork.ArtworkLoader
import com.erhu.erhu.core.model.MetadataCandidate
import com.erhu.erhu.core.model.ProviderStatus
import com.erhu.erhu.core.model.Song
import com.erhu.erhu.core.model.TagChange
import com.erhu.erhu.core.network.bpm.BpmFinder
import com.erhu.erhu.core.network.bpm.BpmLookup
import com.erhu.erhu.core.network.connectivity.NetworkStatus
import com.erhu.erhu.core.network.connectivity.NetworkStatusSource
import com.erhu.erhu.core.network.repository.DetailedSearchResponse
import com.erhu.erhu.core.network.repository.GenreLookup
import com.erhu.erhu.core.network.repository.MatchDecision
import com.erhu.erhu.core.network.repository.MetadataRepository
import com.erhu.erhu.core.network.repository.MetadataSources
import com.erhu.erhu.core.network.repository.TrackIdentity
import com.erhu.erhu.core.network.repository.TrackMatcher
import com.erhu.erhu.core.network.repository.usableIdentityText
import com.erhu.erhu.core.network.retry.classifyProviderFailure
import com.erhu.erhu.core.network.tagger.EmbeddedTagReader
import com.erhu.erhu.core.network.tagger.FieldFixPlan
import com.erhu.erhu.core.network.tagger.FieldSelection
import com.erhu.erhu.core.network.tagger.FixField
import com.erhu.erhu.core.network.tagger.LocalTrackTags
import com.erhu.erhu.core.network.tagger.ReleaseDate
import com.erhu.erhu.core.network.tagger.ReleaseDateDecision
import com.erhu.erhu.core.network.tagger.TagWriteRequest
import com.erhu.erhu.core.network.tagger.fields
import com.erhu.erhu.core.network.tagger.isUsableTag
import com.erhu.erhu.core.network.tagger.isUsableYearTag
import com.erhu.erhu.core.network.tagger.planFieldFix
import com.erhu.erhu.core.network.tagger.releaseDateFor
import com.erhu.erhu.core.network.tagger.usableBpm
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One operation shared by the service and every LibraryViewModel instance. No Activity is retained.
 * Existing matching/planning code is relocated intact; failures are caught at the operation boundary to retain results.
 */
@Suppress("LargeClass", "TooGenericExceptionCaught")
@Singleton
@OptIn(FlowPreview::class)
class MetadataRepairOperation
    @Inject
    constructor(
        @ApplicationContext private val appContext: android.content.Context,
        private val libraryRepository: LibraryRepository,
        private val metadataRepository: MetadataRepository,
        private val learnedWatermarkDao: com.erhu.erhu.core.database.dao.LearnedWatermarkDao,
        private val fileRenamer: com.erhu.erhu.core.network.tagger.FileRenamer,
        private val titleCandidateGenerator: com.erhu.erhu.core.network.cleaner.TitleCandidateGenerator,
        private val embeddedTagReader: EmbeddedTagReader,
        private val trackMatcher: TrackMatcher,
        private val networkStatus: NetworkStatusSource,
        private val repairBatchPersistence: RepairBatchPersistence,
        private val bpmFinder: BpmFinder,
        private val access: MetadataWriteAccess,
        private val logger: Logger,
    ) {
        internal val queue = MutableStateFlow<List<RepairQueueItem>>(emptyList())
        internal val busy = MutableStateFlow(false)
        internal val pause = MutableStateFlow<BatchPause?>(null)
        val progress = MutableStateFlow(RepairProgress())
        val awaitingApproval = MutableStateFlow(false)
        val openRequested = MutableStateFlow(false)
        val messages = MutableStateFlow<String?>(null)
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private val store = PendingRepairStore(appContext)
        private var pending = PendingRepairs()
        private var options: Options? = null
        private val pauseRequested = MutableStateFlow(false)
        private var applying = false
        private var completedTrackIds = emptySet<Long>()
        private val restoration = kotlinx.coroutines.CompletableDeferred<Unit>()
        private var restorationFailed = false

        init {
            scope.launch {
                try {
                    val saved = repairBatchPersistence.load()
                    pending = withContext(Dispatchers.IO) { store.load() }
                    val songs =
                        if (saved != null || pending.ids.isNotEmpty()) {
                            libraryRepository.getAllSongs().first().associateBy { it.id }
                        } else {
                            emptyMap()
                        }
                    if (queue.value.isEmpty()) {
                        queue.value =
                            saved?.items.orEmpty().mapNotNull { item ->
                                songs[item.songId]?.let {
                                    RepairQueueItem(
                                        it,
                                        if (item.status ==
                                            RepairStatus.FIXING
                                        ) {
                                            RepairStatus.PENDING
                                        } else {
                                            item.status
                                        },
                                        item.reason,
                                    )
                                }
                            }
                    }
                    // The durable plan wins if the process died between its checkpoint and the queue save.
                    val recovered =
                        pending.ids
                            .filter { id ->
                                queue.value.none { it.song.id == id }
                            }.mapNotNull { songs[it]?.let(::RepairQueueItem) }
                    queue.value =
                        (queue.value + recovered).map {
                            when {
                                it.song.id in pending.ids -> it.copy(status = RepairStatus.READY)
                                it.status == RepairStatus.READY -> it.copy(status = RepairStatus.PENDING)
                                else -> it
                            }
                        }
                    awaitingApproval.value = pending.ids.isNotEmpty()
                    pause.value = saved?.pauseReason?.let { BatchPause(BatchPause.Kind.USER, it) }
                } catch (e: Exception) {
                    restorationFailed = true
                    messages.value = "Could not restore metadata repair: ${e.message}. Existing results were kept."
                    logger.w("MetadataRepair", "Could not restore repair", e)
                }
                restoration.complete(Unit)
                queue.combine(pause) { items, reason -> items to reason }.debounce(PERSIST_DEBOUNCE_MS).collect { (items, reason) ->
                    if (!busy.value) repairBatchPersistence.save(items.toPersisted(reason))
                }
            }
        }

        // Restoration is a finite prerequisite; the persistence observer above remains application-scoped.
        suspend fun ready() {
            restoration.await()
        }

        data class Options(
            val provider: String,
            val selection: FieldSelection,
            val acceptAt: Float,
        )

        @Suppress("ReturnCount")
        fun prepare(
            provider: String,
            selection: FieldSelection,
            acceptAt: Float,
        ): Boolean {
            if (busy.value || awaitingApproval.value || restorationFailed) return false
            if (selection.fields.isEmpty() && !selection.rename) {
                messages.value = "Choose at least one field to fix"
                return false
            }
            if (queue.value.none { it.status in RepairStatus.TO_PROCESS }) return false
            queue.value = queue.value.map { if (it.status == RepairStatus.NETWORK_FAILED) it.copy(status = RepairStatus.PENDING) else it }
            completedTrackIds =
                queue.value
                    .filter { it.status != RepairStatus.PENDING && it.status != RepairStatus.FIXING }
                    .map { it.song.id }
                    .toSet()
            busy.value = true
            options = Options(provider, selection, acceptAt)
            pauseRequested.value = false
            progress.value =
                RepairProgress(RepairPhase.FETCHING, queue.value.count { it.status !in RepairStatus.TO_PROCESS }, queue.value.size)
            return true
        }

        fun startFailed(reason: String) {
            busy.value = false
            messages.value = reason
            progress.value = progress.value.copy(phase = RepairPhase.FAILED, reason = reason)
        }

        fun requestPause() {
            pauseRequested.value = true
        }

        fun requestOpen() {
            openRequested.value = true
        }

        fun consumeOpen() {
            openRequested.value = false
        }

        /** Called only by MetadataRepairService. Its lifecycle, not a ViewModel, owns this coroutine. */
        @Suppress("CyclomaticComplexMethod")
        suspend fun execute() {
            try {
                ready()
                if (restorationFailed) {
                    startFailed("Pending metadata could not be restored. No files were changed.")
                    return
                }
                val config =
                    options ?: run {
                        busy.value = false
                        return
                    }
                pause.value = null
                queue.value =
                    queue.value.map { if (it.status == RepairStatus.NETWORK_FAILED) it.copy(status = RepairStatus.PENDING) else it }
                if (MetadataSources.hostsFor(config.provider).isEmpty()) error("Choose a supported metadata source.")
                completedTrackIds =
                    completedTrackIds +
                    queue.value.filter { it.status != RepairStatus.PENDING && it.status != RepairStatus.FIXING }.map { it.song.id }
                do {
                    pause.value = null
                    progress.value = progress.value.copy(phase = RepairPhase.FETCHING, reason = null)
                    val result = fetchBatch(config)
                    val reason = result.pause
                    if (reason == null || !reason.kind.resumesWithNetwork || pauseRequested.value) break
                    progress.value = progress.value.copy(phase = RepairPhase.PAUSED, reason = reason.reason)
                    awaitNetworkOrPause()
                } while (!pauseRequested.value)
                if (pauseRequested.value) pause.value = BatchPause(BatchPause.Kind.USER, "Paused by you")
                awaitingApproval.value = pending.ids.isNotEmpty()
                progress.value =
                    RepairProgress(
                        if (pauseRequested.value || pause.value != null) RepairPhase.PAUSED else RepairPhase.COMPLETE,
                        processedCount(),
                        queue.value.size,
                        pending.ids.size,
                        pause.value?.reason,
                    )
            } catch (e: CancellationException) {
                retainInterruptedResults()
                throw e
            } catch (e: Exception) {
                awaitingApproval.value = pending.ids.isNotEmpty()
                messages.value = "Metadata fetching stopped: ${e.message}"
                progress.value = RepairProgress(RepairPhase.FAILED, processedCount(), queue.value.size, pending.ids.size, messages.value)
                logger.w("MetadataRepair", "Metadata fetching failed", e)
            } finally {
                busy.value = false
                options = null
            }
        }

        private suspend fun retainInterruptedResults() {
            withContext(NonCancellable) {
                pause.value = BatchPause(BatchPause.Kind.USER, "Fetching interrupted; completed results kept")
                queue.value = queue.value.map { if (it.status == RepairStatus.FIXING) it.copy(status = RepairStatus.PENDING) else it }
                try {
                    checkpoint()
                } catch (failure: Exception) {
                    messages.value = "Could not save interrupted metadata work: ${failure.message}"
                }
                awaitingApproval.value = pending.ids.isNotEmpty()
                progress.value =
                    RepairProgress(
                        RepairPhase.CANCELLED,
                        processedCount(),
                        queue.value.size,
                        pending.ids.size,
                        "Fetching interrupted; completed results kept",
                    )
            }
        }

        private suspend fun fetchBatch(config: Options): LookupPhaseResult {
            val plans = pending.plans.toMutableMap()
            val runner =
                RepairBatchRunner(
                    networkStatus = { networkStatus.status.value },
                    pauseRequested = { pauseRequested.value },
                    inProgress = { it.copy(diagnostics = searchingDiagnostics(it, config.provider)) },
                    lookup = {
                        delay(REPAIR_DELAY_MS)
                        lookUpTrack(it, config.provider, config.selection, config.acceptAt, plans)
                    },
                )
            val result =
                runner.lookUp(queue.value, { queue.value = it }) { phase ->
                    queue.value = phase.queue
                    completedTrackIds =
                        completedTrackIds +
                        phase.queue
                            .filter {
                                it.status != RepairStatus.PENDING && it.status != RepairStatus.FIXING
                            }.map { it.song.id }
                    pending = pending.merge(phase, plans)
                    checkpoint()
                    progress.value = RepairProgress(RepairPhase.FETCHING, processedCount(), queue.value.size)
                }
            queue.value = result.queue
            pause.value = result.pause
            pending = pending.merge(result, plans)
            checkpoint()
            return result
        }

        private suspend fun awaitNetworkOrPause() {
            var sawUnavailable = networkStatus.status.value != NetworkStatus.AVAILABLE
            networkStatus.status
                .combine(pauseRequested) { status, stop ->
                    if (status != NetworkStatus.AVAILABLE) sawUnavailable = true
                    stop || (sawUnavailable && status == NetworkStatus.AVAILABLE)
                }.first { it }
        }

        private fun processedCount() = completedTrackIds.size

        private suspend fun checkpoint() {
            withContext(Dispatchers.IO) { store.save(pending) }
            when (val saved = repairBatchPersistence.save(queue.value.toPersisted(pause.value))) {
                is AppResult.Error -> error(saved.error.message)
                is AppResult.Success -> Unit
            }
        }

        /** Invoked by a resumed Activity, never by the notification's service/background callback. */
        fun approvePending() {
            if (!awaitingApproval.value) return
            if (applying || access.isPending || busy.value) return
            applying = true
            busy.value = true
            scope.launch {
                try {
                    val items = queue.value.toMutableList()
                    val writes = items.mapIndexedNotNull { i, item -> pending.writes[item.song.id]?.let { i to it } }.toMap()
                    val renames = items.mapIndexedNotNull { i, item -> pending.renames[item.song.id]?.let { i to it } }.toMap()
                    writePlanned(items, writes, renames, pending.plans)
                } catch (e: Exception) {
                    applying = false
                    busy.value = false
                    messages.value = "Could not apply metadata: ${e.message}. Pending changes were kept."
                }
            }
        }

        private suspend fun applied(id: Long) {
            pending = pending.copy(writes = pending.writes - id, renames = pending.renames - id, plans = pending.plans - id)
            checkpoint()
        }

        private suspend fun finishApproval() {
            pending = PendingRepairs()
            checkpoint()
            awaitingApproval.value = false
            appContext.getSystemService(android.app.NotificationManager::class.java).cancel(
                com.erhu.erhu.feature.library.service.MetadataRepairService.COMPLETION_NOTIFICATION_ID,
            )
            applying = false
            busy.value = false
        }

        private val metadataSourceOptions = MutableStateFlow(metadataSourceOptions())

        private val queueState get() = queue

        /**
         * Writes the planned tag edits, then renames the files that asked for it ("Filename"). A track
         * is only marked fixed once its file was actually written (or renamed, for a rename alone).
         */
        @Suppress("LongMethod", "CyclomaticComplexMethod")
        private suspend fun writePlanned(
            queue: MutableList<RepairQueueItem>,
            writes: Map<Int, TagWriteRequest>,
            renames: Map<Int, String>,
            plans: Map<Long, FieldFixPlan>,
        ) {
            queueState.value = queue.toList()
            val indices = (writes.keys + renames.keys).sorted()
            if (indices.isEmpty()) {
                finishApproval()
                return
            }
            access.withWriteAccess(
                contentUris = indices.map { queue[it].song.uri },
                onGranted = {
                    try {
                        var changedAny = false
                        for (index in indices) {
                            val item = queue[index]
                            val request = writes[index]
                            val filePath =
                                request?.filePath ?: libraryRepository.getSongFilePath(item.song.id) ?: item.song.filePath.orEmpty()
                            val preWriteEmbedded = embeddedTagReader.readEmbeddedTags(filePath)

                            val writeResult = request?.let { metadataRepository.writeTagsToFile(it) }
                            if (writeResult is AppResult.Error) {
                                queue[index] = item.copy(status = RepairStatus.FAILED, failureReason = writeResult.error.message)
                                queueState.value = queue.toList()
                                applied(item.song.id)
                                continue
                            }
                            if (request != null) {
                                changedAny = true
                                ArtworkLoader.invalidate(request.contentUri)
                            }

                            val renameRes =
                                renames[index]?.let { target -> fileRenamer.renameAudioFile(item.song.uri, filePath, target) }
                            val newPath = (renameRes as? AppResult.Success)?.data?.newFilePath
                            val newName = (renameRes as? AppResult.Success)?.data?.newFileName
                            if (newPath != null) {
                                changedAny = true
                                libraryRepository.updateFilePath(item.song.id, newPath)
                            }

                            val successDetails =
                                buildSuccessDetails(
                                    oldTitle = preWriteEmbedded.title ?: item.song.title,
                                    oldArtist = preWriteEmbedded.artist ?: item.song.artist,
                                    oldAlbum = preWriteEmbedded.album ?: item.song.album,
                                    oldYear = preWriteEmbedded.year,
                                    oldGenre = preWriteEmbedded.genre,
                                    newTitle = request?.title ?: item.song.title,
                                    newArtist = request?.artist ?: item.song.artist,
                                    newAlbum = request?.album ?: item.song.album,
                                    newYear = request?.releaseDate ?: request?.year?.toString() ?: preWriteEmbedded.year,
                                    newGenre = request?.genre ?: preWriteEmbedded.genre,
                                    oldFileName = java.io.File(filePath).name,
                                    newFileName = newName,
                                    renameError = (renameRes as? AppResult.Error)?.error?.message,
                                    isManual = false,
                                    extraChanges = plans[item.song.id]?.let(::nonTextChanges).orEmpty(),
                                    hadOriginalArtwork = preWriteEmbedded.artworkBytes != null,
                                    originalArtworkBytes = preWriteEmbedded.artworkBytes,
                                    hadEmbeddedTitle = !preWriteEmbedded.title.isNullOrBlank(),
                                    hadEmbeddedArtist = !preWriteEmbedded.artist.isNullOrBlank(),
                                    hadEmbeddedAlbum = !preWriteEmbedded.album.isNullOrBlank(),
                                )
                            // A selected field that couldn't be found (e.g. no BPM) makes it a partial fix, said so.
                            val unfilled = plans[item.song.id]?.unfilled.orEmpty()
                            queue[index] =
                                when {
                                    renameRes is AppResult.Error && request == null ->
                                        item.copy(
                                            status = RepairStatus.FAILED,
                                            failureReason = renameRes.error.message,
                                            successDetails = successDetails,
                                        )
                                    renameRes is AppResult.Error || unfilled.isNotEmpty() ->
                                        item.copy(
                                            status = RepairStatus.PARTIAL_SUCCESS,
                                            failureReason =
                                                (unfilled + listOfNotNull((renameRes as? AppResult.Error)?.error?.message))
                                                    .joinToString("; "),
                                            successDetails = successDetails,
                                        )
                                    else -> item.copy(status = RepairStatus.DONE, successDetails = successDetails)
                                }
                            queueState.value = queue.toList()
                            applied(item.song.id)
                        }
                        if (changedAny) libraryRepository.requestRescanAfterChange()
                        finishApproval()
                    } catch (e: Exception) {
                        applying = false
                        busy.value = false
                        messages.value = "Could not finish applying metadata: ${e.message}. Remaining edits were kept."
                    }
                },
                onDenied = {
                    for (index in indices) {
                        queue[index] =
                            queue[index].copy(
                                status = RepairStatus.FAILED,
                                failureReason = "Write access not granted; changes were not applied",
                            )
                    }
                    queueState.value = queue.toList()
                    finishApproval()
                },
            )
        }

        /** Looks up one track and concludes what to do with it. Network trouble is reported as such, never as "no match". */
        @Suppress("TooGenericExceptionCaught", "LongMethod", "ReturnCount", "CyclomaticComplexMethod")
        private suspend fun lookUpTrack(
            item: RepairQueueItem,
            provider: String,
            selection: FieldSelection,
            acceptAt: Float,
            plans: MutableMap<Long, FieldFixPlan>,
        ): TrackLookup =
            try {
                val filePath = libraryRepository.getSongFilePath(item.song.id)
                val rawFileName = filePath?.let { java.io.File(it).name } ?: item.song.title
                val activeLearnedTokens = learnedWatermarkDao.getAllActiveTokensSync()
                val cleanerResult =
                    (metadataRepository as? com.erhu.erhu.core.network.repository.MetadataRepositoryImpl)?.let {
                        metadataRepository.cleanFilename(rawFileName, activeLearnedTokens)
                    }
                // The file's own tags are the track's identity; the file name is only a fallback
                // (downloaders shorten it: "THANK YOU.mp3" holds "GONE, GONE / THANK YOU").
                val embedded = filePath?.let { embeddedTagReader.readEmbeddedTags(it) }
                val identity =
                    TrackIdentity(
                        title = usableIdentityText(embedded?.title) ?: usableIdentityText(item.song.title),
                        artist = usableIdentityText(embedded?.artist) ?: usableIdentityText(item.song.artist),
                        album = usableIdentityText(embedded?.album) ?: usableIdentityText(item.song.album),
                        durationMs = item.song.durationMs.takeIf { it > 0 },
                        fileTitles = titleCandidateGenerator.generateCandidates(rawFileName, activeLearnedTokens),
                        fileArtist = usableIdentityText(cleanerResult?.artistGuess),
                    )
                val result =
                    trackMatcher.match(identity, acceptAt) { query ->
                        metadataRepository
                            .searchAndScoreTrackDetailed(
                                title = query.title,
                                artist = query.artist?.takeUnless { it.equals(query.title, ignoreCase = true) },
                                album = query.album,
                                durationMs = identity.durationMs,
                                sourceProvider = provider,
                            ).getOrDefault(DetailedSearchResponse(emptyList(), emptyList()))
                    }
                val candidates = result.candidates
                val outcomes = result.providerOutcomes
                val diag =
                    com.erhu.erhu.core.model.RepairDiagnostics(
                        originalFilename = rawFileName,
                        searchTitle =
                            result.queries
                                .firstOrNull()
                                ?.title
                                .orEmpty(),
                        searchArtist = result.queries.firstOrNull()?.artist,
                        targetDurationMs = item.song.durationMs.takeIf { it > 0 },
                        providerOutcomes = outcomes,
                        topCandidates = candidates.take(TOP_CANDIDATES_SHOWN),
                        embeddedTitle = embedded?.title,
                        embeddedArtist = embedded?.artist,
                        embeddedAlbum = embedded?.album,
                        fileTitles = identity.fileTitles,
                        queries = result.attempts,
                    )

                fun concluded(
                    status: String,
                    reason: String?,
                    write: TagWriteRequest? = null,
                    renameTo: String? = null,
                ) = TrackLookup.Concluded(
                    item.copy(status = status, failureReason = reason, diagnostics = diag.copy(finalReason = reason)),
                    write,
                    renameTo,
                )

                val decision = result.decision
                val verdict = if (decision is MatchDecision.Confident) null else networkVerdict(outcomes)
                when {
                    filePath == null -> concluded(RepairStatus.FAILED, "FILE_NOT_FOUND")
                    verdict is NetworkVerdict.Unreachable -> TrackLookup.Unreachable(verdict.reason)
                    verdict is NetworkVerdict.PartlyUnreachable ->
                        TrackLookup.PartlyUnreachable(item.copy(diagnostics = diag.copy(finalReason = verdict.reason)), verdict.reason)
                    decision is MatchDecision.NoMatch -> {
                        val providerFailure = if (candidates.isEmpty()) providerFailureSummary(outcomes) else null
                        if (providerFailure != null) {
                            concluded(RepairStatus.FAILED, providerFailure)
                        } else {
                            concluded(RepairStatus.NO_MATCH, decision.reason ?: "No online match found")
                        }
                    }
                    decision is MatchDecision.LowConfidence -> concluded(RepairStatus.NEEDS_REVIEW, decision.reason)
                    decision is MatchDecision.Ambiguous -> concluded(RepairStatus.NEEDS_REVIEW, decision.reason)
                    else -> {
                        val match = (decision as MatchDecision.Confident).match
                        val plan = planFix(item.song, filePath, match, candidates, selection, acceptAt)
                        plans[item.song.id] = plan
                        val request = plan.request
                        // "Filename": the file is named after its (new or current) title.
                        val renameTo =
                            if (selection.rename) {
                                val title = request?.title ?: embedded?.title ?: item.song.title
                                fileRenamer
                                    .buildTargetFileName(title, filePath.substringAfterLast('.', "mp3"))
                                    .takeIf { it != java.io.File(filePath).name }
                            } else {
                                null
                            }
                        if (request == null && renameTo == null) {
                            // Nothing to write is not a fix: say why instead of reporting success.
                            val why = plan.notes.joinToString("; ").ifBlank { "The tags already match the online match" }
                            concluded(RepairStatus.UNCHANGED, why)
                        } else {
                            concluded(RepairStatus.READY, null, request, renameTo)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val failure = classifyProviderFailure(e)
                if (failure.status.isTransient) {
                    TrackLookup.PartlyUnreachable(item, failure.message)
                } else {
                    TrackLookup.Concluded(item.copy(status = RepairStatus.FAILED, failureReason = failure.message))
                }
            }

        /** While a track is looked up: the providers being asked, marked as in progress. */
        private fun searchingDiagnostics(
            item: RepairQueueItem,
            provider: String,
        ): com.erhu.erhu.core.model.RepairDiagnostics =
            com.erhu.erhu.core.model.RepairDiagnostics(
                originalFilename = item.song.filePath?.substringAfterLast('/') ?: item.song.title,
                searchTitle = item.song.title,
                searchArtist = item.song.artist,
                providerOutcomes =
                    metadataSourceOptions.value
                        .filter { it.key != MetadataSources.ALL && MetadataSources.matches(it.key, provider) }
                        .map { option ->
                            com.erhu.erhu.core.model
                                .ProviderOutcome(option.label, status = ProviderStatus.IN_PROGRESS)
                        },
            )

        /**
         * Plans the tag edit for a confident [match]: reads the file's current genre and cover, looks
         * up a genre and downloads the cover only when [action] could use them, then lets
         * [planFieldFix] decide field by field. Lookup failures become notes, never empty values;
         * the ones for fields the file needs are also kept as the plan's "unfilled" reasons.
         */
        @Suppress("CyclomaticComplexMethod")
        private suspend fun planFix(
            song: Song,
            filePath: String,
            match: MetadataCandidate,
            candidates: List<MetadataCandidate>,
            selection: FieldSelection,
            acceptAt: Float,
        ): FieldFixPlan {
            val fields = selection.fields
            val fillOnly = selection.fillOnly
            val local = localTags(song, filePath, checkDateFormat = FixField.YEAR in fields)
            // Specific reasons found here replace the plan's generic "not found" notes.
            val notes = mutableMapOf<String, String>()
            val wantsGenre = FixField.GENRE in fields && (!fillOnly || (local.tagsReadable && !isUsableTag(local.genre)))
            val genre = if (wantsGenre) onlineGenre(match, candidates, notes) else null
            val wantsArtwork = FixField.ARTWORK in fields && (!fillOnly || (local.tagsReadable && !local.hasArtwork))
            val artwork = if (wantsArtwork) onlineCover(match, notes) else null
            val wantsYear = FixField.YEAR in fields && (!fillOnly || (local.tagsReadable && !isUsableYearTag(local.year)))
            val releaseDate = if (wantsYear) onlineReleaseDate(match, candidates, acceptAt, notes) else null
            // The file's own plausible BPM is kept by All Fields: no lookup or audio analysis then.
            val wantsBpm = FixField.BPM in fields && (!fillOnly || (local.tagsReadable && usableBpm(local.bpm) == null))
            val bpm = if (wantsBpm) foundBpm(match, candidates, filePath, notes) else null

            val plan = planFieldFix(song.uri, filePath, fields, fillOnly, local, match, genre, artwork, releaseDate, bpm)
            return plan.copy(notes = plan.notes.map { notes[it] ?: it }, unfilled = notes.values.toList())
        }

        /** What the file has now, read from its embedded tags (the library's title / artist / album). */
        private suspend fun localTags(
            song: Song,
            filePath: String,
            checkDateFormat: Boolean,
        ): LocalTrackTags {
            val embedded = embeddedTagReader.readEmbeddedTags(filePath)
            // What the file says, which is what a write changes (the library's copy can differ, e.g.
            // a title Android misread); the library's values only stand in for missing tags.
            return LocalTrackTags(
                title = embedded.title ?: song.title,
                artist = embedded.artist ?: song.artist,
                album = embedded.album ?: song.album,
                genre = embedded.genre,
                hasArtwork = embedded.artworkBytes != null,
                tagsReadable = embedded.hasEmbeddedTag,
                year = embedded.year,
                canStoreYearMonth = checkDateFormat && embeddedTagReader.canStoreYearMonth(filePath),
                bpm = embedded.bpm,
            )
        }

        /** The match's release date if it is valid and unambiguous, or null with the reason in [notes]. */
        private fun onlineReleaseDate(
            match: MetadataCandidate,
            candidates: List<MetadataCandidate>,
            acceptAt: Float,
            notes: MutableMap<String, String>,
        ): ReleaseDate? =
            when (val decision = releaseDateFor(match, candidates, LocalDate.now(), acceptAt)) {
                is ReleaseDateDecision.Found -> decision.date
                is ReleaseDateDecision.Rejected -> null.also { notes[NO_YEAR_NOTE] = "No year: ${decision.reason}" }
            }

        /** The match's genre, or null with the reason in [notes]. */
        private suspend fun onlineGenre(
            match: MetadataCandidate,
            candidates: List<MetadataCandidate>,
            notes: MutableMap<String, String>,
        ): String? =
            when (val lookup = metadataRepository.lookupGenre(match, candidates)) {
                is GenreLookup.Found -> lookup.genre
                is GenreLookup.NotFound -> null.also { notes[NO_GENRE_NOTE] = "No genre: ${lookup.reason}" }
                is GenreLookup.Failed -> null.also { notes[NO_GENRE_NOTE] = "Genre lookup failed: ${lookup.reason}" }
            }

        /** The track's tempo (AcousticBrainz, else estimated on this device), or null with the reason in [notes]. */
        private suspend fun foundBpm(
            match: MetadataCandidate,
            candidates: List<MetadataCandidate>,
            filePath: String,
            notes: MutableMap<String, String>,
        ): BpmLookup.Found? =
            when (val lookup = bpmFinder.find(match, filePath, candidates)) {
                is BpmLookup.Found -> lookup
                is BpmLookup.NotFound -> null.also { notes[NO_BPM_NOTE] = "No BPM: ${lookup.reason}" }
                is BpmLookup.Failed -> null.also { notes[NO_BPM_NOTE] = "BPM lookup failed: ${lookup.reason}" }
            }

        /** The match's cover image, or null with the reason in [notes]. */
        private suspend fun onlineCover(
            match: MetadataCandidate,
            notes: MutableMap<String, String>,
        ): ByteArray? {
            val url =
                match.coverArtUrl
                    ?: return null.also { notes[NO_ARTWORK_NOTE] = "No cover: ${match.sourceName} gave none for this match" }
            return when (val download = metadataRepository.downloadArtwork(url)) {
                is AppResult.Success -> download.data
                is AppResult.Error -> null.also { notes[NO_ARTWORK_NOTE] = "No cover: ${download.error.message}" }
            }
        }

        private fun nonTextChanges(plan: FieldFixPlan): List<TagChange> =
            plan.changes
                .filter { it.field in NON_TEXT_FIELDS }
                .map { change ->
                    val label =
                        when (change.field) {
                            FixField.YEAR -> "Year"
                            FixField.GENRE -> "Genre"
                            FixField.BPM -> "BPM"
                            else -> "Artwork"
                        }
                    TagChange(label, change.oldValue, change.newValue)
                }

        /** When every provider that was asked (or should have been) failed rather than answered, the failures. */
        private fun providerFailureSummary(outcomes: List<com.erhu.erhu.core.model.ProviderOutcome>): String? {
            val considered = outcomes.filter { it.status != ProviderStatus.SKIPPED }
            val failed = considered.filter { it.status !in ANSWERED_STATUSES }
            return when {
                considered.isEmpty() -> "No provider could be queried for this source"
                failed.size != considered.size -> null
                else -> "Lookup failed: " + failed.joinToString("; ") { "${it.providerName}: ${it.errorMessage ?: it.status.label}" }
            }
        }

        companion object {
            private const val PERSIST_DEBOUNCE_MS = 500L
            private const val REPAIR_DELAY_MS = 600L
            private const val NO_GENRE_NOTE = "No genre found online"
            private const val NO_ARTWORK_NOTE = "No artwork found online"
            private const val NO_YEAR_NOTE = "No release year found online"
            private const val NO_BPM_NOTE = "No BPM found"
            private val NON_TEXT_FIELDS = setOf(FixField.YEAR, FixField.GENRE, FixField.ARTWORK, FixField.BPM)
            private const val TOP_CANDIDATES_SHOWN = 3
        }
    }

enum class RepairPhase { IDLE, FETCHING, PAUSED, COMPLETE, CANCELLED, FAILED }

data class RepairProgress(
    val phase: RepairPhase = RepairPhase.IDLE,
    val processed: Int = 0,
    val total: Int = 0,
    val pending: Int = 0,
    val reason: String? = null,
)

// Preserve the existing result builder signature for manual editing and batch repair.
@Suppress("LongParameterList", "LongMethod")
internal fun buildSuccessDetails(
    oldTitle: String,
    oldArtist: String,
    oldAlbum: String,
    oldYear: String? = null,
    oldGenre: String? = null,
    newTitle: String,
    newArtist: String,
    newAlbum: String,
    newYear: String? = null,
    newGenre: String? = null,
    oldFileName: String,
    newFileName: String?,
    renameError: String? = null,
    isManual: Boolean = false,
    extraChanges: List<TagChange> = emptyList(),
    hadOriginalArtwork: Boolean = false,
    originalArtworkBytes: ByteArray? = null,
    hadEmbeddedTitle: Boolean = true,
    hadEmbeddedArtist: Boolean = true,
    hadEmbeddedAlbum: Boolean = true,
): com.erhu.erhu.core.model.SuccessDetails {
    val changes = mutableListOf<com.erhu.erhu.core.model.TagChange>()
    if (oldTitle.trim() != newTitle.trim()) {
        changes.add(
            com.erhu.erhu.core.model
                .TagChange("Title", oldTitle, newTitle),
        )
    }
    if (oldArtist.trim() != newArtist.trim()) {
        changes.add(
            com.erhu.erhu.core.model
                .TagChange("Artist", oldArtist, newArtist),
        )
    }
    if (oldAlbum.trim() != newAlbum.trim()) {
        changes.add(
            com.erhu.erhu.core.model
                .TagChange("Album", oldAlbum, newAlbum),
        )
    }
    if (!oldYear.isNullOrBlank() || !newYear.isNullOrBlank()) {
        if (oldYear?.trim() != newYear?.trim()) {
            changes.add(
                com.erhu.erhu.core.model
                    .TagChange("Year", oldYear.orEmpty(), newYear.orEmpty()),
            )
        }
    }
    if (!oldGenre.isNullOrBlank() || !newGenre.isNullOrBlank()) {
        if (oldGenre?.trim() != newGenre?.trim()) {
            changes.add(
                com.erhu.erhu.core.model
                    .TagChange("Genre", oldGenre.orEmpty(), newGenre.orEmpty()),
            )
        }
    }
    changes += extraChanges
    val isRenamed = newFileName != null && newFileName != oldFileName
    return com.erhu.erhu.core.model.SuccessDetails(
        originalTitle = oldTitle,
        originalArtist = oldArtist,
        originalAlbum = oldAlbum,
        originalYear = oldYear,
        originalGenre = oldGenre,
        updatedTitle = newTitle,
        updatedArtist = newArtist,
        updatedAlbum = newAlbum,
        updatedYear = newYear,
        updatedGenre = newGenre,
        changes = changes,
        isManualSelection = isManual,
        isRenamed = isRenamed,
        oldFilename = oldFileName,
        newFilename = newFileName,
        renameError = renameError,
        hadOriginalArtwork = hadOriginalArtwork,
        originalArtworkBytes = originalArtworkBytes,
        hadEmbeddedTitle = hadEmbeddedTitle,
        hadEmbeddedArtist = hadEmbeddedArtist,
        hadEmbeddedAlbum = hadEmbeddedAlbum,
    )
}
