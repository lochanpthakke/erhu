package com.lochanpthakke.empee3.core.database.repository

import androidx.datastore.core.DataStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lochanpthakke.empee3.core.common.dispatcher.DispatcherProvider
import com.lochanpthakke.empee3.core.common.result.AppError
import com.lochanpthakke.empee3.core.common.result.AppResult
import com.lochanpthakke.empee3.core.database.EmPee3Database
import com.lochanpthakke.empee3.core.database.entity.AnimatedArtworkEntity
import com.lochanpthakke.empee3.core.datastore.proto.PlaybackStateProto
import com.lochanpthakke.empee3.core.model.AnimatedArtOrientation
import com.lochanpthakke.empee3.core.model.AnimatedArtStatus
import com.lochanpthakke.empee3.core.network.apple.AnimatedArtworkNetworkDataSource
import com.lochanpthakke.empee3.core.network.apple.EditorialVideoUrls
import com.lochanpthakke.empee3.core.network.apple.HlsVariantStream
import com.lochanpthakke.empee3.core.network.apple.ResolveOutcome
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class LiveCoverCheckTest {
    private val db =
        Room
            .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), EmPee3Database::class.java)
            .allowMainThreadQueries()
            .build()
    private val dao = db.animatedArtworkDao()
    private val fileStore = ArtworkFileStore(ApplicationProvider.getApplicationContext())
    private val network = FakeNetwork()

    private fun repository() = AnimatedArtworkRepositoryImpl(dao, network, fileStore, FakeSettings(), TestDispatchers)

    private val repository = repository()

    @After
    fun close() {
        db.close()
        fileStore.deleteAll()
    }

    private fun check(albumId: Long = ALBUM_ID) =
        runBlocking { repository.checkLiveCover(albumId, ARTIST, ALBUM, TRACKS) }

    private fun status(albumId: Long = ALBUM_ID) = runBlocking { dao.getByAlbumId(albumId)?.status }

    @Test
    fun `an album Apple has no match for shows nothing, and is not asked about again`() {
        network.resolve = AppResult.Success(ResolveOutcome.NoMatch)

        assertEquals(AppResult.Success(LiveCoverCheck.None), check())
        assertEquals(AnimatedArtStatus.NO_MATCH.name, status())
        assertEquals(AppResult.Success(LiveCoverCheck.None), check())
        assertEquals(1, network.resolveCalls)
    }

    @Test
    fun `an album without a motion cover shows nothing, and is not asked about again`() {
        network.resolve = AppResult.Success(ResolveOutcome.Matched("123", "us", null, null))

        assertEquals(AppResult.Success(LiveCoverCheck.None), check())
        assertEquals(AnimatedArtStatus.NO_ANIMATION.name, status())
        check()
        assertEquals(1, network.resolveCalls)
    }

    @Test
    fun `a motion cover is offered with its phone-sized resolution and exact size, without downloading it`() {
        network.resolve = AppResult.Success(ResolveOutcome.Matched("123", "us", SQUARE_MASTER, TALL_MASTER))

        val result = (check() as AppResult.Success).data as LiveCoverCheck.Downloadable

        // The default preference (1080p) picks the phone-sized variant; every variant is offered.
        assertEquals(AnimatedArtOrientation.SQUARE_1_1, result.orientation)
        assertEquals(1080, result.width)
        assertEquals(1080, result.height)
        assertEquals(EXACT_BYTES, result.bytes)
        assertEquals(SQUARE_1080, result.selectedVariant?.variantUrl)
        assertEquals(listOf(2048, 1080, 720), result.variants.map { it.width })
        assertEquals(AnimatedArtStatus.FOUND.name, status())
        assertEquals("123", runBlocking { dao.getByAlbumId(ALBUM_ID)?.catalogAlbumId })
        assertEquals(0, network.downloads)
        // Sizes are read from the playlists' byte ranges, never by downloading.
        assertTrue(SQUARE_1080 in network.sizedVariants)
    }

    @Test
    fun `a size the playlist does not state is left unknown, not guessed`() {
        network.resolve = AppResult.Success(ResolveOutcome.Matched("123", "us", SQUARE_MASTER, null))
        network.variantBytes = null

        val result = check() as AppResult.Success

        assertNull((result.data as LiveCoverCheck.Downloadable).bytes)
    }

    @Test
    fun `a cover found earlier is checked again without searching for the album`() {
        network.resolve = AppResult.Success(ResolveOutcome.Matched("123", "us", SQUARE_MASTER, null))
        check()

        val later = repository()
        val result = runBlocking { later.checkLiveCover(ALBUM_ID, ARTIST, ALBUM, TRACKS) }

        assertTrue((result as AppResult.Success).data is LiveCoverCheck.Downloadable)
        assertEquals(1, network.resolveCalls)
        assertEquals(1, network.masterCalls)
    }

    @Test
    fun `a saved cover is reported as downloaded with its real size and no network use`() {
        val file = fileStore.getSquareFile(ALBUM_ID, ALBUM, ARTIST).apply { parentFile?.mkdirs() }
        file.writeBytes(ByteArray(4096))
        runBlocking { dao.upsert(entity(status = AnimatedArtStatus.AVAILABLE, squareFileName = file.name)) }

        assertEquals(
            AppResult.Success(LiveCoverCheck.Downloaded(AnimatedArtOrientation.SQUARE_1_1, file.absolutePath, 4096)),
            check(),
        )
        assertEquals(0, network.resolveCalls)
    }

    @Test
    fun `another album's cover is never taken for this one`() {
        val other = fileStore.getSquareFile(OTHER_ALBUM_ID, ALBUM, ARTIST).apply { parentFile?.mkdirs() }
        other.writeBytes(ByteArray(10))
        runBlocking {
            dao.upsert(entity(albumId = OTHER_ALBUM_ID, status = AnimatedArtStatus.AVAILABLE, squareFileName = other.name, artist = "x"))
        }
        network.resolve = AppResult.Success(ResolveOutcome.NoMatch)

        assertEquals(AppResult.Success(LiveCoverCheck.None), check())
        assertEquals(1, network.resolveCalls)
    }

    @Test
    fun `a network failure is reported and not remembered`() {
        network.resolve = AppResult.Error(AppError.NetworkError("offline"))

        assertTrue(check() is AppResult.Error)
        assertNull(status())
    }

    @Test
    fun `repeated download requests share one download and one file`() {
        network.resolve = AppResult.Success(ResolveOutcome.Matched("123", "us", SQUARE_MASTER, null))
        check()
        network.downloadGate = CompletableDeferred()

        val results =
            runBlocking {
                val all =
                    List(3) {
                        async(Dispatchers.Default) {
                            repository.fetchAndStoreArtwork(ALBUM_ID, ARTIST, ALBUM, TRACKS, AnimatedArtOrientation.SQUARE_1_1)
                        }
                    }
                withTimeout(5_000) { network.downloadStarted.await() }
                network.downloadGate!!.complete(Unit)
                all.awaitAll()
            }

        assertEquals(1, network.downloads)
        assertTrue(results.all { it is AppResult.Success })
        assertEquals(listOf("Sos_Sza_AnimatedArtwork_square.mp4".lowercase()), fileStore.baseDir.list()!!.map { it.lowercase() })
        assertEquals(AnimatedArtStatus.AVAILABLE.name, status())
    }

    @Test
    fun `a cancelled download leaves no file and the cover offered again`() {
        network.resolve = AppResult.Success(ResolveOutcome.Matched("123", "us", SQUARE_MASTER, null))
        check()
        network.downloadGate = CompletableDeferred()

        runBlocking {
            val download =
                async(Dispatchers.Default) {
                    repository.fetchAndStoreArtwork(ALBUM_ID, ARTIST, ALBUM, TRACKS, AnimatedArtOrientation.SQUARE_1_1)
                }
            withTimeout(5_000) { network.downloadStarted.await() }
            repository.cancelDownload(ALBUM_ID)
            runCatching { download.await() }
        }

        assertEquals(AnimatedArtStatus.FOUND.name, status())
        assertEquals(emptyList<String>(), fileStore.baseDir.list()?.toList().orEmpty())
        assertTrue((check() as AppResult.Success).data is LiveCoverCheck.Downloadable)
    }

    @Test
    fun `removing a downloaded cover deletes its file`() {
        network.resolve = AppResult.Success(ResolveOutcome.Matched("123", "us", SQUARE_MASTER, null))
        check()
        runBlocking { repository.fetchAndStoreArtwork(ALBUM_ID, ARTIST, ALBUM, TRACKS, AnimatedArtOrientation.SQUARE_1_1) }
        val saved = (check() as AppResult.Success).data as LiveCoverCheck.Downloaded

        runBlocking { repository.deleteArtwork(ALBUM_ID) }

        assertFalse(File(saved.filePath).exists())
        assertNull(runBlocking { repository.observeAnimatedArtwork(ALBUM_ID).first() })
    }

    private fun entity(
        albumId: Long = ALBUM_ID,
        status: AnimatedArtStatus,
        squareFileName: String? = null,
        artist: String = "sza",
    ) = AnimatedArtworkEntity(
        albumId = albumId,
        normalizedArtist = artist,
        normalizedAlbum = "sos",
        squareFileName = squareFileName,
        status = status.name,
        updatedAtEpochMs = System.currentTimeMillis(),
    )

    private class FakeNetwork : AnimatedArtworkNetworkDataSource {
        var resolve: AppResult<ResolveOutcome> = AppResult.Success(ResolveOutcome.NoMatch)
        var variantBytes: Long? = EXACT_BYTES
        var downloadGate: CompletableDeferred<Unit>? = null
        val downloadStarted = CompletableDeferred<Unit>()
        var resolveCalls = 0
        var masterCalls = 0
        var downloads = 0
        val sizedVariants = mutableListOf<String>()

        override suspend fun resolveAlbum(
            artist: String,
            album: String,
            trackCount: Int?,
        ): AppResult<ResolveOutcome> {
            resolveCalls++
            return resolve
        }

        override suspend fun fetchMasterUrls(
            catalogAlbumId: String,
            storefront: String,
        ): AppResult<EditorialVideoUrls> {
            masterCalls++
            val matched = (resolve as? AppResult.Success)?.data as? ResolveOutcome.Matched
            return AppResult.Success(EditorialVideoUrls(matched?.squareMasterUrl, matched?.tallMasterUrl))
        }

        override suspend fun fetchVariantStreamsForMasterUrl(masterUrl: String): AppResult<List<HlsVariantStream>> =
            AppResult.Success(
                listOf(
                    HlsVariantStream("2048x2048", 2048, 2048, 17_000_000, "$masterUrl/2048", 10f),
                    HlsVariantStream("1080x1080", 1080, 1080, 9_000_000, "$masterUrl/1080", 5f),
                    HlsVariantStream("720x720", 720, 720, 4_000_000, "$masterUrl/720", 2f),
                ),
            )

        override suspend fun fetchVariantByteSize(variantUrl: String): AppResult<Long?> {
            sizedVariants += variantUrl
            return AppResult.Success(variantBytes)
        }

        override suspend fun downloadToFile(
            masterUrl: String,
            destination: File,
            tmp: File,
        ): AppResult<Unit> {
            downloads++
            tmp.parentFile?.mkdirs()
            tmp.writeBytes(ByteArray(2048))
            downloadStarted.complete(Unit)
            downloadGate?.await()
            tmp.renameTo(destination)
            return AppResult.Success(Unit)
        }

        override suspend fun downloadVariantToFile(
            variantUrl: String,
            destination: File,
            tmp: File,
        ): AppResult<Unit> = downloadToFile(variantUrl, destination, tmp)
    }

    private class FakeSettings : DataStore<PlaybackStateProto> {
        private val state = MutableStateFlow(PlaybackStateProto.getDefaultInstance())
        override val data: Flow<PlaybackStateProto> = state

        override suspend fun updateData(transform: suspend (t: PlaybackStateProto) -> PlaybackStateProto): PlaybackStateProto =
            transform(state.value).also { state.value = it }
    }

    private object TestDispatchers : DispatcherProvider {
        override val main = Dispatchers.Unconfined
        override val io = Dispatchers.Default
        override val default = Dispatchers.Default
    }

    private companion object {
        const val ALBUM_ID = 42L
        const val OTHER_ALBUM_ID = 43L
        const val ARTIST = "SZA"
        const val ALBUM = "SOS"
        const val TRACKS = 23
        const val SQUARE_MASTER = "https://example.invalid/square.m3u8"
        const val TALL_MASTER = "https://example.invalid/tall.m3u8"
        const val SQUARE_1080 = "$SQUARE_MASTER/1080"
        const val EXACT_BYTES = 28_189_727L
    }
}
