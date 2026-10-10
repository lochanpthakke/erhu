package com.lochanpthakke.empee3.feature.transfer.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferCoreTest {
    @Test
    fun `sessions get fresh random credentials and stop working when closed or expired`() {
        val a = TransferSession.create(nowMs = 0)
        val b = TransferSession.create(nowMs = 0)
        assertNotEquals(a.token, b.token)
        assertTrue(a.token.length >= 43) // 256 bits, base64url
        assertTrue(a.pairingCode.matches(Regex("\\d{6}")))
        assertFalse(a.toString().contains(a.token))

        assertTrue(a.isAuthorized(a.token, 1))
        assertFalse(a.isAuthorized(b.token, 1))
        assertFalse(a.isAuthorized(null, 1))
        assertFalse(a.isAuthorized(a.token.dropLast(1), 1))
        assertFalse(a.isAuthorized(a.token, TransferSession.DEFAULT_LIFETIME_MS)) // expired

        a.close()
        assertFalse(a.isAuthorized(a.token, 1))
        assertEquals(PairResult.Closed, a.pair(a.pairingCode, 1))
    }

    @Test
    fun `the pairing code locks after repeated wrong guesses`() {
        val session = TransferSession.create(nowMs = 0, maxPairAttempts = 3)
        val wrong = if (session.pairingCode == "000000") "000001" else "000000"
        assertEquals(PairResult.WrongCode, session.pair(wrong, 1))
        assertEquals(PairResult.WrongCode, session.pair(null, 1))
        assertEquals(PairResult.Locked, session.pair(wrong, 1))
        // Even the right code no longer works by typing; the QR token still does.
        assertEquals(PairResult.Locked, session.pair(session.pairingCode, 1))
        assertTrue(session.isAuthorized(session.token, 1))

        val fresh = TransferSession.create(nowMs = 0)
        assertEquals(PairResult.Paired(fresh.token), fresh.pair(" ${fresh.pairingCode} ", 1))
    }

    @Test
    fun `file names lose any path and unsafe characters`() {
        assertEquals("song.mp3", TransferFiles.sanitize("../../../data/data/com.app/song.mp3"))
        assertEquals("song.mp3", TransferFiles.sanitize("C:\\Users\\me\\Music\\song.mp3"))
        assertEquals("evil.flac", TransferFiles.sanitize("/sdcard/../../evil.flac"))
        assertEquals("a_b_c.mp3", TransferFiles.sanitize("a<b>c.mp3"))
        assertEquals("Track 01.m4a", TransferFiles.sanitize("  Track   01.M4A  "))
        assertEquals("hidden.mp3", TransferFiles.sanitize("...hidden.mp3"))
        assertEquals("nul_byte.mp3", TransferFiles.sanitize("nul\u0000byte.mp3"))
        assertNull(TransferFiles.sanitize(".."))
        assertNull(TransferFiles.sanitize("../"))
        assertNull(TransferFiles.sanitize(".mp3"))
        assertNull(TransferFiles.sanitize("song"))
        assertNull(TransferFiles.sanitize("payload.apk"))
        assertNull(TransferFiles.sanitize("song.mp3.exe"))
        assertNull(TransferFiles.sanitize(""))
        assertNull(TransferFiles.sanitize(null))
        assertEquals(120 + 4, TransferFiles.sanitize("x".repeat(500) + ".mp3")!!.length)
        assertEquals("Song (2).mp3", TransferFiles.numbered("Song.mp3", 2))
        assertEquals("audio/flac", TransferFiles.mimeTypeOf("a.flac"))
    }

    @Test
    fun `content must match the claimed audio format`() {
        fun header(vararg parts: String) = parts.joinToString("").toByteArray(Charsets.ISO_8859_1).copyOf(12)
        assertTrue(TransferFiles.looksLikeAudio("a.mp3", header("ID3"), 12))
        assertTrue(TransferFiles.looksLikeAudio("a.mp3", byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0, 0), 4))
        assertTrue(TransferFiles.looksLikeAudio("a.flac", header("fLaC"), 12))
        assertTrue(TransferFiles.looksLikeAudio("a.m4a", header("\u0000\u0000\u0000\u0020ftypM4A "), 12))
        assertTrue(TransferFiles.looksLikeAudio("a.ogg", header("OggS"), 12))
        assertTrue(TransferFiles.looksLikeAudio("a.opus", header("OggS"), 12))
        assertTrue(TransferFiles.looksLikeAudio("a.wav", header("RIFF\u0000\u0000\u0000\u0000WAVE"), 12))
        // A renamed executable, an HTML page, or too little data.
        assertFalse(TransferFiles.looksLikeAudio("a.mp3", header("MZ"), 12))
        assertFalse(TransferFiles.looksLikeAudio("a.flac", header("<html>"), 12))
        assertFalse(TransferFiles.looksLikeAudio("a.wav", header("RIFF\u0000\u0000\u0000\u0000AVI "), 12))
        assertFalse(TransferFiles.looksLikeAudio("a.mp3", header("ID3"), 2))
    }

    @Test
    fun `transfer states only move forward and finish once`() {
        val registry = TransferRegistry()
        val id = registry.begin("a.mp3", 100)
        assertTrue(registry.progress(id, 40))
        assertTrue(registry.finalizing(id))
        assertFalse(registry.progress(id, 50)) // no progress after finalizing
        assertTrue(registry.completed(id, "a.mp3"))
        assertFalse(registry.completed(id, "a (2).mp3")) // never finalized twice
        assertFalse(registry.failed(id, "late failure"))
        assertFalse(registry.cancelled(id))
        assertEquals(TransferStatus.COMPLETED, registry.items.value.single().status)
        assertEquals(100L, registry.items.value.single().receivedBytes)

        val other = registry.begin("b.mp3", 10)
        registry.cancelAllActive()
        assertEquals(TransferStatus.CANCELLED, registry.items.value.single { it.id == other }.status)
        assertEquals(TransferStatus.COMPLETED, registry.items.value.single { it.id == id }.status)
        assertFalse(registry.completed(other, "b.mp3"))
    }
}
