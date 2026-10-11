package com.lochanpthakke.empee3.core.sound.dsp

import com.lochanpthakke.empee3.core.sound.model.SoundSettings

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
