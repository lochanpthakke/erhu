package com.erhu.erhu.core.network.apple

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

class ArtworkCompressionTransactionTest {
    @get:Rule val folder = TemporaryFolder()

    private fun original(): File =
        folder.newFile("original.mp4").apply {
            RandomAccessFile(this, "rw").use { it.setLength(8L * 1024 * 1024) }
        }

    private fun candidate(file: File) {
        RandomAccessFile(file, "rw").use { it.setLength(4L * 1024 * 1024) }
    }

    @Test fun `validated worthwhile candidate atomically replaces original and reports actual sizes`() =
        runBlocking {
            val source = original()
            var committed = false
            val result =
                ArtworkCompressionTransaction().run(source, ::candidate, {
                    assertEquals(8L * 1024 * 1024, source.length())
                    assertEquals(4L * 1024 * 1024, it.length())
                }) { committed = true }
            assertEquals(4L * 1024 * 1024, source.length())
            assertEquals(8L * 1024 * 1024, result.originalBytes)
            assertTrue(committed)
            assertEquals(listOf(source.name), folder.root.list()!!.toList())
        }

    @Test fun `insufficient storage does not begin encoding`() =
        runBlocking {
            val source = original()
            try {
                ArtworkCompressionTransaction(freeBytes = { 0 }).run(source, { error("Must not encode") }, {})
                error("Expected storage failure")
            } catch (_: IOException) {
                assertEquals(8L * 1024 * 1024, source.length())
            }
        }

    @Test fun `encoder failure preserves original and removes only owned staging`() =
        runBlocking {
            val source = original()
            val unrelated = folder.newFile("other-operation.tmp")
            try {
                ArtworkCompressionTransaction().run(source, {
                    candidate(it)
                    throw IOException("Encoder failed")
                }, {})
                error("Expected encoder failure")
            } catch (_: IOException) {
                assertEquals(8L * 1024 * 1024, source.length())
                assertTrue(unrelated.exists())
                assertEquals(2, folder.root.list()!!.size)
            }
        }

    @Test fun `cancellation during encoding never promotes staging`() =
        runBlocking {
            val source = original()
            try {
                ArtworkCompressionTransaction().run(source, {
                    candidate(it)
                    throw CancellationException()
                }, {})
                error("Expected cancellation")
            } catch (_: CancellationException) {
                assertEquals(8L * 1024 * 1024, source.length())
                assertEquals(1, folder.root.list()!!.size)
            }
        }

    @Test fun `invalid metadata or failed full decoding retains original`() =
        runBlocking {
            val source = original()
            try {
                ArtworkCompressionTransaction().run(source, ::candidate, { throw IOException("Invalid metadata/decode") })
                error("Expected validation failure")
            } catch (_: IOException) {
                assertEquals(8L * 1024 * 1024, source.length())
                assertEquals(1, folder.root.list()!!.size)
            }
        }

    @Test fun `failed replacement retains original and never reports success`() =
        runBlocking {
            val source = original()
            var committed = false
            try {
                ArtworkCompressionTransaction(publisher = VerifiedArtworkPublisher { _, _ -> throw IOException("Move failed") })
                    .run(source, ::candidate, {}) { committed = true }
                error("Expected move failure")
            } catch (_: IOException) {
                assertFalse(committed)
                assertEquals(8L * 1024 * 1024, source.length())
            }
        }

    @Test fun `candidate with no worthwhile savings is discarded`() =
        runBlocking {
            val source = original()
            try {
                ArtworkCompressionTransaction().run(source, { source.copyTo(it, overwrite = true) }, { error("Must not validate") })
                error("Expected savings rejection")
            } catch (_: IOException) {
                assertEquals(8L * 1024 * 1024, source.length())
            }
        }

    @Test fun `concurrently changed original is retained instead of overwritten`() =
        runBlocking {
            val source = original()
            try {
                ArtworkCompressionTransaction().run(source, {
                    candidate(it)
                    source.writeText("new download")
                }, {})
                error("Expected source change failure")
            } catch (_: IOException) {
                assertEquals("new download", source.readText())
            }
        }
}
