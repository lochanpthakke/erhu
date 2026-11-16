package com.erhu.erhu.core.playback

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackErrorsTest {
    @Test
    fun `a remote track waits when the connection fails or times out`() {
        assertEquals(ErrorResponse.WAIT, errorResponse(true, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED))
        assertEquals(ErrorResponse.WAIT, errorResponse(true, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT))
    }

    @Test
    fun `a remote track the server refuses is skipped`() {
        assertEquals(ErrorResponse.SKIP, errorResponse(true, PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS))
        assertEquals(ErrorResponse.SKIP, errorResponse(true, PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND))
        assertEquals(ErrorResponse.SKIP, errorResponse(true, PlaybackException.ERROR_CODE_IO_UNSPECIFIED))
    }

    @Test
    fun `device files are always skipped as before`() {
        assertEquals(ErrorResponse.SKIP, errorResponse(false, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED))
        assertEquals(ErrorResponse.SKIP, errorResponse(false, PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND))
    }
}
