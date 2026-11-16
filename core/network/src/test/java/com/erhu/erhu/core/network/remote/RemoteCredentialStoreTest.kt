package com.erhu.erhu.core.network.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.GeneralSecurityException

class RemoteCredentialStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    /** Reversible stand-in for the Keystore cipher: the store's own behaviour is what is under test. */
    private class FakeCipher : SecretCipher {
        var broken = false

        override fun encrypt(plain: ByteArray): ByteArray = plain.map { (it.toInt() xor MASK).toByte() }.toByteArray()

        override fun decrypt(sealed: ByteArray): ByteArray {
            if (broken) throw GeneralSecurityException("key lost")
            return encrypt(sealed)
        }

        private companion object {
            const val MASK = 0x5A
        }
    }

    private val cipher = FakeCipher()

    private fun store(dir: File = File(folder.root, "creds")) = RemoteCredentialStore(dir, cipher)

    @Test
    fun `a saved secret is read back per source and is not on disk in the clear`() {
        val dir = File(folder.root, "creds")
        val store = store(dir)
        store.save(1, "pässword one")
        store.save(2, "two")

        assertEquals("pässword one", store.load(1))
        assertEquals("two", store.load(2))
        assertFalse(File(dir, "1.bin").readBytes().toString(Charsets.UTF_8).contains("word"))
    }

    @Test
    fun `saving again replaces the secret and leaves no staging file`() {
        val dir = File(folder.root, "creds")
        val store = store(dir)
        store.save(1, "old")
        store.save(1, "new")

        assertEquals("new", store.load(1))
        assertEquals(listOf("1.bin"), dir.list()!!.toList())
    }

    @Test
    fun `a missing, deleted or undecryptable secret reads as absent`() {
        val store = store()
        assertNull(store.load(9))

        store.save(1, "secret")
        cipher.broken = true
        assertNull(store.load(1))

        cipher.broken = false
        store.delete(1)
        assertNull(store.load(1))
    }
}
