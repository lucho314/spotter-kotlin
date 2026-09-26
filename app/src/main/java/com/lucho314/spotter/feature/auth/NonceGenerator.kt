package com.lucho314.spotter.feature.auth

import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject

data class NoncePair(val rawNonce: String, val hashedNonce: String)

/**
 * Generates the raw/hashed nonce pair used for Google Sign-In via Credential Manager: the hashed
 * nonce is sent to Google, the raw nonce to Supabase so it can verify the ID token's `nonce` claim.
 */
class NonceGenerator @Inject constructor(
    private val random: SecureRandom,
) {
    fun generate(): NoncePair {
        val bytes = ByteArray(32).also(random::nextBytes)
        val rawNonce = bytes.toHex()
        val hashedNonce = sha256Hex(rawNonce)
        return NoncePair(rawNonce, hashedNonce)
    }

    companion object {
        fun sha256Hex(input: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
            return digest.toHex()
        }
    }
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
