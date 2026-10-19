package com.lochanpthakke.empee3.feature.library.mix

import com.lochanpthakke.empee3.core.database.repository.TimeSource
import com.lochanpthakke.empee3.core.model.MonthlyPlays
import com.lochanpthakke.empee3.core.model.TrackFacts
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SmartMixSourceTest {
    /**
     * Lets virtual time run well past the debounce. (advanceUntilIdle would not do: it ignores the
     * backgroundScope work the source's sharing runs in.)
     */
    private fun TestScope.settle() {
        advanceTimeBy(10_000)
        runCurrent()
    }

    private val library = (1L..30L).map { track(it, artist = (it - 1) / 3 + 1, genre = if (it <= 15) "Hip-Hop" else "Soul") }
    private val history = (1L..12L).map { played(it, 4, daysAgo = (it % 5).toInt()) }

    private class Fixture(
        scope: TestScope,
        tracks: List<TrackFacts>,
        plays: List<MonthlyPlays>,
        taste: TasteNeighbours,
    ) {
        val tracks = MutableStateFlow(tracks)
        val plays = MutableStateFlow(plays)
        val hidden = MutableStateFlow(emptySet<Long>())
        val dispatcher = StandardTestDispatcher(scope.testScheduler)
        val source =
            SmartMixSource(
                tracks = this.tracks,
                plays = this.plays,
                hidden = this.hidden,
                clock = TimeSource { NOW },
                taste = taste,
                scope = scope.backgroundScope,
                compute = dispatcher,
                debounceMs = 1_000,
            )
    }

    @Test
    fun buildsOnce_thenRebuildsOnlyWhenListeningChanges() =
        runTest {
            var builds = 0
            val f = Fixture(this, library, history, TasteNeighbours { _, _ -> builds++; null })
            backgroundScope.launch { f.source.mixes.collect {} }
            settle()
            val first = f.source.mixes.value
            assertNotNull(first)
            assertEquals(1, builds)

            // Nothing changed: re-subscribing or re-reading does not rebuild.
            backgroundScope.launch { f.source.mixes.collect {} }
            settle()
            assertEquals(1, builds)

            // A counted play rebuilds (after the debounce) and the rotation follows it.
            f.plays.value = history + played(20, 9, daysAgo = 0)
            advanceTimeBy(500)
            runCurrent()
            assertEquals(1, builds)
            settle()
            assertEquals(2, builds)
            assertEquals(20L, f.source.mixes.value!!.mix(MixKind.ROTATION)!!.songIds.first())
        }

    @Test
    fun aBurstOfChangesIsBuiltOnce() =
        runTest {
            var builds = 0
            val f = Fixture(this, library, history, TasteNeighbours { _, _ -> builds++; null })
            backgroundScope.launch { f.source.mixes.collect {} }
            settle()
            repeat(5) { i ->
                f.plays.value = history + played(21, i + 1, daysAgo = 0)
                advanceTimeBy(200)
            }
            settle()
            assertEquals(2, builds)
        }

    @Test
    fun worksWithoutVybe_andWhenVybeFails() =
        runTest {
            val failing = Fixture(this, library, history, TasteNeighbours { _, _ -> error("no index") })
            assertTrue(failing.source.mixes.filterNotNull().first().mixes.isNotEmpty())
        }

    @Test
    fun vybeIsNotAskedWhileHistoryIsSparse() =
        runTest {
            var asked = false
            val f = Fixture(this, library, listOf(played(1, 1, 0)), TasteNeighbours { _, _ -> asked = true; null })
            val mixes = f.source.mixes.filterNotNull().first()
            assertTrue(mixes.needsMoreListening)
            assertEquals(false, asked)
        }

    @Test
    fun aMixIsLookedUpByKey_withTheSameSongsAsItsCard() =
        runTest {
            val f = Fixture(this, library, history, TasteNeighbours { _, _ -> null })
            val all = f.source.mixes.filterNotNull().first()
            val rotation = all.mix(MixKind.ROTATION)!!
            assertEquals(rotation, f.source.mix(MixKind.ROTATION.key).filterNotNull().first())
            assertNull(f.source.mix("no-such-mix").first())
        }

    @Test
    fun removedSongsLeaveTheMixes() =
        runTest {
            val f = Fixture(this, library, history, TasteNeighbours { _, _ -> null })
            backgroundScope.launch { f.source.mixes.collect {} }
            settle()
            assertTrue(1L in f.source.mixes.value!!.mix(MixKind.ROTATION)!!.songIds)
            f.tracks.value = library.filter { it.songId != 1L }
            settle()
            assertTrue(f.source.mixes.value!!.mixes.none { 1L in it.songIds })
        }

    @Test
    fun hiddenSongsLeaveEveryMix_andComeBackWhenShownAgain() =
        runTest {
            val f = Fixture(this, library, history, TasteNeighbours { _, _ -> null })
            backgroundScope.launch { f.source.mixes.collect {} }
            settle()
            val rotation = f.source.mixes.value!!.mix(MixKind.ROTATION)!!.songIds
            val hiddenSong = rotation.first()
            f.hidden.value = setOf(hiddenSong)
            settle()
            assertTrue(f.source.mixes.value!!.mixes.none { hiddenSong in it.songIds })
            f.hidden.value = emptySet()
            settle()
            assertEquals(rotation, f.source.mixes.value!!.mix(MixKind.ROTATION)!!.songIds)
        }
}
