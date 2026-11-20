package com.erhu.erhu.core.playback

import androidx.media3.common.PlaybackException

internal enum class ErrorResponse {
    /** The entry itself is broken or gone: move on to the next one. */
    SKIP,

    /** The network is the problem: stay on this entry, paused, until the user plays again. */
    WAIT,
}

/**
 * A device file that fails is skipped, as always. A remote track that fails only because the
 * connection dropped or timed out must not be skipped: every following remote track would fail the
 * same way and the whole queue would be discarded in seconds. Remote tracks the server refuses
 * (deleted, access revoked, sign-in expired) are skipped like a missing file.
 */
internal fun errorResponse(
    isRemote: Boolean,
    errorCode: Int,
): ErrorResponse =
    if (isRemote &&
        (
            errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
        )
    ) {
        ErrorResponse.WAIT
    } else {
        ErrorResponse.SKIP
    }
