package com.erhu.erhu.core.playback

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Metadata
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import androidx.media3.extractor.metadata.id3.InternalFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import com.erhu.erhu.core.model.ReplayGainMode
import org.junit.Assert.*
import org.junit.Test

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(manifest = org.robolectric.annotation.Config.NONE, sdk = [34])
class ReplayGainMetadataTest {
    private fun group(mime: String, selected: Boolean, metadata: Metadata) = Tracks.Group(
        TrackGroup(Format.Builder().setSampleMimeType(mime).setMetadata(metadata).build()),
        false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(selected),
    )
    @Test fun selectedAudioReadsVorbisId3AndMp4FreeformMetadata() {
        val tags = Metadata(
            VorbisComment("REPLAYGAIN_ALBUM_GAIN", "-3 dB"),
            TextInformationFrame("TXXX", "REPLAYGAIN_TRACK_GAIN", listOf("-6 dB")),
            InternalFrame("com.apple.iTunes", "replaygain_track_peak", "0.8"),
        )
        val result = replayGainFromTracks(Tracks(listOf(group("audio/flac", true, tags))))
        assertEquals(-3f, result.albumDb!!, 0f); assertEquals(-6f, result.trackDb!!, 0f)
        assertEquals(0.8f, result.trackPeak!!, 0f)
        assertTrue(result.gain(ReplayGainMode.TRACK) < result.gain(ReplayGainMode.ALBUM))
    }
    @Test fun unselectedAndVideoTagsCannotNormalizeTheCurrentSong() {
        val tags = Metadata(VorbisComment("REPLAYGAIN_TRACK_GAIN", "20 dB"))
        val result = replayGainFromTracks(Tracks(listOf(group("audio/flac", false, tags), group("video/avc", true, tags))))
        assertNull(result.trackDb)
        assertEquals(1f, result.gain(ReplayGainMode.TRACK), 0f)
        assertEquals(1f, replayGainFromTracks(Tracks.EMPTY).gain(ReplayGainMode.ALBUM), 0f)
    }
}
