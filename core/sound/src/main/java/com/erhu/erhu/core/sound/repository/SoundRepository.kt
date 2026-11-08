package com.erhu.erhu.core.sound.repository

import com.erhu.erhu.core.common.result.AppResult
import com.erhu.erhu.core.sound.dsp.FilterType
import com.erhu.erhu.core.sound.model.EqPreset
import com.erhu.erhu.core.sound.model.SoundSettings
import kotlinx.coroutines.flow.StateFlow

@Suppress("TooManyFunctions")
interface SoundRepository {
    val soundSettings: StateFlow<SoundSettings>

    fun attachAudioSession(audioSessionId: Int)

    fun setUtilities(value: com.erhu.erhu.core.model.AudioUtilities)

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

    /** What the equalizer processor is doing right now (read-only, for the audio inspector). */
    fun eqRuntimeState(): com.erhu.erhu.core.sound.dsp.EqRuntimeState
}
