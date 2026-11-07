package com.erhu.erhu.feature.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.erhu.erhu.core.common.result.AppError
import com.erhu.erhu.core.common.result.AppResult
import com.erhu.erhu.core.database.repository.ArtistArtworkRepository
import com.erhu.erhu.core.database.repository.LibraryRepository
import com.erhu.erhu.core.model.Album
import com.erhu.erhu.core.model.Artist
import com.erhu.erhu.core.model.Song
import com.erhu.erhu.core.network.artwork.AlbumArtworkCandidate
import com.erhu.erhu.core.network.artwork.AlbumArtworkResult
import com.erhu.erhu.core.network.artwork.ArtworkUpgradeImages
import com.erhu.erhu.core.network.artwork.ArtworkUpgradeMatch
import com.erhu.erhu.core.network.repository.ArtistArtworkSearchResponse
import com.erhu.erhu.core.network.repository.MetadataRepository
import com.erhu.erhu.core.network.tagger.TagWriteRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import java.io.ByteArrayOutputStream
import java.io.File
import java.lang.reflect.Proxy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], shadows = [UpgradeRetriever::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ArtworkUpgradeTest {
    @Test fun transparentArtworkKeepsItsTransparentBackground() {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val bytes = ByteArrayOutputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            bitmap.recycle()
            it.toByteArray()
        }
        val saved = ArtworkUpgradeImages.prepare(bytes)!!
        val decoded = BitmapFactory.decodeByteArray(saved, 0, saved.size)
        assertEquals(0, Color.alpha(decoded.getPixel(0, 0)))
        decoded.recycle()
    }

    @Test fun alreadyLargeImagesAvoidNetworkSearchAndUnreadableOriginalsAreReported() = runBlocking {
        Harness().use { h ->
            h.originalFile.writeBytes(image(1400))
            h.scan()
            assertEquals(0, h.searches)
            assertTrue(h.controller.state.value.results.isEmpty())
            h.originalFile.writeBytes("invalid".toByteArray())
            h.scan()
            assertEquals(1, h.controller.state.value.skipped)
            assertEquals(0, h.searches)
        }
    }

    @Test fun switchingKindsClearsPreviousMatchesAndPreviews() = runBlocking {
        Harness().use { h ->
            h.scan()
            val match = h.controller.state.value.results.single()
            h.controller.select(ArtworkUpgradeKind.ALBUMS)
            assertTrue(h.controller.state.value.results.isEmpty())
            assertFalse(match.file.exists())
            h.scan(ArtworkUpgradeKind.ALBUMS)
            assertNotNull(h.controller.state.value.results.single().target.album)
        }
    }

    @Test fun realImageDecodingRecognizesResolutionAndCompressionButRejectsDifferentImages() {
        val small = ArtworkUpgradeImages.inspect(image(150, quality = 75))!!
        val large = ArtworkUpgradeImages.inspect(image(1200))!!
        val different = ArtworkUpgradeImages.inspect(image(1200, moved = true))!!
        assertTrue(ArtworkUpgradeMatch.resembles(small.signature, large.signature))
        assertFalse(ArtworkUpgradeMatch.resembles(small.signature, different.signature))
        assertNull(ArtworkUpgradeImages.inspect("not an image".toByteArray()))
        assertNull(ArtworkUpgradeImages.prepare(ByteArray(ArtworkUpgradeImages.MAX_BYTES + 1)))
        val prepared = ArtworkUpgradeImages.inspect(ArtworkUpgradeImages.prepare(image(2000))!!)!!
        assertEquals(1440, prepared.width)
        assertEquals(1440, prepared.height)
        assertTrue(ArtworkUpgradeMatch.resembles(small.signature, prepared.signature))
        assertEquals(150, ArtworkUpgradeImages.inspect(ArtworkUpgradeImages.prepare(image(150))!!)!!.width)
    }

    @Test fun artistSearchOnlyOffersSameImageAndNeverSavesUntilExplicitApply() = runBlocking {
        Harness().use { h ->
            h.scan()
            assertEquals(1, h.controller.state.value.results.size)
            assertEquals(0, h.saves)
            val result = h.controller.state.value.results.single()
            assertEquals(1200, result.replacement.width)
            h.controller.apply(result)
            h.awaitSave()
            assertEquals(1, h.saves)
            assertTrue(h.controller.state.value.results.isEmpty())
            assertFalse(result.file.exists())
        }
    }

    @Test fun wrongArtworkAndMalformedResponsesAreNotOfferedAsUpgrades() = runBlocking {
        Harness().use { h ->
            h.candidate = image(1200, moved = true)
            h.scan()
            assertTrue(h.controller.state.value.results.isEmpty())
            h.candidate = "bad image".toByteArray()
            h.scan()
            assertTrue(h.controller.state.value.results.isEmpty())
            assertEquals(0, h.saves)
        }
    }

    @Test fun changedOriginalOrChangedCandidateCannotBeApplied() = runBlocking {
        Harness().use { h ->
            h.scan()
            val match = h.controller.state.value.results.single()
            h.originalFile.writeBytes(image(200, moved = true))
            h.controller.apply(match)
            h.awaitSave()
            assertEquals(0, h.saves)
            assertTrue(h.controller.state.value.message!!.contains("changed"))
            h.originalFile.writeBytes(h.original)
            match.file.writeBytes(image(1200, moved = true))
            h.controller.apply(match)
            h.awaitSave()
            assertEquals(0, h.saves)
        }
    }

    @Test fun providerFailureCanBeRetriedAndSaveFailureKeepsTheReview() = runBlocking {
        Harness().use { h ->
            h.failSearch = true
            h.scan()
            assertEquals(1, h.controller.state.value.skipped)
            h.failSearch = false
            h.scan()
            h.failSave = true
            h.controller.apply(h.controller.state.value.results.single())
            h.awaitSave()
            assertEquals(1, h.controller.state.value.results.size)
            assertNotNull(h.controller.state.value.message)
        }
    }

    @Test fun stopAndDuplicateSearchDoNotCreateLateMatchesOrDuplicateRequests() = runBlocking {
        Harness().use { h ->
            h.slowSearch = true
            h.controller.select(ArtworkUpgradeKind.ARTISTS)
            h.controller.search(ArtworkUpgradeKind.ARTISTS)
            h.controller.search(ArtworkUpgradeKind.ARTISTS)
            assertTrue(h.entered.await(5, TimeUnit.SECONDS))
            h.controller.stop()
            h.awaitScan()
            assertEquals(1, h.searches)
            assertTrue(h.controller.state.value.results.isEmpty())
            assertFalse(h.controller.state.value.finished)
        }
    }

    @Test fun albumConsentDenialMakesNoWritesAndApprovalOnlyChangesMatchingArtwork() = runBlocking {
        Harness().use { h ->
            h.scan(ArtworkUpgradeKind.ALBUMS)
            val match = h.controller.state.value.results.single()
            h.allowWrite = false
            h.controller.apply(match)
            h.awaitSave()
            assertTrue(h.writes.isEmpty())
            h.allowWrite = true
            h.controller.apply(match)
            h.awaitSave()
            assertEquals(2, h.consents)
            assertEquals(1, h.writes.size)
            val write = h.writes.single()
            assertEquals(h.songs.first().uri, write.contentUri)
            assertNotNull(write.artworkBytes)
            assertNull(write.title)
            assertNull(write.artist)
            assertNull(write.album)
            assertFalse(write.clearArtwork)
            assertTrue(h.controller.state.value.message!!.contains("1 of 2"))
        }
    }

    private inner class Harness : AutoCloseable {
        val context: Context = RuntimeEnvironment.getApplication()
        val original = image(200)
        val originalFile = File.createTempFile("original", ".jpg", context.cacheDir).apply { writeBytes(original) }
        val artist = Artist(1, "Example", 1, 1, Uri.fromFile(originalFile).toString())
        val album = Album(1, "Example album", "Example", 1, 2, 2024)
        val songs = (1L..2L).map { Song(it, "Track $it", "Example", "Example album", 1000, "content://songs/$it", filePath = "/music/$it.mp3") }
        var candidate = image(1200)
        var searches = 0
        var saves = 0
        var failSearch = false
        var failSave = false
        var slowSearch = false
        var allowWrite = true
        var consents = 0
        val entered = CountDownLatch(1)
        val writes = mutableListOf<TagWriteRequest>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val controller: ArtworkUpgradeController

        init {
            UpgradeRetriever.pictures[songs[0].uri] = original
            UpgradeRetriever.pictures[songs[1].uri] = image(200, moved = true)
            val library = proxy<LibraryRepository> { name, _ ->
                when (name) {
                    "getAllArtists" -> flowOf(listOf(artist))
                    "getArtistById" -> flowOf(artist)
                    "getAllAlbums" -> flowOf(listOf(album))
                    "getSongsByAlbumId" -> flowOf(songs)
                    "requestRescanAfterChange" -> AppResult.Success(Unit)
                    else -> error(name)
                }
            }
            val metadata = proxy<MetadataRepository> { name, args ->
                when (name) {
                    "fetchArtistArtworkCandidatesDetailed", "searchAlbumArtwork" -> {
                        searches++
                        entered.countDown()
                        if (slowSearch) Thread.sleep(500)
                        if (failSearch) AppResult.Error(AppError.Unknown())
                        else if (name == "searchAlbumArtwork") AppResult.Success(AlbumArtworkResult(listOf(
                            AlbumArtworkCandidate("https://example.test/art", "Test catalogue", album.title, album.artist, width = 1200, height = 1200, verifiedImage = true),
                        ), emptyList(), 0))
                        else AppResult.Success(ArtistArtworkSearchResponse(listOf("https://example.test/art")))
                    }
                    "downloadArtwork" -> AppResult.Success(candidate)
                    "writeTagsToFile" -> { writes += args[0] as TagWriteRequest; AppResult.Success(Unit) }
                    else -> error(name)
                }
            }
            val artists = proxy<ArtistArtworkRepository> { name, _ ->
                check(name == "setCustomArtwork")
                if (failSave) AppResult.Error(AppError.Unknown()) else { saves++; AppResult.Success(Unit) }
            }
            controller = ArtworkUpgradeController(context, scope, library, metadata, artists) { _, granted, denied ->
                consents++
                if (allowWrite) granted() else denied()
            }
        }

        suspend fun scan(kind: ArtworkUpgradeKind = ArtworkUpgradeKind.ARTISTS) {
            controller.select(kind)
            controller.search(kind)
            awaitScan()
        }

        suspend fun awaitScan() { withTimeout(10000) { controller.state.first { !it.scanning } } }
        suspend fun awaitSave() { withTimeout(10000) { controller.state.first { !it.saving } } }
        override fun close() { scope.cancel(); originalFile.delete(); UpgradeRetriever.pictures.clear() }
    }

    private inline fun <reified T> proxy(crossinline call: (String, Array<out Any?>) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args -> call(method.name, args.orEmpty()) } as T

    private fun image(size: Int, moved: Boolean = false, quality: Int = 94): ByteArray {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.BLUE)
        canvas.drawRect(size * (if (moved) 0.6f else 0.1f), size * 0.2f, size * (if (moved) 0.85f else 0.35f), size * 0.8f, Paint().apply { color = 0xffff9900.toInt() })
        return ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it); bitmap.recycle(); it.toByteArray() }
    }
}

/** Test fixture only: controls embedded bytes without touching real music files. */
@Implements(MediaMetadataRetriever::class)
class UpgradeRetriever {
    private var uri: String = ""
    @Implementation fun setDataSource(context: Context, value: Uri) { uri = value.toString() }
    @Implementation fun getEmbeddedPicture(): ByteArray? = pictures[uri]
    @Implementation fun release() = Unit
    companion object { val pictures = ConcurrentHashMap<String, ByteArray>() }
}
