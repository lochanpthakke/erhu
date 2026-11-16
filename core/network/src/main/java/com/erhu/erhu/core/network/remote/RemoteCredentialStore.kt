package com.erhu.erhu.core.network.remote

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/** Encrypts and decrypts one secret. The key never leaves the implementation. */
internal interface SecretCipher {
    @Throws(GeneralSecurityException::class)
    fun encrypt(plain: ByteArray): ByteArray

    @Throws(GeneralSecurityException::class)
    fun decrypt(sealed: ByteArray): ByteArray
}

/**
 * The secret of each remote source (a server password or API key), one file per source id under
 * `noBackupFilesDir`, encrypted with a key held in the Android Keystore. Files there are left out
 * of cloud backup and device transfer, and the key cannot be exported, so a copied file is useless
 * on another device.
 *
 * Nothing here is ever logged, and a secret that cannot be decrypted (key lost after a restore or
 * a lock-screen reset) reads as absent: the source then asks the user to sign in again.
 */
@Singleton
class RemoteCredentialStore internal constructor(
    private val directory: File,
    private val cipher: SecretCipher,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : this(File(context.noBackupFilesDir, "remote-credentials"), KeystoreCipher())

    @Synchronized
    @Throws(IOException::class)
    fun save(
        sourceId: Long,
        secret: String,
    ) {
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Could not create the credential folder")
        val sealed =
            try {
                cipher.encrypt(secret.toByteArray(Charsets.UTF_8))
            } catch (e: GeneralSecurityException) {
                throw IOException("Could not protect the credential", e)
            }
        // Written beside the target and renamed, so an interrupted save never leaves half a secret.
        val target = file(sourceId)
        val staged = File(directory, "${target.name}.tmp")
        staged.writeBytes(sealed)
        if (!staged.renameTo(target)) {
            target.delete()
            if (!staged.renameTo(target)) {
                staged.delete()
                throw IOException("Could not store the credential")
            }
        }
    }

    /** The stored secret, or null when there is none or it can no longer be read. */
    @Synchronized
    @Suppress("SwallowedException")
    fun load(sourceId: Long): String? {
        val target = file(sourceId)
        if (!target.isFile) return null
        return try {
            String(cipher.decrypt(target.readBytes()), Charsets.UTF_8)
        } catch (e: GeneralSecurityException) {
            null
        } catch (e: IOException) {
            null
        }
    }

    @Synchronized
    fun delete(sourceId: Long) {
        file(sourceId).delete()
    }

    private fun file(sourceId: Long) = File(directory, "$sourceId.bin")
}

/** AES-256-GCM with a Keystore key; each sealed value is a 12-byte nonce followed by the ciphertext. */
private class KeystoreCipher : SecretCipher {
    override fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return cipher.iv + cipher.doFinal(plain)
    }

    override fun decrypt(sealed: ByteArray): ByteArray {
        if (sealed.size <= NONCE_BYTES) throw GeneralSecurityException("Truncated credential")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, sealed, 0, NONCE_BYTES))
        return cipher.doFinal(sealed, NONCE_BYTES, sealed.size - NONCE_BYTES)
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec
                .Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_BITS)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "erhu.remote.credentials.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val NONCE_BYTES = 12
        const val TAG_BITS = 128
        const val KEY_BITS = 256
    }
}
