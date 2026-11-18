package com.erhu.erhu.core.sound.dsp

import com.erhu.erhu.core.sound.model.SoundSettings

/** Puts [settings]' equalizer state into this processor (the main player's or a secondary one). */
fun CustomEqAudioProcessor.applySettings(settings: SoundSettings) {
    configureBands(settings.effectiveBands())
    setEqEnabled(settings.isEqEnabled)
    setUtilities(settings.utilities)
}
