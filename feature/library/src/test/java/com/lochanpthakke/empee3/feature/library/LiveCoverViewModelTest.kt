package com.lochanpthakke.empee3.feature.library

import com.lochanpthakke.empee3.core.common.result.AppError
import com.lochanpthakke.empee3.core.common.result.AppResult
import com.lochanpthakke.empee3.core.database.repository.AnimatedArtworkRepository
import com.lochanpthakke.empee3.core.database.repository.LiveCoverCheck
import com.lochanpthakke.empee3.core.model.Album
import com.lochanpthakke.empee3.core.model.AnimatedArtOrientation
import com.lochanpthakke.empee3.core.model.AnimatedArtStatus
import com.lochanpthakke.empee3.core.model.LocalAnimatedArtwork
import com.lochanpthakke.empee3.core.network.apple.EditorialVideoUrls
import com.lochanpthakke.empee3.core.network.apple.HlsVariantStream
import com.lochanpthakke.empee3.core.network.apple.ResolveOutcome
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LiveCoverViewModelTest {
    private val album = Album(id = 7, title = "SOS", artist = "SZA", artistId = 1, songCount = 23, year = 2022)
    private val offer = LiveCoverCheck.Downloadable(AnimatedArtOrientation.SQUARE_1_1, 1080, 1080, 28_189_727)
    private val saved = LiveCoverCheck.Downloaded(AnimatedArtOrientation.SQUARE_1_1, "/covers/SOS.mp4", 28_189_727)
    private val repository = FakeRepository()

    @Before
    fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun reset() = Dispatchers.resetMain()

    private fun viewModel() = LiveCoverViewModel(repository).also { it.bind(album) }

    @Test
    fun `nothing is shown for an album without a live cover`() =
        runTest {
            repository.check = AppResult.Success(LiveCoverCheck.None)

            assertEquals(LiveCoverUi.Hidden, viewModel().state.value)
        }

    @Test
    fun `nothing is shown when the check fails, and nothing is claimed`() =
        runTest {
            repository.check = AppResult.Error(AppError.NetworkError("offline"))

            assertEquals(LiveCoverUi.Hidden, viewModel().state.value)
        }

    @Test
    fun `a live cover is offered, then saved by Download`() =
        runTest {
            repository.check = AppResult.Success(offer)
            val viewModel = viewModel()
            assertEquals(LiveCoverUi.Offer(offer), viewModel.state.value)

            repository.check = AppResult.Success(saved)
            viewModel.download()

            assertEquals(LiveCoverUi.Saved(saved), viewModel.state.value)
            assertEquals(listOf(AnimatedArtOrientation.SQUARE_1_1), repository.fetches)
        }

    @Test
    fun `an already saved cover shows as saved`() =
        runTest {
            repository.check = AppResult.Success(saved)

            assertEquals(LiveCoverUi.Saved(saved), viewModel().state.value)
        }

    @Test
    fun `repeated taps while downloading start one download`() =
        runTest {
            repository.check = AppResult.Success(offer)
            repository.gate = CompletableDeferred()
            val viewModel = viewModel()

            repeat(3) { viewModel.download() }

            assertEquals(LiveCoverUi.Downloading(offer), viewModel.state.value)
            assertEquals(1, repository.fetches.size)
            repository.gate!!.complete(Unit)
        }

    @Test
    fun `a failed download says so and can be retried`() =
        runTest {
            repository.check = AppResult.Success(offer)
            repository.fetchResult = AppResult.Error(AppError.NetworkError("timeout"))
            val viewModel = viewModel()

            viewModel.download()

            val failed = viewModel.state.value as LiveCoverUi.Failed
            assertEquals(offer, failed.offer)
            assertTrue(failed.message.isNotBlank())
        }

    @Test
    fun `a download that finds no cover after all is not reported as saved`() =
        runTest {
            repository.check = AppResult.Success(offer)
            val viewModel = viewModel()
            repository.check = AppResult.Success(LiveCoverCheck.None)

            viewModel.download()

            assertTrue(viewModel.state.value is LiveCoverUi.Failed)
        }

    @Test
    fun `cancelling stops the download and offers the cover again`() =
        runTest {
            repository.check = AppResult.Success(offer)
            repository.gate = CompletableDeferred()
            val viewModel = viewModel()
            viewModel.download()

            viewModel.cancel()

            assertEquals(listOf(album.id), repository.cancelled)
            assertEquals(LiveCoverUi.Offer(offer), viewModel.state.value)
        }

    @Test
    fun `removing a saved cover deletes it and offers it again`() =
        runTest {
            repository.check = AppResult.Success(saved)
            val viewModel = viewModel()
            repository.check = AppResult.Success(offer)

            viewModel.remove()

            assertEquals(listOf(album.id), repository.deleted)
            assertEquals(LiveCoverUi.Offer(offer), viewModel.state.value)
        }

    @Test
    fun `binding the same album again does not check again`() =
        runTest {
            repository.check = AppResult.Success(offer)
            val viewModel = viewModel()

            viewModel.bind(album)

            assertEquals(1, repository.checks)
        }

    private class FakeRepository : AnimatedArtworkRepository {
        var check: AppResult<LiveCoverCheck> = AppResult.Success(LiveCoverCheck.None)
        var fetchResult: AppResult<LocalAnimatedArtwork>? = null
        var gate: CompletableDeferred<Unit>? = null
        var checks = 0
        val fetches = mutableListOf<AnimatedArtOrientation>()
        val cancelled = mutableListOf<Long>()
        val deleted = mutableListOf<Long>()

        override suspend fun checkLiveCover(
            albumId: Long,
            albumArtist: String,
            album: String,
            trackCount: Int?,
        ): AppResult<LiveCoverCheck> {
            checks++
            return check
        }

        override suspend fun fetchAndStoreArtwork(
            albumId: Long,
            albumArtist: String,
            album: String,
            trackCount: Int?,
            orientation: AnimatedArtOrientation,
            forceRefresh: Boolean,
        ): AppResult<LocalAnimatedArtwork> {
            fetches += orientation
            gate?.await()
            return fetchResult ?: AppResult.Success(
                LocalAnimatedArtwork(albumId, "/covers/SOS.mp4", null, true, null, AnimatedArtStatus.AVAILABLE, "1", "us", 0),
            )
        }

        override fun cancelDownload(albumId: Long) {
            cancelled += albumId
            gate?.cancel()
        }

        override suspend fun deleteArtwork(albumId: Long) {
            deleted += albumId
        }

        override fun observeAnimatedArtwork(albumId: Long): Flow<LocalAnimatedArtwork?> = flowOf(null)

        override suspend fun resolveAlbum(
            albumArtist: String,
            album: String,
            trackCount: Int?,
        ): AppResult<ResolveOutcome> = error("unused")

        override suspend fun fetchMasterUrls(
            catalogAlbumId: String,
            storefront: String,
        ): AppResult<EditorialVideoUrls> = error("unused")

        override suspend fun fetchVariantsForMasterUrl(masterUrl: String): AppResult<List<HlsVariantStream>> = error("unused")

        override suspend fun downloadAndSaveVariant(
            albumId: Long,
            albumArtist: String,
            album: String,
            catalogAlbumId: String,
            storefront: String,
            orientation: AnimatedArtOrientation,
            variantUrl: String,
        ): AppResult<LocalAnimatedArtwork> = error("unused")

        override suspend fun fetchAndStoreArtworkByUrl(
            albumId: Long,
            albumArtist: String,
            album: String,
            catalogAlbumId: String,
            storefront: String,
            orientation: AnimatedArtOrientation,
            forceRefresh: Boolean,
        ): AppResult<LocalAnimatedArtwork> = error("unused")

        override suspend fun deleteAllArtwork() = Unit

        override suspend fun totalStorageBytes(): Long = 0

        override fun observePreferredOrientation(): Flow<AnimatedArtOrientation> = flowOf(AnimatedArtOrientation.SQUARE_1_1)

        override suspend fun setPreferredOrientation(orientation: AnimatedArtOrientation) = Unit

        override suspend fun cleanupOnStartup() = Unit
    }
}
