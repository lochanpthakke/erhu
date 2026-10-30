package com.lochanpthakke.empee3.core.network.apple

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class VerifiedArtworkPublisherTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun `publication decodes the completed candidate before replacing an existing original`() =
        runBlocking {
            val original = File(folder.root, "cover.mp4").apply { writeText("original") }
            val staged = File(folder.root, "cover.mp4.tmp").apply { writeText("complete candidate") }
            var validated = false
            VerifiedArtworkPublisher().publish(staged, original) {
                assertEquals("original", original.readText())
                assertEquals("complete candidate", it.readText())
                validated = true
            }
            assertTrue(validated)
            assertEquals("complete candidate", original.readText())
            assertFalse(staged.exists())
        }

    @Test fun `failed decoding retains the original and never calls replacement`() =
        runBlocking {
            val original = File(folder.root, "cover.mp4").apply { writeText("original") }
            val staged = File(folder.root, "cover.mp4.tmp").apply { writeText("corrupt input") }
            try {
                VerifiedArtworkPublisher { _, _ -> fail("Must not replace undecodable input") }.publish(staged, original) {
                    throw IOException("Decoder failed")
                }
                fail("Expected failure")
            } catch (_: IOException) {
                // original remains
            }
            assertEquals("original", original.readText())
        }

    @Test fun `disk full or failed atomic move cannot delete the previous cover`() =
        runBlocking {
            val original = File(folder.root, "cover.mp4").apply { writeText("original") }
            val staged = File(folder.root, "cover.mp4.tmp").apply { writeText("candidate") }
            try {
                VerifiedArtworkPublisher { _, _ -> throw IOException("No space / atomic move failed") }.publish(staged, original) { }
                fail("Expected failure")
            } catch (_: IOException) {
                // original remains
            }
            assertEquals("original", original.readText())
            assertTrue(staged.exists())
        }

    @Test fun `cancellation before promotion retains both the old file and the operation owned candidate`() =
        runBlocking {
            val original = File(folder.root, "cover.mp4").apply { writeText("original") }
            val staged = File(folder.root, "cover.mp4.tmp").apply { writeText("candidate") }
            try {
                VerifiedArtworkPublisher().publish(staged, original) { throw CancellationException("Cancelled decoding") }
                fail("Expected cancellation")
            } catch (_: CancellationException) {
                // caller owns staged cleanup
            }
            assertEquals("original", original.readText())
            assertTrue(staged.exists())
        }

    @Test fun `staging and destination must be distinct files on the same directory`() =
        runBlocking {
            val original = File(folder.root, "cover.mp4").apply { writeText("original") }
            val other = folder.newFolder("other")
            val staged = File(other, "cover.tmp").apply { writeText("candidate") }
            for (candidate in listOf(original, staged)) {
                try {
                    VerifiedArtworkPublisher().publish(candidate, original) { fail("Invalid staging must not decode") }
                    fail("Expected invalid staging")
                } catch (_: IllegalArgumentException) {
                    // rejected
                }
            }
            assertEquals("original", original.readText())
        }

    @Test fun `interruption before publication leaves the previous cover recoverable`() =
        runBlocking {
            val original = File(folder.root, "cover.mp4").apply { writeText("original") }
            File(folder.root, "cover.mp4.tmp").writeText("interrupted candidate")
            assertEquals("original", original.readText())
            val staged = File(folder.root, "new.tmp").apply { writeText("complete next download") }
            VerifiedArtworkPublisher().publish(staged, original) { }
            assertEquals("complete next download", original.readText())
            assertTrue(File(folder.root, "cover.mp4.tmp").exists())
        }
}
