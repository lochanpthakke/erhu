package com.erhu.erhu.feature.library

import com.erhu.erhu.core.model.ProviderOutcome
import com.erhu.erhu.core.model.ProviderStatus
import com.erhu.erhu.core.model.Song
import com.erhu.erhu.core.network.connectivity.NetworkStatus
import com.erhu.erhu.core.network.repository.MetadataSources
import com.erhu.erhu.core.network.tagger.TagWriteRequest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RepairBatchRunnerTest {
    private fun song(id: Long) = Song(id, "Song $id", "Artist", "Album", 200_000L, "content://media/$id")

    private fun queue(
        size: Int,
        status: String = RepairStatus.PENDING,
    ) = (1..size).map { RepairQueueItem(song(it.toLong()), status) }

    private fun write(id: Long) = TagWriteRequest("content://media/$id", "/music/$id.mp3", genre = "hip hop")

    /** A lookup that fixes every track it is given, and records which ones. */
    private class FixEverything {
        val looked = mutableListOf<Long>()

        fun lookup(item: RepairQueueItem): TrackLookup {
            looked += item.song.id
            return TrackLookup.Concluded(
                item.copy(status = RepairStatus.FIXING),
                TagWriteRequest(item.song.uri, "/music/${item.song.id}.mp3", genre = "hip hop"),
            )
        }
    }

    // ---- the reported failure: the connection goes in the middle of a batch

    @Test
    fun networkBlockedMidBatch_pausesTheBatch_insteadOfFailingEveryRemainingTrack() =
        runTest {
            var network = NetworkStatus.AVAILABLE
            val fixer = FixEverything()
            val runner =
                RepairBatchRunner(
                    networkStatus = { network },
                    pauseRequested = { false },
                    lookup = { item ->
                        // erhu goes to the background with Battery Saver on after the third track.
                        if (item.song.id == 3L) network = NetworkStatus.BLOCKED
                        fixer.lookup(item)
                    },
                )

            val result = runner.lookUp(queue(10))

            assertEquals(listOf(1L, 2L, 3L), fixer.looked)
            assertEquals(BatchPause.Kind.BLOCKED, result.pause?.kind)
            assertTrue(result.pause!!.reason.contains("Battery Saver"))
            assertEquals("the finished lookups are still written", setOf(0, 1, 2), result.writes.keys)
            assertEquals(7, result.queue.count { it.status == RepairStatus.PENDING })
            assertEquals(0, result.queue.count { it.status == RepairStatus.NO_MATCH || it.status == RepairStatus.FAILED })
        }

    @Test
    fun dnsFailureForEveryProvider_putsTheTrackBack_andPauses() =
        runTest {
            val runner =
                RepairBatchRunner(
                    networkStatus = { NetworkStatus.AVAILABLE },
                    pauseRequested = { false },
                    lookup = { item ->
                        if (item.song.id >= 2L) {
                            TrackLookup.Unreachable("MusicBrainz: Couldn't resolve the provider's address; Deezer: Couldn't resolve…")
                        } else {
                            TrackLookup.Concluded(item.copy(status = RepairStatus.NO_MATCH, failureReason = "No close match"))
                        }
                    },
                )

            val result = runner.lookUp(queue(5))

            assertEquals(BatchPause.Kind.UNREACHABLE, result.pause?.kind)
            assertEquals(RepairStatus.NO_MATCH, result.queue[0].status)
            assertEquals("the unreachable track wasn't checked", RepairStatus.PENDING, result.queue[1].status)
            assertTrue(result.queue.drop(1).all { it.status == RepairStatus.PENDING })
        }

    @Test
    fun offlineAtStart_looksNothingUp() =
        runTest {
            val fixer = FixEverything()
            val runner = RepairBatchRunner({ NetworkStatus.OFFLINE }, { false }, lookup = fixer::lookup)

            val result = runner.lookUp(queue(3))

            assertTrue(fixer.looked.isEmpty())
            assertEquals(BatchPause.Kind.OFFLINE, result.pause?.kind)
            assertTrue(result.queue.all { it.status == RepairStatus.PENDING })
        }

    @Test
    fun aPartlyUnreachableTrack_isANetworkFailure_andTheBatchGoesOn() =
        runTest {
            val runner =
                RepairBatchRunner(
                    networkStatus = { NetworkStatus.AVAILABLE },
                    pauseRequested = { false },
                    lookup = { item ->
                        if (item.song.id == 1L) {
                            TrackLookup.PartlyUnreachable(item, "Deezer: Request timed out")
                        } else {
                            TrackLookup.Concluded(item.copy(status = RepairStatus.UNCHANGED))
                        }
                    },
                )

            val result = runner.lookUp(queue(3))

            assertNull(result.pause)
            assertEquals(RepairStatus.NETWORK_FAILED, result.queue[0].status)
            assertEquals("Deezer: Request timed out", result.queue[0].failureReason)
            assertTrue(result.queue.drop(1).all { it.status == RepairStatus.UNCHANGED })
        }

    // ---- resume

    @Test
    fun resume_retriesOnlyPendingAndNetworkFailures_neverFinishedTracks() =
        runTest {
            val start =
                listOf(
                    RepairQueueItem(song(1), RepairStatus.DONE),
                    RepairQueueItem(song(2), RepairStatus.UNCHANGED),
                    RepairQueueItem(song(3), RepairStatus.NO_MATCH),
                    RepairQueueItem(song(4), RepairStatus.NEEDS_REVIEW),
                    RepairQueueItem(song(5), RepairStatus.NETWORK_FAILED, "Deezer: Request timed out"),
                    RepairQueueItem(song(6), RepairStatus.PENDING),
                )
            val fixer = FixEverything()

            val result = RepairBatchRunner({ NetworkStatus.AVAILABLE }, { false }, lookup = fixer::lookup).lookUp(start)

            assertEquals(listOf(5L, 6L), fixer.looked)
            assertEquals(setOf(4, 5), result.writes.keys)
            assertEquals(start.take(4), result.queue.take(4))
        }

    @Test
    fun aUserPause_stopsBeforeTheNextTrack_andKeepsWhatWasFound() =
        runTest {
            var pause = false
            val fixer = FixEverything()
            val runner =
                RepairBatchRunner(
                    networkStatus = { NetworkStatus.AVAILABLE },
                    pauseRequested = { pause },
                    lookup = { item ->
                        pause = true
                        fixer.lookup(item)
                    },
                )

            val result = runner.lookUp(queue(4))

            assertEquals(listOf(1L), fixer.looked)
            assertEquals(BatchPause.Kind.USER, result.pause?.kind)
            assertFalse(result.pause!!.kind.resumesWithNetwork)
            assertEquals(setOf(0), result.writes.keys)
        }

    // ---- what the providers' answers mean for a track

    @Test
    fun networkVerdict_separatesConnectionTroubleFromNoMatch() {
        fun outcome(
            name: String,
            status: ProviderStatus,
        ) = ProviderOutcome(name, status = status, errorMessage = "$name says $status")

        // What the dialog showed: both providers' hostnames failed to resolve.
        val dns = listOf(outcome("MusicBrainz", ProviderStatus.NETWORK_ERROR), outcome("Deezer", ProviderStatus.NETWORK_ERROR))
        assertTrue(networkVerdict(dns) is NetworkVerdict.Unreachable)

        // India: Deezer never answers (withheld, a permanent error) and MusicBrainz is unreachable.
        val india = listOf(outcome("MusicBrainz", ProviderStatus.NETWORK_ERROR), outcome("Deezer", ProviderStatus.FAILED))
        assertTrue(networkVerdict(india) is NetworkVerdict.Unreachable)

        val partly = listOf(outcome("MusicBrainz", ProviderStatus.NO_RESULTS), outcome("Deezer", ProviderStatus.RATE_LIMITED))
        assertTrue(networkVerdict(partly) is NetworkVerdict.PartlyUnreachable)

        val answered =
            listOf(
                outcome("MusicBrainz", ProviderStatus.NO_RESULTS),
                outcome("Deezer", ProviderStatus.FAILED),
                outcome("Last.fm", ProviderStatus.NOT_CONFIGURED),
            )
        assertNull("a real no-match stays a no-match", networkVerdict(answered))
    }

    @Test
    fun summary_countsEachOutcomeOnce() {
        val items =
            listOf(RepairStatus.DONE, RepairStatus.PARTIAL_SUCCESS, RepairStatus.UNCHANGED, RepairStatus.NEEDS_REVIEW) +
                List(3) { RepairStatus.NO_MATCH } +
                List(2) { RepairStatus.NETWORK_FAILED } +
                listOf(RepairStatus.FAILED, RepairStatus.PENDING)
        val summary = RepairSummary.of(items.mapIndexed { i, status -> RepairQueueItem(song(i.toLong()), status) })
        assertEquals(
            RepairSummary(fixed = 2, unchanged = 1, needsReview = 1, noMatch = 3, otherFailures = 1, networkFailures = 2, notChecked = 1),
            summary,
        )
    }

    // ---- what is shown and saved

    @Test
    fun providerLines_alwaysSayWhyThereIsNoAnswer() {
        fun line(
            status: ProviderStatus,
            reason: String,
        ) = providerStatusText(ProviderOutcome("Last.fm", status = status, errorMessage = reason))
        assertEquals("Not queried: Not selected (source: MusicBrainz)", line(ProviderStatus.SKIPPED, "Not selected (source: MusicBrainz)"))
        assertEquals("Not configured: No Last.fm API key configured", line(ProviderStatus.NOT_CONFIGURED, "No Last.fm API key configured"))
        assertEquals("Credentials rejected: Last.fm error 10: Invalid API key", line(ProviderStatus.AUTH_FAILED, "Last.fm error 10: Invalid API key"))
        assertTrue(line(ProviderStatus.NETWORK_ERROR, "dns").startsWith("Network unavailable"))
        assertEquals("Searching…", providerStatusText(ProviderOutcome("Deezer", status = ProviderStatus.IN_PROGRESS)))
        for (status in ProviderStatus.entries) {
            assertFalse(providerStatusText(ProviderOutcome("X", status = status)).contains("Provider not queried"))
        }
    }

    @Test
    fun savedState_neverClaimsAnUnwrittenTrackIsDone() {
        val items =
            listOf(
                RepairQueueItem(song(1), RepairStatus.DONE),
                RepairQueueItem(song(2), RepairStatus.FIXING),
                RepairQueueItem(song(3), RepairStatus.NETWORK_FAILED, "timeout"),
            )
        val saved = items.toPersisted(BatchPause(BatchPause.Kind.OFFLINE, "No internet connection"))
        assertEquals(listOf("DONE", "PENDING", "NETWORK_FAILED"), saved.items.map { it.status })
        assertEquals("timeout", saved.items[2].reason)
        assertEquals("No internet connection", saved.pauseReason)
        assertNull(items.toPersisted(null).pauseReason)
    }

    @Test
    fun lastFm_isOfferedWithItsRealConfiguration() {
        val unconfigured = metadataSourceOptions(lastFmConfigured = false).single { it.key == MetadataSources.LAST_FM }
        assertTrue(unconfigured.summary.startsWith("Not configured"))
        val configured = metadataSourceOptions(lastFmConfigured = true).single { it.key == MetadataSources.LAST_FM }
        assertFalse(configured.summary.startsWith("Not configured"))
        assertEquals(MetadataSources.LAST_FM, offeredMetadataSource("LastFm"))
        assertEquals(MetadataSources.ALL, offeredMetadataSource("Spotify"))
    }

    @Test
    fun aRealWrite_isOnlyPlannedForConcludedTracks() =
        runTest {
            val runner =
                RepairBatchRunner(
                    { NetworkStatus.AVAILABLE },
                    { false },
                    lookup = { item -> TrackLookup.Concluded(item.copy(status = RepairStatus.FIXING), write(item.song.id)) },
                )
            assertEquals(mapOf(0 to write(1), 1 to write(2)), runner.lookUp(queue(2)).writes)
        }

    @Test
    fun aRenameOnlyTrack_isPlannedForTheWritePhase() =
        runTest {
            val runner =
                RepairBatchRunner(
                    { NetworkStatus.AVAILABLE },
                    { false },
                    lookup = { item -> TrackLookup.Concluded(item.copy(status = RepairStatus.FIXING), write = null, renameTo = "Song.mp3") },
                )
            val result = runner.lookUp(queue(1))
            assertTrue(result.writes.isEmpty())
            assertEquals(mapOf(0 to "Song.mp3"), result.renames)
        }

    @Test
    fun theThreshold_defaultsTo85_andStaysInRange() {
        assertEquals(0.85f, effectiveThreshold(0f), 0.0001f)
        assertEquals(0.75f, effectiveThreshold(0.75f), 0.0001f)
        assertEquals(LibraryViewModel.MIN_THRESHOLD, effectiveThreshold(0.2f), 0.0001f)
        assertEquals(LibraryViewModel.MAX_THRESHOLD, effectiveThreshold(1.4f), 0.0001f)
    }
}
