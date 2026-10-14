package com.lochanpthakke.empee3.feature.library

import com.lochanpthakke.empee3.core.model.ProviderOutcome
import com.lochanpthakke.empee3.core.model.ProviderStatus
import com.lochanpthakke.empee3.core.network.connectivity.NetworkStatus
import com.lochanpthakke.empee3.core.network.tagger.TagWriteRequest

/** Queue statuses of the metadata repair batch. */
object RepairStatus {
    const val PENDING = "PENDING"
    const val FIXING = "FIXING"
    const val DONE = "DONE"
    const val PARTIAL_SUCCESS = "PARTIAL_SUCCESS"
    const val UNCHANGED = "UNCHANGED"
    const val NEEDS_REVIEW = "NEEDS_REVIEW"

    /** Every provider answered and none had a close enough match. */
    const val NO_MATCH = "NO_MATCH"

    /** Anything else that went wrong for this track (file missing, write refused, provider errors). */
    const val FAILED = "FAILED"

    /** A provider couldn't be reached for this track: not a "no match", and retried on resume. */
    const val NETWORK_FAILED = "NETWORK_FAILED"

    /** Statuses a run (or a resume) looks up; everything else is finished and never redone. */
    val TO_PROCESS = setOf(PENDING, NETWORK_FAILED)
}

/** What looking up one track concluded. */
sealed interface TrackLookup {
    /** A conclusion for this track: [item] carries its new status; [write] is the tag edit to make, if any. */
    data class Concluded(
        val item: RepairQueueItem,
        val write: TagWriteRequest? = null,
        /** File name to give the track ("Filename" field), when it differs from the current one. */
        val renameTo: String? = null,
    ) : TrackLookup

    /** Some providers answered, others couldn't be reached, and nothing confident was found: retry later. */
    data class PartlyUnreachable(
        val item: RepairQueueItem,
        val reason: String,
    ) : TrackLookup

    /** No provider could be reached at all: the connection, not the track. The batch pauses here. */
    data class Unreachable(
        val reason: String,
    ) : TrackLookup
}

/** Why a batch stopped before every track was looked up. */
data class BatchPause(
    val kind: Kind,
    val reason: String,
) {
    enum class Kind {
        /** No network at all. */
        OFFLINE,

        /** Android blocks empee3's network (background with Battery Saver / Data Saver). */
        BLOCKED,

        /** A network is up but no provider answered (DNS or connection failures). */
        UNREACHABLE,

        /** The user paused it. */
        USER,
        ;

        /** Paused by the connection: resumes by itself when the network comes back. */
        val resumesWithNetwork: Boolean get() = this != USER
    }
}

data class LookupPhaseResult(
    val queue: List<RepairQueueItem>,
    /** Tag edits to make, by queue index: only for tracks whose lookup concluded with a change. */
    val writes: Map<Int, TagWriteRequest>,
    val pause: BatchPause?,
    /** Files to rename, by queue index (after their tags are written, if any). */
    val renames: Map<Int, String> = emptyMap(),
)

/**
 * The lookup half of a repair batch: goes through the tracks still to process
 * ([RepairStatus.TO_PROCESS]), never repeats finished ones, and stops gracefully when the
 * connection goes: before each track it checks [networkStatus], and a track for which no provider
 * could be reached is put back (not counted as "no match") and pauses the batch. Everything
 * concluded before the pause is returned, so its writes still happen.
 */
