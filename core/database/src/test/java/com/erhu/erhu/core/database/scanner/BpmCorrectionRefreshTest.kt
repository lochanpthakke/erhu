package com.erhu.erhu.core.database.scanner

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.erhu.erhu.core.common.logging.Logger
import com.erhu.erhu.core.common.result.AppResult
import com.erhu.erhu.core.database.ErhuDatabase
import com.erhu.erhu.core.database.entity.SongEntity
import com.erhu.erhu.core.database.mapper.toDomainModel
import com.erhu.erhu.core.model.Genres
import com.erhu.erhu.core.network.tagger.AudioContentStore
import com.erhu.erhu.core.network.tagger.BpmCorrection
import com.erhu.erhu.core.network.tagger.JAudioTaggerWriter
import com.erhu.erhu.core.network.tagger.TagWriteRequest
import kotlinx.coroutines.runBlocking
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * The half-time correction end to end, on a real MP3 and the real library database: only the BPM
 * tag changes in the file, the file reads back with it, and the library (re-enriched after the
 * rescan, as the app does) holds the new BPM and the untouched genres.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class BpmCorrectionRefreshTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val db =
        Room
            .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ErhuDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    private val songs = db.songDao()

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

    /** Content URIs backed by real files, standing in for MediaStore. */
    private class FileStore(
        private val file: File,
    ) : AudioContentStore {
        override fun openInput(contentUri: String): InputStream = file.inputStream()

        override fun openOverwrite(contentUri: String): OutputStream = file.outputStream()
    }

    @After
    fun close() = db.close()

    /** EARFQUAKE as AcousticBrainz tagged it: double time (156), with its genres. */
    private fun earfquake(): File {
        val file = File(tmp.root, "EARFQUAKE.mp3")
        file.writeBytes(
            ByteArray(417 * 40).also { bytes ->
                for (frame in 0 until 40) {
                    val at = frame * 417
                    bytes[at] = 0xFF.toByte()
                    bytes[at + 1] = 0xFB.toByte()
                    bytes[at + 2] = 0x90.toByte()
                    bytes[at + 3] = 0x64.toByte()
                }
            },
        )
        val audio = AudioFileIO.read(file)
        val tag = audio.tagOrCreateAndSetDefault
        tag.setField(FieldKey.TITLE, "EARFQUAKE")
        tag.setField(FieldKey.ALBUM, "IGOR")
        tag.setField(FieldKey.GENRE, "hip hop; neo soul")
        tag.setField(FieldKey.BPM, "156")
        audio.tag = tag
        AudioFileIO.write(audio)
        return file
    }

    private fun entity(
        file: File,
        dateModified: Long,
    ) = SongEntity(
        id = 1,
        title = "EARFQUAKE",
        artist = "Tyler, The Creator",
        album = "IGOR",
        artistId = 2,
        albumId = 3,
        durationMs = 190_000,
        uri = "content://media/external/audio/media/1",
        filePath = file.path,
        trackNumber = 2,
        year = 2019,
        dateModified = dateModified,
    )

    @Test
    fun halfTimeCorrection_reachesTheFileAndTheLibrary_andNothingElseChanges() =
        runBlocking {
            val file = earfquake()
            val enricher = JAudioTaggerEnricher(songs, SilentLogger)
            songs.upsertAll(listOf(entity(file, dateModified = 1)))
            enricher.enrichUnenrichedSongs()
            assertEquals(156, songs.getSongById(1)?.bpm)

            // What the inspector offers and the user confirms: half time, 156 -> 78.
            val corrected = BpmCorrection.HALF_TIME.applyTo(156)!!
            assertEquals(78, corrected)
            val rescans = mutableListOf<String>()
            val writer = JAudioTaggerWriter(FileStore(file), { tmp.newFolder() }, { path, _ -> rescans += path }, SilentLogger)
            val result = writer.writeTags(TagWriteRequest("content://media/external/audio/media/1", file.path, bpm = corrected))
            assertTrue("$result", result is AppResult.Success)
            assertEquals(listOf(file.path), rescans)

            // The file: new BPM, every other tag as it was.
            val tag = AudioFileIO.read(file).tag
            assertEquals("78", tag.getFirst(FieldKey.BPM))
            assertEquals("EARFQUAKE", tag.getFirst(FieldKey.TITLE))
            assertEquals("IGOR", tag.getFirst(FieldKey.ALBUM))
            assertEquals("hip hop; neo soul", tag.getFirst(FieldKey.GENRE))

            // The library: updated at once, then re-read from the file after the rescan.
            songs.updateBpm(1, corrected)
            assertEquals(78, songs.getSongById(1)?.toDomainModel()?.bpm)
            val existing = songs.getSongById(1)!!
            val plan = planLibrarySync(scanned = listOf(entity(file, dateModified = 2)), existing = listOf(existing))
            songs.upsertAll(plan.upserts)
            enricher.enrichUnenrichedSongs()
            val refreshed = songs.getSongById(1)!!
            assertEquals(78, refreshed.bpm)
            assertEquals(listOf("hip hop", "neo soul"), Genres.parse(refreshed.genre))
            assertEquals(78, refreshed.toDomainModel().bpm)
        }
}
