package com.erhu.erhu

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import com.erhu.erhu.core.common.logging.Logger
import com.erhu.erhu.core.common.result.AppResult
import com.erhu.erhu.core.network.tagger.ContentResolverAudioContentStore
import com.erhu.erhu.core.network.tagger.JAudioTaggerWriter
import com.erhu.erhu.core.network.tagger.MediaRescanner
import com.erhu.erhu.core.network.tagger.TagEditWorkDir
import com.erhu.erhu.core.network.tagger.TagWriteRequest
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Writes only generated audio inside the isolated test app's cache; never touches the user's music. */
class KeylessMetadataArtworkTest {
    private object SilentLogger : Logger {
        override fun v(tag: String, message: String, throwable: Throwable?) = Unit
        override fun d(tag: String, message: String, throwable: Throwable?) = Unit
        override fun i(tag: String, message: String, throwable: Throwable?) = Unit
        override fun w(tag: String, message: String, throwable: Throwable?) = Unit
        override fun e(tag: String, message: String, throwable: Throwable?) = Unit
    }

    @Test fun localCoverReplacementPreservesManualTags() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue("Use -PbenchApp", context.packageName.endsWith(".bench"))
        val directory = File(context.cacheDir, "keyless-cover-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val audio = File(directory, "fixture.wav")
            val dataSize = 16000
            val bytes = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
                .put("RIFF".toByteArray()).putInt(36 + dataSize).put("WAVE".toByteArray())
                .put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
                .putInt(8000).putInt(16000).putShort(2).putShort(16)
                .put("data".toByteArray()).putInt(dataSize).array()
            audio.writeBytes(bytes)
            val uri = android.net.Uri.fromFile(audio).toString()
            val writer = JAudioTaggerWriter(
                ContentResolverAudioContentStore(context),
                TagEditWorkDir { File(directory, "work") },
                MediaRescanner { _, _ -> },
                SilentLogger,
            )
            assertTrue(writer.writeTags(TagWriteRequest(uri, audio.path, title = "Manual title", artist = "Manual artist")) is AppResult.Success)
            var previousCover = byteArrayOf()
            for (color in listOf(Color.RED, Color.BLUE)) {
                val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
                val cover = ByteArrayOutputStream().use { output -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, output); output.toByteArray() }
                bitmap.recycle()
                val result = writer.writeTags(TagWriteRequest(uri, audio.path, artworkBytes = cover))
                assertTrue("$result", result is AppResult.Success)
                val tag = AudioFileIO.read(audio).tag
                assertEquals("Manual title", tag.getFirst(FieldKey.TITLE))
                assertEquals("Manual artist", tag.getFirst(FieldKey.ARTIST))
                val embedded = tag.firstArtwork.binaryData
                val decoded = requireNotNull(BitmapFactory.decodeByteArray(embedded, 0, embedded.size))
                assertEquals(8, decoded.width)
                assertEquals(8, decoded.height)
                decoded.recycle()
                assertFalse(previousCover.contentEquals(embedded))
                previousCover = embedded
            }
            val cache = com.erhu.erhu.core.network.artwork.AlbumArtworkCache(File(directory, "artwork-cache"))
            val finder = com.erhu.erhu.core.network.artwork.AlbumArtworkFinder(
                okhttp3.OkHttpClient(), kotlinx.serialization.json.Json { ignoreUnknownKeys = true }, cache,
                com.erhu.erhu.core.network.artwork.AndroidArtworkImageInspector(),
            )
            val beforeSearch = audio.readBytes()
            val query = com.erhu.erhu.core.network.artwork.AlbumArtworkQuery("SOS", "SZA", 2022, com.erhu.erhu.core.network.artwork.ArtworkReleaseKind.ALBUM)
            val found = finder.search(query)
            assertTrue("Search must preserve the custom cover and tags", beforeSearch.contentEquals(audio.readBytes()))
            assertTrue("Live artwork unavailable: ${found.outcomes}", found.candidates.isNotEmpty())
            val selected = found.recommended ?: found.candidates.first()
            val artwork = requireNotNull(cache.bytes(selected.imageUrl))
            assertTrue(writer.writeTags(TagWriteRequest(uri, audio.path, artworkBytes = artwork)) is AppResult.Success)
            val tag = AudioFileIO.read(audio).tag
            assertEquals("Manual title", tag.getFirst(FieldKey.TITLE))
            assertEquals("Manual artist", tag.getFirst(FieldKey.ARTIST))
            val savedFacts = requireNotNull(com.erhu.erhu.core.network.artwork.AndroidArtworkImageInspector().inspect(tag.firstArtwork.binaryData))
            val sourceFacts = requireNotNull(com.erhu.erhu.core.network.artwork.AndroidArtworkImageInspector().inspect(artwork))
            val savedHash = java.lang.Long.parseUnsignedLong(savedFacts.visualHash.substringBefore(":"), 16)
            val sourceHash = java.lang.Long.parseUnsignedLong(sourceFacts.visualHash.substringBefore(":"), 16)
            assertTrue("Saved cover must preserve the selected image after the writer's existing JPEG conversion", java.lang.Long.bitCount(savedHash xor sourceHash) <= 8)
            assertTrue(savedFacts.width > 0 && savedFacts.height > 0)
            val offlineClient = okhttp3.OkHttpClient.Builder().addInterceptor { throw java.io.IOException("Offline test") }.build()
            val offline = com.erhu.erhu.core.network.artwork.AlbumArtworkFinder(
                offlineClient, kotlinx.serialization.json.Json { ignoreUnknownKeys = true },
                com.erhu.erhu.core.network.artwork.AlbumArtworkCache(File(directory, "artwork-cache")),
                com.erhu.erhu.core.network.artwork.AndroidArtworkImageInspector(),
            ).search(query)
            assertTrue(offline.fromCache)
            assertEquals(found.candidates, offline.candidates)
        } finally {
            directory.deleteRecursively()
        }
    }
}
