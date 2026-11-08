package com.erhu.erhu.core.playback

import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.erhu.erhu.core.model.AudioUtilities
import com.erhu.erhu.core.sound.dsp.CustomEqAudioProcessor
import com.erhu.erhu.core.sound.model.SoundSettings
import com.erhu.erhu.core.sound.repository.SoundRepository
import java.lang.reflect.Proxy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PlaybackUtilitiesTest {
    private class Fixture {
        val settings = MutableStateFlow(SoundSettings())
        val listeners = mutableListOf<Player.Listener>()
        var parameters = PlaybackParameters.DEFAULT
        var skipping = false
        var endOfTrack = false
        var pauses = 0
        var updates = 0
        val sound = Proxy.newProxyInstance(SoundRepository::class.java.classLoader, arrayOf(SoundRepository::class.java)) { _, method, args ->
            when (method.name) {
                "getSoundSettings" -> settings
                "setUtilities" -> { settings.value = settings.value.copy(utilities = args!![0] as AudioUtilities); null }
                "toString" -> "TestSoundRepository"
                else -> error("Unexpected sound operation: ${method.name}")
            }
        } as SoundRepository
        val player = Proxy.newProxyInstance(ExoPlayer::class.java.classLoader, arrayOf(ExoPlayer::class.java)) { _, method, args ->
            when (method.name) {
                "addListener" -> { listeners += args!![0] as Player.Listener; null }
                "getPlaybackParameters" -> parameters
                "setPlaybackParameters" -> { parameters = args!![0] as PlaybackParameters; null }
                "getSkipSilenceEnabled" -> skipping
                "setSkipSilenceEnabled" -> { skipping = args!![0] as Boolean; null }
                "getPauseAtEndOfMediaItems" -> endOfTrack
                "setPauseAtEndOfMediaItems" -> { endOfTrack = args!![0] as Boolean; null }
                "pause" -> { pauses++; null }
                "toString" -> "TestPlayer"
                else -> error("Unexpected playback/queue operation: ${method.name}")
            }
        } as ExoPlayer
        fun set(u: AudioUtilities) { settings.value = settings.value.copy(utilities = u) }
    }

    @Test fun ratePitchAndSilenceChangeOnTheExistingSession() = runTest {
        val f = Fixture()
        PlaybackUtilities(f.player, f.sound, CustomEqAudioProcessor(), backgroundScope, { f.updates++ }).start()
        runCurrent()
        f.set(AudioUtilities(speed = 1.5f, pitch = 0.8f, skipSilence = true)); runCurrent()
        assertEquals(1.5f, f.parameters.speed, 0f); assertEquals(0.8f, f.parameters.pitch, 0f)
        assertTrue(f.skipping); assertEquals(1, f.updates)
        f.set(f.settings.value.utilities.copy(balance = 0.5f)); runCurrent()
        assertEquals("Balance must not repeatedly rebuild transitions", 1, f.updates)
        assertEquals(0, f.pauses)
    }
    @Test fun timerRunsInBackgroundAndDoesNotResetWhenSoundSettingsChange() = runTest {
        val f = Fixture(); val epoch = 1000000L
        PlaybackUtilities(f.player, f.sound, CustomEqAudioProcessor(), backgroundScope, {}, { epoch + testScheduler.currentTime }).start()
        f.set(AudioUtilities(sleepDeadlineMs = epoch + 1000)); runCurrent()
        advanceTimeBy(500); f.set(f.settings.value.utilities.copy(mono = true)); runCurrent()
        advanceTimeBy(499); runCurrent(); assertEquals(0, f.pauses)
        advanceTimeBy(1); runCurrent(); assertEquals(1, f.pauses)
        assertEquals(0L, f.settings.value.utilities.sleepDeadlineMs)
        assertTrue(f.settings.value.utilities.mono)
    }
    @Test fun cancelledTimerCannotPauseALaterSession() = runTest {
        val f = Fixture(); val epoch = 1000000L
        PlaybackUtilities(f.player, f.sound, CustomEqAudioProcessor(), backgroundScope, {}, { epoch + testScheduler.currentTime }).start()
        f.set(AudioUtilities(sleepDeadlineMs = epoch + 1000)); runCurrent()
        f.set(AudioUtilities()); runCurrent(); advanceTimeBy(2000); runCurrent()
        assertEquals(0, f.pauses)
    }
    @Test fun expiredRestoredTimerPausesOnceAndClearsItself() = runTest {
        val f = Fixture(); f.set(AudioUtilities(sleepDeadlineMs = 50))
        PlaybackUtilities(f.player, f.sound, CustomEqAudioProcessor(), backgroundScope, {}, { 100 }).start()
        runCurrent(); assertEquals(1, f.pauses); assertEquals(0L, f.settings.value.utilities.sleepDeadlineMs)
        runCurrent(); assertEquals(1, f.pauses)
    }
    @Test fun endOfTrackUsesNativePauseAndClearsWithoutChangingQueueOrPermission() = runTest {
        val f = Fixture()
        PlaybackUtilities(f.player, f.sound, CustomEqAudioProcessor(), backgroundScope, {}).start()
        f.set(AudioUtilities(sleepAtTrackEnd = true)); runCurrent(); assertTrue(f.endOfTrack)
        f.listeners.single().onPlayWhenReadyChanged(false, Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM)
        runCurrent(); assertFalse(f.settings.value.utilities.sleepAtTrackEnd); assertFalse(f.endOfTrack)
        assertEquals(0, f.pauses)
    }
}
