package com.erhu.erhu.core.sound.dsp

import com.erhu.erhu.core.sound.model.SoundSettings

/** Puts [settings]' equalizer state into this processor (the main player's or a secondary one). */
fun CustomEqAudioProcessor.applySettings(settings: SoundSettings) {
    configureParametricState(
        gainsDb = settings.currentBandGains,
        frequenciesHz = settings.currentBandFrequencies,
        qs = settings.currentBandQs,
        types = settings.currentFilterTypes,
        activeCount = settings.activeBandCount,
    )
    setEqEnabled(settings.isEqEnabled)
}
