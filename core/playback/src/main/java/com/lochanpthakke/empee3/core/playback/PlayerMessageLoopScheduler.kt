package com.lochanpthakke.empee3.core.playback

import android.os.Looper
import androidx.media3.exoplayer.ExoPlayer

/**
 * [LoopScheduler] on ExoPlayer's own player messages: the player delivers the message when playback
 * of the entry reaches the position (media time, so playback speed is irrelevant), again on every
 * later pass because it is kept after delivery, on the main thread where the player lives. No polling,
 * and independent of any screen: it runs as long as the player plays.
 */
internal class PlayerMessageLoopScheduler(
    private val player: ExoPlayer,
) : LoopScheduler {
    override fun scheduleAt(
        mediaItemIndex: Int,
        positionMs: Long,
        onReached: () -> Unit,
    ): Cancellable {
        val message =
            player
                .createMessage { _, _ -> onReached() }
                .setLooper(Looper.getMainLooper())
                .setPosition(mediaItemIndex, positionMs)
                .setDeleteAfterDelivery(false)
                .send()
        return Cancellable { message.cancel() }
    }
}
