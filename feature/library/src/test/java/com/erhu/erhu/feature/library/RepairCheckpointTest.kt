/*
 * Copyright (C) 2026 Kshitij H Kekunnaya
 *
 * This file is part of erhu.
 * Future Android releases from 1.0.18: proprietary terms in LICENSE.
 * Historical GPL grants are preserved in LICENSES/GPL-3.0-historical.txt.
 */

package com.erhu.erhu.feature.library

import com.erhu.erhu.core.model.Song
import com.erhu.erhu.core.network.connectivity.NetworkStatus
import com.erhu.erhu.core.network.tagger.TagWriteRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class RepairCheckpointTest {
    @Test fun cancellationKeepsCompletedEditsAndResumeNeverLooksThemUpAgain() = runTest {
        val tracks = (1L..3L).map { RepairQueueItem(Song(it, "Song $it", "Artist", "Album", 1000, "content://media/$it")) }
        var checkpoint: LookupPhaseResult? = null
        val runner = RepairBatchRunner({ NetworkStatus.AVAILABLE }, { false }, lookup = {
            if (it.song.id == 2L) throw CancellationException("process interrupted")
            TrackLookup.Concluded(it.copy(status = RepairStatus.READY), TagWriteRequest(it.song.uri, "/test.mp3", title = "Correct title"))
        })
        try {
            runner.lookUp(tracks, onTrackComplete = { checkpoint = it })
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
        val kept = requireNotNull(checkpoint)
        assertEquals(listOf(RepairStatus.READY, RepairStatus.PENDING, RepairStatus.PENDING), kept.queue.map { it.status })
        assertEquals(1, kept.writes.size)
        val looked = mutableListOf<Long>()
        val resumed = RepairBatchRunner({ NetworkStatus.AVAILABLE }, { false }, lookup = {
            looked += it.song.id
            TrackLookup.Concluded(it.copy(status = RepairStatus.NO_MATCH))
        }).lookUp(kept.queue)
        assertEquals(listOf(2L, 3L), looked)
        assertEquals(RepairStatus.READY, resumed.queue.first().status)
    }

    @Test fun unreachableTrackDoesNotAdvanceProgressOrLosePriorWrites() = runTest {
        val items = (1L..2L).map { RepairQueueItem(Song(it, "Song", "Artist", "Album", 1000, "content://media/$it")) }
        val completed = mutableListOf<LookupPhaseResult>()
        val result = RepairBatchRunner({ NetworkStatus.AVAILABLE }, { false }, lookup = {
            if (it.song.id == 1L) TrackLookup.Concluded(it.copy(status = RepairStatus.READY),
                TagWriteRequest(it.song.uri, "/test.mp3", title = "Correct title"))
            else TrackLookup.Unreachable("Provider outage")
        }).lookUp(items, onTrackComplete = { completed += it })
        assertEquals(1, completed.size)
        assertEquals(BatchPause.Kind.UNREACHABLE, result.pause?.kind)
        assertEquals(RepairStatus.PENDING, result.queue.last().status)
        assertEquals(1, result.writes.size)
    }
}
