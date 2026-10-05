package com.lochanpthakke.empee3.core.sound.repository

import com.lochanpthakke.empee3.core.common.result.AppResult
import com.lochanpthakke.empee3.core.sound.dsp.FilterType
import com.lochanpthakke.empee3.core.sound.effects.AudioEffectCapabilities
import com.lochanpthakke.empee3.core.sound.model.EqPreset
import com.lochanpthakke.empee3.core.sound.model.SoundSettings
import kotlinx.coroutines.flow.StateFlow

@Suppress("TooManyFunctions")
interface SoundRepository {
    val soundSettings: StateFlow<SoundSettings>
    val capabilities: StateFlow<AudioEffectCapabilities>

    fun attachAudioSession(audioSessionId: Int)

    fun setEqEnabled(enabled: Boolean)

    fun setBandGain(
        bandIndex: Int,
        gainDb: Float,
    )

    fun setBandFrequency(
        bandIndex: Int,
        freqHz: Float,
    )

    fun setBandQ(
        bandIndex: Int,
        q: Float,
    )

    fun setFilterType(
        bandIndex: Int,
        type: FilterType,
    )

    fun setActiveBandCount(count: Int)

    fun setSelectedBandIndex(index: Int)

    fun setBandFrequencyAndGain(
        bandIndex: Int,
        freqHz: Float,
        gainDb: Float,
    )

    fun applyPreset(presetId: String)

    fun resetToFlat()

    suspend fun saveCustomPreset(name: String): AppResult<EqPreset>

    suspend fun deleteCustomPreset(presetId: String): AppResult<Unit>

    fun setBassBoost(
        enabled: Boolean,
        strength: Short,
    )

    fun setVirtualizer(
        enabled: Boolean,
        strength: Short,
    )

    fun setLoudnessEnhancer(
        enabled: Boolean,
        gainMb: Int,
    )
}
