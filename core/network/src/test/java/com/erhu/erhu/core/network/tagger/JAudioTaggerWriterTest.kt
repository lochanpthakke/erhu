package com.erhu.erhu.core.network.tagger

import com.erhu.erhu.core.common.logging.Logger
import com.erhu.erhu.core.common.result.AppError
import com.erhu.erhu.core.common.result.AppResult
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
    fun genreOnlyFix_writesTheGenreIntoTheFile_andKeepsEveryOtherTag() {
        seedWithTags()
        val match =
            com.erhu.erhu.core.model
                .MetadataCandidate("mb_1", "Some Other Title", "Some Other Artist", "Some Other Album", sourceName = "MusicBrainz")
        val local = LocalTrackTags("Old Title", "Original Artist", "Original Album", genre = "Jazz")
        val plan =
            planFieldFix(uri, path, com.erhu.erhu.core.model.MetadataFixAction.GENRE_ONLY, local, match, "hip hop", null)

        val result = write(plan.request!!)

        assertTrue("$result", result is AppResult.Success)
        val tag = storedTag()
        assertEquals("hip hop", tag.getFirst(FieldKey.GENRE))
        assertEquals("Old Title", tag.getFirst(FieldKey.TITLE))
        assertEquals("Original Artist", tag.getFirst(FieldKey.ARTIST))
        assertEquals("Original Album", tag.getFirst(FieldKey.ALBUM))
        assertEquals(listOf(path), rescans)
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
    fun candidateRejectsMp4TagWritesWithoutChangingStoredMedia() {
        assertTrue(com.erhu.erhu.core.network.BuildConfig.PROPRIETARY_CANDIDATE)
        store.files[uri] = wavBytes()
        val before = store.files.getValue(uri).copyOf()
        listOf("mp4", "m4a", "m4p", "m4b").forEach { extension ->
            val result = write(TagWriteRequest(uri, "/Music/test.$extension", title = "x"))
            assertTrue("$result", (result as AppResult.Error).error is AppError.TagWriteError)
            assertArrayEquals(before, store.files.getValue(uri))
        }
    }

    @Test
    fun scratchCopies_areAlwaysDeleted() {
        store.files[uri] = wavBytes()
        write(TagWriteRequest(uri, path, title = "ok"))
        store.denyWrites = true
        write(TagWriteRequest(uri, path, title = "denied"))

        assertTrue(workDir.listFiles().orEmpty().isEmpty())
    }

    // ---- year / release date

    @Test
    fun releaseDate_replacesTheYear_withFullPrecision_andKeepsOtherTags() {
        seedWithTags()
        assertTrue(write(TagWriteRequest(uri, path, year = 1970)) is AppResult.Success)

        val result = write(TagWriteRequest(uri, path, releaseDate = "2010-11-19"))

        assertTrue("$result", result is AppResult.Success)
        val tag = storedTag()
        assertEquals("2010-11-19", tag.getFirst(FieldKey.YEAR))
        assertEquals("Old Title", tag.getFirst(FieldKey.TITLE))
        assertEquals("Original Artist", tag.getFirst(FieldKey.ARTIST))
        assertEquals("Original Album", tag.getFirst(FieldKey.ALBUM))
        assertEquals("Jazz", tag.getFirst(FieldKey.GENRE))
    }

    @Test
    fun aLaterYearOnlyWrite_leavesNoPartOfTheOldDate() {
        seedWithTags()
        write(TagWriteRequest(uri, path, releaseDate = "2010-11-19"))

        assertTrue(write(TagWriteRequest(uri, path, releaseDate = "2005")) is AppResult.Success)

        assertEquals("2005", storedTag().getFirst(FieldKey.YEAR))
    }

    @Test
    fun deniedWriteAccess_forAYearFix_isReported_andTheFileIsUntouched() {
        seedWithTags()
        val before = store.files.getValue(uri).copyOf()
        store.denyWrites = true

        val result = write(TagWriteRequest(uri, path, releaseDate = "2010-11-19"))

        assertTrue("$result", (result as AppResult.Error).error is AppError.WriteAccessRequired)
        assertArrayEquals(before, store.files.getValue(uri))
        assertTrue(rescans.isEmpty())
    }

    // An MP3 with an ID3v2.3 tag (jaudiotagger's default, and what the library's files carry).

    private val mp3Uri = "content://media/external/audio/media/43"
    private val mp3Path = "/storage/emulated/0/Music/test.mp3"

    /** Silent MPEG-1 Layer III frames (128 kbit/s, 44.1 kHz): enough audio for jaudiotagger to read. */
    private fun mp3Bytes(): ByteArray {
        val frameLength = 417
        val frames = 40
        return ByteArray(frameLength * frames).also { bytes ->
            for (f in 0 until frames) {
                val at = f * frameLength
                bytes[at] = 0xFF.toByte()
                bytes[at + 1] = 0xFB.toByte()
                bytes[at + 2] = 0x90.toByte()
                bytes[at + 3] = 0x64.toByte()
            }
        }
    }

    private fun storedMp3Tag(): org.jaudiotagger.tag.Tag {
        val file = File(tmp.root, "readback-${System.nanoTime()}.mp3").apply { writeBytes(store.files.getValue(mp3Uri)) }
        return AudioFileIO.read(file).tag
    }

    private fun seedMp3With1970() {
        store.files[mp3Uri] = mp3Bytes()
        val seeded = write(TagWriteRequest(mp3Uri, mp3Path, title = "Runaway", artist = "Kanye West", genre = "Rap", year = 1970))
        assertTrue("seeding failed: $seeded", seeded is AppResult.Success)
        assertTrue(storedMp3Tag() is org.jaudiotagger.tag.id3.ID3v23Tag)
        assertEquals("1970", storedMp3Tag().getFirst(FieldKey.YEAR))
    }

    /** jaudiotagger alone read "2010-11-19" back as "1970-11-19" here: the old year frame survived. */
    @Test
    fun id3v23_fullDateReplacesThe1970Year() {
        seedMp3With1970()

        val result = write(TagWriteRequest(mp3Uri, mp3Path, releaseDate = "2010-11-19"))

        assertTrue("$result", result is AppResult.Success)
        val tag = storedMp3Tag()
        assertEquals("2010-11-19", tag.getFirst(FieldKey.YEAR))
        assertEquals("Runaway", tag.getFirst(FieldKey.TITLE))
        assertEquals("Kanye West", tag.getFirst(FieldKey.ARTIST))
        assertEquals("Rap", tag.getFirst(FieldKey.GENRE))
    }

    /** ID3v2.3 would store "2010-11" as "2010-11-01", a day nobody gave: the write is refused instead. */
    @Test
    fun id3v23_yearMonthIsRefused_notStoredWithAnInventedDay() {
        seedMp3With1970()
        val before = store.files.getValue(mp3Uri).copyOf()

        val result = write(TagWriteRequest(mp3Uri, mp3Path, releaseDate = "2010-11"))

        assertTrue("$result", (result as AppResult.Error).error is AppError.TagWriteError)
        assertArrayEquals(before, store.files.getValue(mp3Uri))
    }

    // ---- Unicode text survives a write (the corruption seen on the phone is not from this path)

    @Test
    fun id3v23_unicodeTitleRoundTrips_andOtherTagsAreKept() {
        seedMp3With1970()
        val titles = listOf("IGOR\u2019S THEME", "Beyonc\u00e9 \u2014 D\u00e9j\u00e0 Vu", "\u6771\u4eac \ud83c\udfb5", "\u0416\u0438\u0437\u043d\u044c")
        for (title in titles) {
            val result = write(TagWriteRequest(mp3Uri, mp3Path, title = title))
            assertTrue("$title: $result", result is AppResult.Success)
            val tag = storedMp3Tag()
            assertEquals(title, tag.getFirst(FieldKey.TITLE))
            assertEquals("Kanye West", tag.getFirst(FieldKey.ARTIST))
            assertEquals("Rap", tag.getFirst(FieldKey.GENRE))
            assertEquals("1970", tag.getFirst(FieldKey.YEAR))
        }
    }

    // ---- Several genres and a BPM, per tag format: real files written by the writer, read back

    private val severalGenres = listOf("Electronic", "Synth-pop", "Alternative")

    /** A minimal FLAC (STREAMINFO: 44.1 kHz, 16-bit mono, 2 s) followed by a frame header and silence. */
    private fun flacBytes(): ByteArray {
        val buffer = ByteBuffer.allocate(4 + 4 + 34 + 2000)
        buffer.put("fLaC".toByteArray())
        buffer.put(byteArrayOf(0x80.toByte(), 0, 0, 34))
        buffer.putShort(4096).putShort(4096).put(ByteArray(6))
        buffer.putLong((44_100L shl 44) or (0L shl 41) or (15L shl 36) or 88_200L).put(ByteArray(16))
        buffer.put(byteArrayOf(0xFF.toByte(), 0xF8.toByte())).put(ByteArray(1998))
        return buffer.array()
    }

    private fun mp3WithId3v24(): ByteArray {
        val file = File(tmp.root, "v24-${System.nanoTime()}.mp3").apply { writeBytes(mp3Bytes()) }
        val audio = AudioFileIO.read(file)
        audio.tag = org.jaudiotagger.tag.id3.ID3v24Tag().apply { setField(FieldKey.TITLE, "Seed") }
        AudioFileIO.write(audio)
        return file.readBytes()
    }

    /** Writes [genre] and [bpm] into a [label] file through the writer; the file as read back. */
    private fun roundTrip(
        label: String,
        bytes: ByteArray,
        genre: String,
        bpm: Int? = null,
    ): File {
        val extension = label.substringAfterLast('.')
        val formatUri = "content://media/external/audio/media/$label"
        store.files[formatUri] = bytes
        val result = write(TagWriteRequest(formatUri, "/storage/emulated/0/Music/$label", genre = genre, bpm = bpm))
        assertTrue("$label: $result", result is AppResult.Success)
        return File(tmp.root, "readback-${System.nanoTime()}.$extension").apply { writeBytes(store.files.getValue(formatUri)) }
    }

    @Test
    fun severalGenres_andBpm_surviveEveryFormat_inItsOwnRepresentation() {
        val joined = "Electronic; Synth-pop; Alternative"
        val formats =
            listOf(
                Triple("id3v23.mp3", mp3Bytes(), false),
                Triple("id3v24.mp3", mp3WithId3v24(), true),
                Triple("test.wav", wavBytes(), false),
                Triple("test.flac", flacBytes(), true),
            )
        for ((label, bytes, separateValues) in formats) {
            val file = roundTrip(label, bytes, joined, bpm = 128)
            val tag = AudioFileIO.read(file).tag
            assertEquals(label, severalGenres, tag.readGenres())
            assertEquals(label, "128", tag.getFirst(FieldKey.BPM))
            if (separateValues) {
                // Separate values other players read as three genres.
                assertEquals(label, severalGenres, tag.getAll(FieldKey.GENRE))
            } else {
                // One value, as the format allows: never jaudiotagger's corrupted multi-value ID3v2.3.
                assertEquals(label, listOf(joined), tag.getAll(FieldKey.GENRE))
            }
            // What the app reads back (editor, repair planning) is the whole list.
            val embedded = runBlocking { EmbeddedTagReader(SilentLogger).readEmbeddedTags(file.path) }
            assertEquals(label, joined, embedded.genre)
            assertEquals(label, "128", embedded.bpm)
        }
    }

    @Test
    fun aSingleGenre_isStillWrittenAsBefore_andReadByName() {
        for ((label, bytes) in listOf("one.mp3" to mp3Bytes(), "one.flac" to flacBytes(), "one.wav" to wavBytes())) {
            val tag = AudioFileIO.read(roundTrip(label, bytes, "Rock")).tag
            assertEquals(label, listOf("Rock"), tag.readGenres())
            assertEquals(label, "Rock", tag.getFirst(FieldKey.GENRE))
        }
    }

    @Test
    fun replacingGenres_dropsTheOldOnes() {
        val file = roundTrip("again.flac", flacBytes(), "Jazz; Blues; Soul")
        val formatUri = "content://media/external/audio/media/again.flac"
        store.files[formatUri] = file.readBytes()
        assertTrue(write(TagWriteRequest(formatUri, "/storage/emulated/0/Music/again.flac", genre = "Hip Hop/Rap")) is AppResult.Success)
        val readBack = File(tmp.root, "again-${System.nanoTime()}.flac").apply { writeBytes(store.files.getValue(formatUri)) }
        // "/" is part of a genre name (Deezer's "Hip Hop/Rap"), never a separator.
        assertEquals(listOf("Hip Hop/Rap"), AudioFileIO.read(readBack).tag.readGenres())
    }

    @Test
    fun id3GenreReferences_andOtherTaggersLists_areReadAsNames() {
        val file = File(tmp.root, "refs.mp3").apply { writeBytes(mp3WithId3v24()) }
        val audio = AudioFileIO.read(file)
        audio.tag.deleteField(FieldKey.GENRE)
        audio.tag.addField(FieldKey.GENRE, "17")
        audio.tag.addField(FieldKey.GENRE, "Synth-pop")
        AudioFileIO.write(audio)
        assertEquals(listOf("Rock", "Synth-pop"), AudioFileIO.read(file).tag.readGenres())
    }
}
