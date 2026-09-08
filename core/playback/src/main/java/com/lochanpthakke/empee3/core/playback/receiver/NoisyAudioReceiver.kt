package com.lochanpthakke.empee3.core.playback.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import com.lochanpthakke.empee3.core.common.logging.Logger
import com.lochanpthakke.empee3.core.playback.PlaybackController

private const val TAG = "NoisyAudioReceiver"

/**
 * Handles ACTION_AUDIO_BECOMING_NOISY — fired whenever the active audio output
 * route disappears (wired headphones unplugged, Bluetooth device disconnected,
 * USB-C DAC removed, etc). Covers both the "headphones" and "Bluetooth" cases
 * from the spec as a single behavior: pause playback so audio doesn't suddenly
 * blast out of the device speaker.
 *
 * Must be registered dynamically (context.registerReceiver at runtime) — this
 * is an implicit broadcast, which Android will NOT deliver to a manifest
 * (static) receiver on API 26+.
 */
class NoisyAudioReceiver(
    private val playbackController: PlaybackController,
    private val logger: Logger,
) : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
            logger.d(TAG, "ACTION_AUDIO_BECOMING_NOISY received - pausing playback")
            playbackController.pause()
        }
    }
}
