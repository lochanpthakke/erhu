package com.erhu.erhu.feature.library.remote

import com.erhu.erhu.core.database.entity.RemoteDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveLibrarySyncTest {
    @Test
    fun `planPendingRemoteRead never schedules file reads when readsFiles is false`() {
        val (needsFile, needsDetails) =
            planPendingRemoteRead(
                readsFiles = false,
                durationMs = 0L,
                hasCachedCover = false,
                detailsVersion = 0,
            )

        assertFalse(needsFile)
        assertTrue(needsDetails)
    }

    @Test
    fun `planPendingRemoteRead skips file reads when Drive tags and cover are cached even if details are pending`() {
        val (needsFile, needsDetails) =
            planPendingRemoteRead(
                readsFiles = true,
                durationMs = 200_000L,
                hasCachedCover = true,
                detailsVersion = 0,
            )

        assertFalse(needsFile)
        assertTrue(needsDetails)
    }

    @Test
    fun `planPendingRemoteRead schedules file reads when Drive track lacks duration or cached cover`() {
        assertEquals(
            true to false,
            planPendingRemoteRead(
                readsFiles = true,
                durationMs = 0L,
                hasCachedCover = true,
                detailsVersion = RemoteDetails.VERSION,
            ),
        )
        assertEquals(
            true to false,
            planPendingRemoteRead(
                readsFiles = true,
                durationMs = 180_000L,
                hasCachedCover = false,
                detailsVersion = RemoteDetails.VERSION,
            ),
        )
        assertEquals(
            false to false,
            planPendingRemoteRead(
                readsFiles = true,
                durationMs = 180_000L,
                hasCachedCover = true,
                detailsVersion = RemoteDetails.VERSION,
            ),
        )
    }

    @Test
    fun `librarySummary reports manual-only caching when idle`() {
        val idle = librarySummary(FolderSyncStatus())
        assertFalse(idle.contains("every few hours", ignoreCase = true))
        assertEquals("Tap to refresh now. Synced music stays cached until you refresh again", idle)

        assertEquals("Looking for music in Drive...", librarySummary(FolderSyncStatus(running = true, found = 0)))
        assertEquals(
            "Syncing: 10 songs found, 4 read",
            librarySummary(FolderSyncStatus(running = true, found = 10, tagged = 4)),
        )
        assertEquals(
            "Last sync failed: Offline. Tap to try again",
            librarySummary(FolderSyncStatus(message = "Offline")),
        )
    }
}
