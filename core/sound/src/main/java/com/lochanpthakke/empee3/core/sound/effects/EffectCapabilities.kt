package com.lochanpthakke.empee3.core.sound.effects

import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import com.lochanpthakke.empee3.core.common.logging.Logger
import javax.inject.Inject
import javax.inject.Singleton

data class AudioEffectCapabilities(
    val isBassBoostSupported: Boolean = false,
    val isVirtualizerSupported: Boolean = false,
    val isLoudnessEnhancerSupported: Boolean = false,
)

@Singleton
class EffectCapabilities
    @Inject
    constructor(
        private val logger: Logger,
    ) {
        @Suppress("MagicNumber", "LongMethod", "CyclomaticComplexMethod", "TooGenericExceptionCaught")
        fun checkCapabilities(audioSessionId: Int): AudioEffectCapabilities {
            if (audioSessionId <= 0) return AudioEffectCapabilities()

            var bassBoostSupported = false
            var virtualizerSupported = false
            var loudnessSupported = false

            // Query system AudioEffects Descriptor
            try {
                val availableEffects = AudioEffect.queryEffects()
                for (effect in availableEffects) {
                    when (effect.type) {
                        AudioEffect.EFFECT_TYPE_BASS_BOOST -> bassBoostSupported = true
                        AudioEffect.EFFECT_TYPE_VIRTUALIZER -> virtualizerSupported = true
                        AudioEffect.EFFECT_TYPE_LOUDNESS_ENHANCER -> loudnessSupported = true
                    }
                }
            } catch (e: Exception) {
                logger.w(TAG, "Failed to query system audio effects: ${e.message}", e)
            }

            // Test BassBoost instantiation
            if (bassBoostSupported) {
                try {
                    val bb = BassBoost(0, audioSessionId)
                    bassBoostSupported = bb.strengthSupported
                    bb.release()
                } catch (e: Exception) {
                    logger.d(TAG, "BassBoost instantiation test failed: ${e.message}", e)
                    bassBoostSupported = false
                }
            }

            // Test Virtualizer instantiation
            if (virtualizerSupported) {
                try {
                    val virt = Virtualizer(0, audioSessionId)
                    virtualizerSupported = true
                    virt.release()
                } catch (e: Exception) {
                    logger.d(TAG, "Virtualizer instantiation test failed: ${e.message}", e)
                    virtualizerSupported = false
                }
            }

            // Test LoudnessEnhancer instantiation
            if (loudnessSupported) {
                try {
                    val le = LoudnessEnhancer(audioSessionId)
                    loudnessSupported = true
                    le.release()
                } catch (e: Exception) {
                    logger.d(TAG, "LoudnessEnhancer instantiation test failed: ${e.message}", e)
                    loudnessSupported = false
                }
            }

            return AudioEffectCapabilities(
                isBassBoostSupported = bassBoostSupported,
                isVirtualizerSupported = virtualizerSupported,
                isLoudnessEnhancerSupported = loudnessSupported,
            )
        }

        companion object {
            private const val TAG = "EffectCapabilities"
        }
    }
