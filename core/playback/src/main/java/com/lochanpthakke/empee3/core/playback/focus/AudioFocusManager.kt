package com.lochanpthakke.empee3.core.playback.focus

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import com.lochanpthakke.empee3.core.common.logging.Logger
import com.lochanpthakke.empee3.core.playback.PlaybackController
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AudioFocusManager"
private const val DUCK_VOLUME = 0.2f
private const val FULL_VOLUME = 1.0f

@Singleton
class AudioFocusManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val playbackController: PlaybackController,
        private val logger: Logger,
    ) {
        private val audioManager: AudioManager =
            context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // Option A: this flag is the single source of truth for "should I resume
        // on regain". Only ever set true right before a TRANSIENT-cause pause,
        // and consumed (reset to false) on regain or on permanent loss.
        private var resumeOnFocusGain = false

        private val focusChangeListener =
            AudioManager.OnAudioFocusChangeListener { focusChange ->
                when (focusChange) {
                    AudioManager.AUDIOFOCUS_LOSS -> {
                        logger.d(TAG, "AUDIOFOCUS_LOSS - pausing, no auto-resume")
                        resumeOnFocusGain = false
                        playbackController.pause()
                    }

                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                        logger.d(TAG, "AUDIOFOCUS_LOSS_TRANSIENT - pausing, will auto-resume")
                        resumeOnFocusGain = playbackController.playbackState.value.status ==
                            com.lochanpthakke.empee3.core.model.PlaybackStatus.PLAYING
                        playbackController.pause()
                    }

                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                        logger.d(TAG, "AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK - ducking volume")
                        playbackController.setVolume(DUCK_VOLUME)
                    }

                    AudioManager.AUDIOFOCUS_GAIN -> {
                        logger.d(TAG, "AUDIOFOCUS_GAIN - restoring volume, resume=$resumeOnFocusGain")
                        playbackController.setVolume(FULL_VOLUME)
                        if (resumeOnFocusGain) {
                            resumeOnFocusGain = false
                            playbackController.play()
                        }
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
            resumeOnFocusGain = false
        }
    }
