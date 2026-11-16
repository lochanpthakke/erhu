package com.erhu.erhu.core.playback

import com.erhu.erhu.core.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(manifest = org.robolectric.annotation.Config.NONE, sdk = [34])
class MediaItemArtworkTest {
    private val song =
        Song(
            id = 7,
            title = "t",
            artist = "a",
            album = "b",
            durationMs = 1000,
            uri = "content://media/external/audio/media/7",
            artworkUri = "content://media/external/audio/albumart/3",
        )

    @Test
    fun artworkUri_isNotPublishedToThePlatformSession() {
        // Media3 copies MediaMetadata.artworkUri into three platform metadata keys, and
        // system_server resolves each content URI's provider on every setMetadata call.
        assertNull(song.toMediaItem().mediaMetadata.artworkUri)
    }

    @Test
    fun artworkUri_survivesTheQueueRoundTrip() {
        val item = song.toMediaItem()
        assertEquals(song.artworkUri, item.toSongOrNull()?.artworkUri)
        assertEquals(song.artworkUri, item.mediaMetadata.extras?.artworkUriFromExtras())
    }

    @Test
    fun missingArtwork_staysMissing() {
        val item = song.copy(artworkUri = null).toMediaItem()
        assertNull(item.toSongOrNull()?.artworkUri)
        assertNull(item.mediaMetadata.extras?.artworkUriFromExtras())
    }
}
