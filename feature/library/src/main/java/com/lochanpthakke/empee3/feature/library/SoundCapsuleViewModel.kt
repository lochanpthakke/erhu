package com.lochanpthakke.empee3.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lochanpthakke.empee3.core.database.repository.ListeningStatsRepository
import com.lochanpthakke.empee3.core.model.SoundCapsule
import com.lochanpthakke.empee3.core.playback.repository.PlaybackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth
import javax.inject.Inject

/** The month shown and whether there is an earlier / later month with listening to move to. */
data class CapsuleMonth(
    val month: YearMonth,
    val previous: YearMonth?,
    val next: YearMonth?,
)

/** Sound Capsule: one month of listening, the current month first. */
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class SoundCapsuleViewModel
    @Inject
    constructor(
        private val listeningStats: ListeningStatsRepository,
        private val playbackRepository: PlaybackRepository,
    ) : ViewModel() {
        private val selected = MutableStateFlow(listeningStats.currentMonth())

        val month: StateFlow<CapsuleMonth> =
            combine(selected, listeningStats.observeMonths()) { month, withListening ->
                // The current month is always reachable, even before its first play.
                val months = (withListening + listeningStats.currentMonth()).distinct().sorted()
                CapsuleMonth(
                    month = month,
                    previous = months.lastOrNull { it < month },
                    next = months.firstOrNull { it > month },
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CapsuleMonth(selected.value, null, null))

        /** Null until the selected month has loaded. */
        val capsule: StateFlow<SoundCapsule?> =
            selected
                .flatMapLatest { month -> listeningStats.observeCapsule(month).map<SoundCapsule, SoundCapsule?> { it } }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

        fun showMonth(month: YearMonth) {
            selected.value = month
        }

        /** Plays the month's top songs from [index], so the rest of the list follows. */
        fun playTopSong(index: Int) {
            val songs = capsule.value?.topSongs?.map { it.song } ?: return
            if (index in songs.indices) playbackRepository.playSongsFromQueue(songs, index)
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
