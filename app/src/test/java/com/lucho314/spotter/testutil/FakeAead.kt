package com.lucho314.spotter.testutil

import com.google.crypto.tink.Aead
import com.lucho314.spotter.core.security.AeadProvider
import java.security.GeneralSecurityException

/**
 * Reversible XOR "cipher" that still enforces associated-data integrity, so tests can exercise
 * [com.lucho314.spotter.core.security.EncryptedSessionManager] without touching Android Keystore.
 * The AD is appended (length-prefixed) to the ciphertext and checked on decrypt.
 */
class FakeAead(private val key: Byte = 0x5A) : Aead {

    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray {
        val xored = ByteArray(plaintext.size) { i -> (plaintext[i].toInt() xor key.toInt()).toByte() }
        val adLength = associatedData.size
        return ByteArray(4 + adLength + xored.size).also { out ->
            out[0] = (adLength shr 24).toByte()
            out[1] = (adLength shr 16).toByte()
            out[2] = (adLength shr 8).toByte()
            out[3] = adLength.toByte()
            associatedData.copyInto(out, 4)
            xored.copyInto(out, 4 + adLength)
        }
    }

    override fun decrypt(ciphertext: ByteArray, associatedData: ByteArray): ByteArray {
        if (ciphertext.size < 4) throw GeneralSecurityException("ciphertext too short")
        val adLength = ((ciphertext[0].toInt() and 0xFF) shl 24) or
            ((ciphertext[1].toInt() and 0xFF) shl 16) or
            ((ciphertext[2].toInt() and 0xFF) shl 8) or
            (ciphertext[3].toInt() and 0xFF)
        if (adLength < 0 || 4 + adLength > ciphertext.size) throw GeneralSecurityException("corrupt ciphertext")
        val storedAd = ciphertext.copyOfRange(4, 4 + adLength)
        if (!storedAd.contentEquals(associatedData)) throw GeneralSecurityException("AD mismatch")
        val xored = ciphertext.copyOfRange(4 + adLength, ciphertext.size)
        return ByteArray(xored.size) { i -> (xored[i].toInt() xor key.toInt()).toByte() }
    }
}

class FakeAeadProvider(private val aead: Aead = FakeAead()) : AeadProvider {
    override fun aead(): Aead = aead
}
