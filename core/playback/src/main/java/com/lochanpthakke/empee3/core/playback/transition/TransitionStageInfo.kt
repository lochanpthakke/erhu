package com.lochanpthakke.empee3.core.playback.transition

import com.lochanpthakke.empee3.core.sound.dsp.TransitionGainProcessor
import javax.inject.Inject
import javax.inject.Singleton

/** Read-only view of the main player's transition gain stage, for the audio inspector. */
@Singleton
class TransitionStageInfo
    @Inject
    constructor(
        private val gain: TransitionGainProcessor,
    ) {
        /** True while a Smooth Transition is fading the incoming track in (otherwise the stage is unity gain). */
        val isFading: Boolean get() = gain.isEngaged
    }
