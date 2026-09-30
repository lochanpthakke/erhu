package com.lochanpthakke.empee3.core.sound.repository

import com.lochanpthakke.empee3.core.common.dispatcher.DispatcherProvider
import com.lochanpthakke.empee3.core.common.logging.Logger
import com.lochanpthakke.empee3.core.common.result.AppResult
import com.lochanpthakke.empee3.core.datastore.SoundSettingsPersistence
import com.lochanpthakke.empee3.core.model.PersistedSoundSettings
import com.lochanpthakke.empee3.core.sound.dsp.CustomEqAudioProcessor
import com.lochanpthakke.empee3.core.sound.effects.AudioEffectManager
import com.lochanpthakke.empee3.core.sound.effects.EffectCapabilities
import com.lochanpthakke.empee3.core.sound.model.EqPreset
import com.lochanpthakke.empee3.core.sound.model.SoundSettings
import com.lochanpthakke.empee3.core.sound.model.toPersisted
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SoundRepositoryImplTest {
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

    /** In-memory stand-in for the DataStore-backed persistence; survives "restarts". */
    private class FakePersistence(
        initial: PersistedSoundSettings? = null,
    ) : SoundSettingsPersistence {
        val stored = MutableStateFlow(initial)
        val saves = mutableListOf<PersistedSoundSettings>()

        /** When set, the first read suspends until completed (a slow disk). */
        var readGate: CompletableDeferred<Unit>? = null
        var failReads = false

        override suspend fun save(settings: PersistedSoundSettings): AppResult<Unit> {
            saves += settings
            stored.value = settings
            return AppResult.Success(Unit)
        }

        override fun observe(): Flow<PersistedSoundSettings?> =
            flow {
                readGate?.await()
                if (failReads) throw java.io.IOException("disk error")
                emit(stored.value)
            }
    }

    private class TestDispatchers(
        dispatcher: CoroutineDispatcher,
    ) : DispatcherProvider {
        override val main = dispatcher
        override val io = dispatcher
        override val default = dispatcher
    }

    private class Harness(
        scope: TestScope,
        val persistence: FakePersistence,
    ) {
        val processor = CustomEqAudioProcessor()
        val repository =
            SoundRepositoryImpl(
                customEqAudioProcessor = processor,
                effectManager = AudioEffectManager(EffectCapabilities(SilentLogger), SilentLogger),
                persistence = persistence,
                dispatchers = TestDispatchers(StandardTestDispatcher(scope.testScheduler)),
                logger = SilentLogger,
            )
    }

    private val saved =
        SoundSettings(
            isEqEnabled = false,
            currentBandGains = floatArrayOf(6f, 5f, 3f, 1f, 0f, 0f, 0f, 0f),
            activePresetId = EqPreset.BASS_BOOSTER.id,
            customPresets = listOf(EqPreset("custom_1", "Car", FloatArray(8) { 2f }, isCustom = true)),
            isBassBoostEnabled = true,
            bassBoostStrength = 500,
        ).toPersisted()

    @Test
    fun freshInstall_keepsDefaults_andWritesNothing() =
        runTest {
            val h = Harness(this, FakePersistence(initial = null))
            advanceUntilIdle()

            assertEquals(SoundSettings(), h.repository.soundSettings.value)
            assertTrue(h.processor.isEqEnabled())
            assertArrayEquals(EqPreset.FLAT.bandGains, h.processor.getBandGains(), 0f)
            assertTrue("defaults must never be written", h.persistence.saves.isEmpty())
        }

    @Test
    fun startup_restoresSettingsIntoUiStateAndAudioProcessor() =
        runTest {
            val h = Harness(this, FakePersistence(initial = saved))
            advanceUntilIdle()

            val state = h.repository.soundSettings.value
            assertEquals(saved, state.toPersisted())
            assertFalse(h.processor.isEqEnabled())
            assertArrayEquals(saved.bandGains.toFloatArray(), h.processor.getBandGains(), 0f)
            assertTrue("restoring must not re-save", h.persistence.saves.isEmpty())
        }

    @Test
    fun restart_restoresExactlyWhatWasSaved() =
        runTest {
            val disk = FakePersistence()
            val first = Harness(this, disk)
            advanceUntilIdle()
            first.repository.applyPreset(EqPreset.ROCK.id)
            first.repository.setBandGain(2, 7.5f)
            first.repository.setEqEnabled(false)
            first.repository.setVirtualizer(true, 420)
            first.repository.saveCustomPreset("Night")
            advanceUntilIdle()
            val beforeRestart = first.repository.soundSettings.value

            val second = Harness(this, disk) // new process, same disk
            advanceUntilIdle()

            assertEquals(beforeRestart, second.repository.soundSettings.value)
            assertArrayEquals(beforeRestart.currentBandGains, second.processor.getBandGains(), 0f)
            assertFalse(second.processor.isEqEnabled())
        }

    @Test
    fun changes_reachTheProcessorImmediately_beforeAnyPersistence() =
        runTest {
            val h = Harness(this, FakePersistence())
            advanceUntilIdle()

            h.repository.setBandGain(3, 9f)
            // No time advanced: the audio path must already have the new gain.
            assertEquals(9f, h.processor.getBandGains()[3], 0f)
            h.repository.setEqEnabled(false)
            assertFalse(h.processor.isEqEnabled())
            assertTrue(h.persistence.saves.isEmpty())
        }

    @Test
    fun sliderDrag_isSavedOnceWithTheFinalValue() =
        runTest {
            val h = Harness(this, FakePersistence())
            advanceUntilIdle()

            for (gain in 1..10) {
                h.repository.setBandGain(0, gain.toFloat())
                advanceTimeBy(50)
            }
            advanceUntilIdle()

            assertEquals(1, h.persistence.saves.size)
            assertEquals(10f, h.persistence.saves.single().bandGains[0], 0f)
        }

    @Test
    fun slowRestore_doesNotLetDefaultsOverwriteSavedSettings() =
        runTest {
            val disk = FakePersistence(initial = saved).apply { readGate = CompletableDeferred() }
            val h = Harness(this, disk)
            runCurrent() // restore is now blocked on the "disk"

            // Nothing may be written while the saved state is unknown.
            advanceTimeBy(5_000)
            assertTrue(disk.saves.isEmpty())

            disk.readGate!!.complete(Unit)
            advanceUntilIdle()
            assertEquals(saved, h.repository.soundSettings.value.toPersisted())
            assertTrue(disk.saves.isEmpty())
        }

    @Test
    fun changeMadeDuringRestore_winsButSavedPresetsSurvive() =
        runTest {
            val disk = FakePersistence(initial = saved).apply { readGate = CompletableDeferred() }
            val h = Harness(this, disk)
            runCurrent()

            h.repository.applyPreset(EqPreset.JAZZ.id)
            disk.readGate!!.complete(Unit)
            advanceUntilIdle()

            val state = h.repository.soundSettings.value
            assertEquals(EqPreset.JAZZ.id, state.activePresetId)
            assertArrayEquals(EqPreset.JAZZ.bandGains, h.processor.getBandGains(), 0f)
            assertEquals(listOf("custom_1"), state.customPresets.map { it.id })
            assertEquals(EqPreset.JAZZ.id, disk.stored.value!!.activePresetId)
        }

    @Test
    fun unreadableStore_fallsBackToDefaultsWithoutCrashing() =
        runTest {
            val h = Harness(this, FakePersistence(initial = saved).apply { failReads = true })
            advanceUntilIdle()

            assertEquals(SoundSettings(), h.repository.soundSettings.value)

            h.repository.setBandGain(1, 3f)
            advanceUntilIdle()
            assertEquals(1, h.persistence.saves.size)
        }
}
