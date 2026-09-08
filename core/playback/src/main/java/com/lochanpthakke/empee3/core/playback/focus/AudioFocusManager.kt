
package com.lochanpthakke.empee3.core.playback.focus

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import com.lochanpthakke.empee3.core.common.logging.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AudioFocusManager"

@Singleton
class AudioFocusManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val logger: Logger,
    ) {
        private val audioManager: AudioManager =
            context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        private var listener: AudioFocusListener? = null

        fun setListener(listener: AudioFocusListener) {
            this.listener = listener
        }

        private val focusChangeListener =
            AudioManager.OnAudioFocusChangeListener { focusChange ->
                when (focusChange) {
                    AudioManager.AUDIOFOCUS_LOSS -> {
                        logger.d(TAG, "AUDIOFOCUS_LOSS - pausing, no auto-resume")
                        listener?.onFocusLost()
                    }

                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                        logger.d(TAG, "AUDIOFOCUS_LOSS_TRANSIENT - pausing, will auto-resume")
                        listener?.onFocusLostTransient()
                    }

                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                        logger.d(TAG, "AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK - ducking volume")
                        listener?.onFocusLostTransientCanDuck()
                    }

                    AudioManager.AUDIOFOCUS_GAIN -> {
                        logger.d(TAG, "AUDIOFOCUS_GAIN - restoring volume")
                        listener?.onFocusGained()
                    }
                }
            }

        private var focusRequest: AudioFocusRequest? = null

        fun requestFocus(): Boolean {
            val request =
                AudioFocusRequest
                    .Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(
                        AudioAttributes
                            .Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build(),
                    ).setOnAudioFocusChangeListener(focusChangeListener)
                    .setWillPauseWhenDucked(false)
                    .build()

            focusRequest = request
            val result = audioManager.requestAudioFocus(request)
            val granted = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            logger.d(TAG, "requestFocus() granted=$granted")
            return granted
        }

        fun abandonFocus() {
            focusRequest?.let {
                audioManager.abandonAudioFocusRequest(it)
                logger.d(TAG, "abandonFocus()")
            }
            focusRequest = null
        }
    }
