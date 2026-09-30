package com.lochanpthakke.empee3.core.network.tagger

import com.lochanpthakke.empee3.core.common.logging.Logger
import com.lochanpthakke.empee3.core.common.result.AppError
import com.lochanpthakke.empee3.core.common.result.AppResult
import kotlinx.coroutines.runBlocking
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class JAudioTaggerWriterTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private object SilentLogger : Logger {
        override fun v(
            tag: String,
            message: String,
            throwable: Throwable?,
        ) = Unit

        override fun d(
            tag: String,
            message: String,
            throwable: Throwable?,
        ) = Unit

        override fun i(
            tag: String,
            message: String,
            throwable: Throwable?,
        ) = Unit

        override fun w(
            tag: String,
            message: String,
            throwable: Throwable?,
        ) = Unit

        override fun e(
            tag: String,
            message: String,
            throwable: Throwable?,
        ) = Unit
    }

    /** In-memory stand-in for MediaStore content URIs. */
    private class FakeStore : AudioContentStore {
        val files = mutableMapOf<String, ByteArray>()
        var denyWrites = false

        /** Throw after this many bytes of the next overwrite (simulates I/O failure mid-write). */
        var failNextWriteAfterBytes: Int? = null

        override fun openInput(contentUri: String): InputStream =
            ByteArrayInputStream(files[contentUri] ?: throw FileNotFoundException(contentUri))

        override fun openOverwrite(contentUri: String): OutputStream {
            if (denyWrites) throw SecurityException("com.lochanpthakke.empee3 has no access to $contentUri")
            if (contentUri !in files) throw FileNotFoundException(contentUri)
            val failAfter = failNextWriteAfterBytes
            failNextWriteAfterBytes = null
            files[contentUri] = ByteArray(0) // truncated, like mode "rwt"
            return object : OutputStream() {
                private val buffer = ByteArrayOutputStream()

                override fun write(b: Int) {
                    if (failAfter != null && buffer.size() >= failAfter) throw IOException("disk full")
                    buffer.write(b)
                    files[contentUri] = buffer.toByteArray()
                }

                override fun write(
                    b: ByteArray,
                    off: Int,
                    len: Int,
                ) {
                    for (i in off until off + len) write(b[i].toInt())
                }
            }
        }
    }

    private val uri = "content://media/external/audio/media/42"
    private val path = "/storage/emulated/0/Download/test.wav"
    private lateinit var store: FakeStore
    private lateinit var workDir: File
    private val rescans = mutableListOf<String>()
    private lateinit var writer: JAudioTaggerWriter

    @Before
    fun setUp() {
        store = FakeStore()
        workDir = tmp.newFolder("work")
        writer = JAudioTaggerWriter(store, { workDir }, { filePath, _ -> rescans += filePath }, SilentLogger)
    }

    /** A valid 16-bit mono PCM WAV with a short silence. */
    private fun wavBytes(): ByteArray {
        val samples = 8000
        val dataSize = samples * 2
        val buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray()).putInt(36 + dataSize).put("WAVE".toByteArray())
        buffer.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(8000).putInt(16000).putShort(2).putShort(16)
        buffer.put("data".toByteArray()).putInt(dataSize)
        return buffer.array()
    }

    /** Tags as read back from the bytes currently stored for [uri]. */
    private fun storedTag(): org.jaudiotagger.tag.Tag {
        val file = File(tmp.root, "readback-${System.nanoTime()}.wav").apply { writeBytes(store.files.getValue(uri)) }
        return AudioFileIO.read(file).tag
    }

    private fun write(request: TagWriteRequest) = runBlocking { writer.writeTags(request) }

    private fun seedWithTags() {
        store.files[uri] = wavBytes()
        val seeded = write(TagWriteRequest(uri, path, title = "Old Title", artist = "Original Artist", album = "Original Album", genre = "Jazz"))
        assertTrue("seeding failed: $seeded", seeded is AppResult.Success)
        rescans.clear()
    }

    @Test
    fun writesTagsThroughTheContentUri_andRescans() {
        store.files[uri] = wavBytes()

        val result = write(TagWriteRequest(uri, path, title = "New Title", artist = "New Artist"))

        assertTrue("$result", result is AppResult.Success)
        assertEquals("New Title", storedTag().getFirst(FieldKey.TITLE))
        assertEquals("New Artist", storedTag().getFirst(FieldKey.ARTIST))
        assertEquals(listOf(path), rescans)
    }

    @Test
    fun fieldsNotInTheRequest_keepTheirExistingValues() {
        seedWithTags()

        val result = write(TagWriteRequest(uri, path, title = "Only Title Changed"))

        assertTrue("$result", result is AppResult.Success)
        val tag = storedTag()
        assertEquals("Only Title Changed", tag.getFirst(FieldKey.TITLE))
        assertEquals("Original Artist", tag.getFirst(FieldKey.ARTIST))
        assertEquals("Original Album", tag.getFirst(FieldKey.ALBUM))
        assertEquals("Jazz", tag.getFirst(FieldKey.GENRE))
    }

    @Test
    fun deniedWriteAccess_isReportedAsRecoverable_andFileIsUntouched() {
        seedWithTags()
        val before = store.files.getValue(uri).copyOf()
        store.denyWrites = true

        val result = write(TagWriteRequest(uri, path, title = "Should Not Stick"))

        assertTrue("$result", (result as AppResult.Error).error is AppError.WriteAccessRequired)
        assertArrayEquals(before, store.files.getValue(uri))
        assertTrue(rescans.isEmpty())
    }

    @Test
    fun failureHalfwayThroughWriteBack_restoresTheOriginalFile() {
        seedWithTags()
        val before = store.files.getValue(uri).copyOf()
        store.failNextWriteAfterBytes = 20

        val result = write(TagWriteRequest(uri, path, title = "Interrupted"))

        assertTrue("$result", result is AppResult.Error)
        assertArrayEquals(before, store.files.getValue(uri))
        assertEquals("Old Title", storedTag().getFirst(FieldKey.TITLE))
    }

    @Test
    fun corruptFile_failsWithFormatError_andIsNotModified() {
        val garbage = ByteArray(4096) { (it % 251).toByte() }
        store.files[uri] = garbage.copyOf()

        val result = write(TagWriteRequest(uri, path, title = "x"))

        assertTrue("$result", (result as AppResult.Error).error is AppError.TagWriteError)
        assertArrayEquals(garbage, store.files.getValue(uri))
    }

    @Test
    fun missingFile_failsWithIoError() {
        val result = write(TagWriteRequest(uri, path, title = "x"))
        assertTrue("$result", (result as AppResult.Error).error is AppError.IoError)
    }

    @Test
    fun unknownFormat_failsWithoutTouchingTheFile() {
        store.files[uri] = wavBytes()
        val before = store.files.getValue(uri).copyOf()

        val result = write(TagWriteRequest(uri, "/storage/emulated/0/Download/no_extension", title = "x"))

        assertTrue("$result", (result as AppResult.Error).error is AppError.TagWriteError)
        assertArrayEquals(before, store.files.getValue(uri))
    }

    @Test
    fun scratchCopies_areAlwaysDeleted() {
        store.files[uri] = wavBytes()
        write(TagWriteRequest(uri, path, title = "ok"))
        store.denyWrites = true
        write(TagWriteRequest(uri, path, title = "denied"))

        assertTrue(workDir.listFiles().orEmpty().isEmpty())
    }
}