class RepairBatchRunner(
    private val networkStatus: () -> NetworkStatus,
    private val pauseRequested: () -> Boolean,
    private val inProgress: (RepairQueueItem) -> RepairQueueItem = { it },
    private val lookup: suspend (RepairQueueItem) -> TrackLookup,
) {
    @Suppress("ReturnCount")
    suspend fun lookUp(
        start: List<RepairQueueItem>,
        onProgress: (List<RepairQueueItem>) -> Unit = {},
    ): LookupPhaseResult {
        val queue = start.toMutableList()
        val writes = mutableMapOf<Int, TagWriteRequest>()
        val renames = mutableMapOf<Int, String>()

        fun result(pause: BatchPause?) = LookupPhaseResult(queue, writes, pause, renames)
        for (i in queue.indices) {
            val item = queue[i]
            if (item.status !in RepairStatus.TO_PROCESS) continue
            networkPause(networkStatus())?.let { return result(it) }
            if (pauseRequested()) return result(BatchPause(BatchPause.Kind.USER, "Paused"))

            queue[i] = inProgress(item.copy(status = RepairStatus.FIXING, failureReason = null))
            onProgress(queue.toList())
            when (val result = lookup(item)) {
                is TrackLookup.Concluded -> {
                    queue[i] = result.item
                    result.write?.let { writes[i] = it }
                    result.renameTo?.let { renames[i] = it }
                }
                is TrackLookup.PartlyUnreachable ->
                    queue[i] = result.item.copy(status = RepairStatus.NETWORK_FAILED, failureReason = result.reason)
                is TrackLookup.Unreachable -> {
                    // The track wasn't checked: it goes back exactly as it was.
                    queue[i] = item
                    onProgress(queue.toList())
                    val pause =
                        networkPause(networkStatus()) ?: BatchPause(BatchPause.Kind.UNREACHABLE, result.reason)
                    return result(pause)
                }
            }
            onProgress(queue.toList())
        }
        return result(null)
    }

    private fun networkPause(status: NetworkStatus): BatchPause? =
        when (status) {
            NetworkStatus.AVAILABLE -> null
            NetworkStatus.OFFLINE -> BatchPause(BatchPause.Kind.OFFLINE, "No internet connection")
            NetworkStatus.BLOCKED ->
                BatchPause(
                    BatchPause.Kind.BLOCKED,
                    "Android blocked empee3's network access (it was in the background with Battery Saver or Data Saver on)",
                )
        }
}

/**
 * How the providers' answers bear on a track that didn't get a confident match: if a provider
 * couldn't be reached, the track is not "no match". Returns null when every queried provider
 * answered (then the match decision stands as it is).
 */
fun networkVerdict(outcomes: List<ProviderOutcome>): NetworkVerdict? {
    val unreachable = outcomes.filter { it.status.isTransient }
    if (unreachable.isEmpty()) return null
    val answered = outcomes.filter { it.status in ANSWERED_STATUSES }
    val reason = unreachable.joinToString("; ") { "${it.providerName}: ${it.errorMessage ?: it.status.label}" }
    return if (answered.isEmpty()) NetworkVerdict.Unreachable(reason) else NetworkVerdict.PartlyUnreachable(reason)
}

sealed interface NetworkVerdict {
    val reason: String

    data class Unreachable(
        override val reason: String,
    ) : NetworkVerdict

    data class PartlyUnreachable(
        override val reason: String,
    ) : NetworkVerdict
}

/** Statuses of a provider that was asked and answered (with or without results). */
internal val ANSWERED_STATUSES =
    setOf(ProviderStatus.NO_RESULTS, ProviderStatus.CANDIDATES_FOUND, ProviderStatus.LOW_CONFIDENCE, ProviderStatus.SUCCESS)

/** Counts for the summary: each status once, never a network failure counted as "no match". */
data class RepairSummary(
    val fixed: Int,
    val unchanged: Int,
    val needsReview: Int,
    val noMatch: Int,
    val otherFailures: Int,
    val networkFailures: Int,
    val notChecked: Int,
) {
    companion object {
        fun of(queue: List<RepairQueueItem>): RepairSummary {
            fun count(predicate: (RepairQueueItem) -> Boolean) = queue.count(predicate)
            return RepairSummary(
                fixed = count { it.status == RepairStatus.DONE || it.status == RepairStatus.PARTIAL_SUCCESS || it.status == "SUCCESS" },
                unchanged = count { it.status == RepairStatus.UNCHANGED },
                needsReview = count { it.status == RepairStatus.NEEDS_REVIEW },
                noMatch = count { it.status == RepairStatus.NO_MATCH },
                otherFailures = count { it.status == RepairStatus.FAILED },
                networkFailures = count { it.status == RepairStatus.NETWORK_FAILED },
                notChecked = count { it.status == RepairStatus.PENDING || it.status == RepairStatus.FIXING },
            )
        }
    }
}
