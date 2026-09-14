@file:Suppress("detekt:All")

package com.lochanpthakke.empee3.core.sound.effects

import android.media.audiofx.BassBoost
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import com.lochanpthakke.empee3.core.common.logging.Logger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@Suppress("MagicNumber", "TooManyFunctions", "LongMethod", "CyclomaticComplexMethod", "TooGenericExceptionCaught")
class AudioEffectManager
    @Inject
    constructor(
        private val effectCapabilities: EffectCapabilities,
        private val logger: Logger,
    ) {
        private var currentSessionId: Int = 0
        private var activeCapabilities = AudioEffectCapabilities()

        private var bassBoost: BassBoost? = null
        private var virtualizer: Virtualizer? = null
        private var loudnessEnhancer: LoudnessEnhancer? = null

        var isBassBoostEnabled: Boolean = false
            private set
        var bassBoostStrength: Short = 0
            private set

        var isVirtualizerEnabled: Boolean = false
            private set
        var virtualizerStrength: Short = 0
            private set

        var isLoudnessEnabled: Boolean = false
            private set
        var loudnessGainMb: Int = 0
            private set

        fun attachToAudioSession(audioSessionId: Int): AudioEffectCapabilities {
            if (audioSessionId <= 0) return AudioEffectCapabilities()
            if (currentSessionId == audioSessionId) return activeCapabilities

            releaseEffects()
            currentSessionId = audioSessionId
            activeCapabilities = effectCapabilities.checkCapabilities(audioSessionId)

            if (activeCapabilities.isBassBoostSupported) {
                try {
                    bassBoost = BassBoost(0, audioSessionId)
                    applyBassBoostState()
                } catch (e: Exception) {
                    logger.w(TAG, "Failed to initialize BassBoost: ${e.message}", e)
                }
            }

            if (activeCapabilities.isVirtualizerSupported) {
                try {
                    virtualizer = Virtualizer(0, audioSessionId)
                    applyVirtualizerState()
                } catch (e: Exception) {
                    logger.w(TAG, "Failed to initialize Virtualizer: ${e.message}", e)
                }
            }

            if (activeCapabilities.isLoudnessEnhancerSupported) {
                try {
                    loudnessEnhancer = LoudnessEnhancer(audioSessionId)
                    applyLoudnessState()
                } catch (e: Exception) {
                    logger.w(TAG, "Failed to initialize LoudnessEnhancer: ${e.message}", e)
                }
            }

            return activeCapabilities
        }

        fun setBassBoost(
            enabled: Boolean,
            strength: Short,
        ) {
            isBassBoostEnabled = enabled
            bassBoostStrength = strength
            applyBassBoostState()
        }

        fun setVirtualizer(
            enabled: Boolean,
            strength: Short,
        ) {
            isVirtualizerEnabled = enabled
            virtualizerStrength = strength
            applyVirtualizerState()
        }

        fun setLoudnessEnhancer(
            enabled: Boolean,
            gainMb: Int,
        ) {
            isLoudnessEnabled = enabled
            loudnessGainMb = gainMb
            applyLoudnessState()
        }

        private fun applyBassBoostState() {
            val bb = bassBoost ?: return
            try {
                bb.setStrength(bassBoostStrength)
                bb.enabled = isBassBoostEnabled
            } catch (e: Exception) {
                logger.w(TAG, "Error applying BassBoost state: ${e.message}", e)
            }
        }

        private fun applyVirtualizerState() {
            val virt = virtualizer ?: return
            try {
                virt.setStrength(virtualizerStrength)
                virt.enabled = isVirtualizerEnabled
            } catch (e: Exception) {
                logger.w(TAG, "Error applying Virtualizer state: ${e.message}", e)
            }
        }

        private fun applyLoudnessState() {
            val le = loudnessEnhancer ?: return
            try {
                le.setTargetGain(loudnessGainMb)
                le.enabled = isLoudnessEnabled
            } catch (e: Exception) {
                logger.w(TAG, "Error applying LoudnessEnhancer state: ${e.message}", e)
            }
        }

        fun releaseEffects() {
            try {
                bassBoost?.release()
                virtualizer?.release()
                loudnessEnhancer?.release()
            } catch (e: Exception) {
                logger.w(TAG, "Error releasing audio effects: ${e.message}", e)
            } finally {
                bassBoost = null
                virtualizer = null
                loudnessEnhancer = null
            }
        }

        companion object {
            private const val TAG = "AudioEffectManager"
        }
    }
