package com.erhu.erhu.feature.library

import androidx.lifecycle.ViewModelStore
import com.erhu.erhu.core.model.Album
import com.erhu.erhu.core.model.Song
import com.erhu.erhu.feature.library.mix.RadioPlan
import com.erhu.erhu.feature.library.mix.RadioSeed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RadioStationViewModelTest {
    private val album = Album(5, "Her Loss", "Drake", 7, 10, 2022, "content://covers/5")
    private val seed = RadioSeed.Album(5)
    private val plan = RadioPlan(seed, "Her Loss", 9, listOf(11, 12, 9), emptyList())
    private fun song(id: Long) = Song(id, "Song $id", "Artist", "Album", 1000, "content://songs/$id")

    @Test fun `opening loads once without any playback dependency and row changes keep the same plan`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            var builds = 0
            var requestedIds = emptyList<Long>()
            val songs = MutableStateFlow(listOf(song(9), song(11), song(12)))
            val loader = RadioStationLoader({ album }, { requested ->
                assertEquals(seed, requested)
                builds++
                plan
            })
            val model = RadioStationViewModel(loader) { ids -> requestedIds = ids; songs }
            store.put("station", model)
            model.open(5)
            model.open(5) // composition recreation must not rebuild the station
            assertEquals(1, builds)
            assertEquals(listOf(9L, 11L, 12L), requestedIds)
            assertEquals(plan, model.content.value?.plan)
            assertEquals(songs.value, model.content.value?.songs)
            songs.value = songs.value.map { it.copy(isFavorite = true) }
            assertEquals(true, model.content.value?.songs?.first()?.isFavorite)
            assertEquals(1, builds)
            assertEquals(plan, model.content.value?.plan)
        } finally {
            store.clear() // Back/navigation releases the station observer
            Dispatchers.resetMain()
        }
    }

    @Test fun `missing album and provider errors can be retried without playback`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            var available = false
            val loader = RadioStationLoader({ if (available) album else null }, { plan })
            val model = RadioStationViewModel(loader) { MutableStateFlow(listOf(song(9))) }
            store.put("station", model)
            model.open(5)
            assertNull(model.content.value)
            assertNotNull(model.error.value)
            available = true
            model.open(5)
            assertEquals(plan, model.content.value?.plan)
            assertNull(model.error.value)
        } finally { store.clear(); Dispatchers.resetMain() }
    }
}
