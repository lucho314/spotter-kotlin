package com.lucho314.spotter.feature.auth

import com.google.common.truth.Truth.assertThat
import java.security.SecureRandom
import org.junit.Test

class NonceGeneratorTest {

    @Test
    fun `sha256Hex matches a known test vector`() {
        val hash = NonceGenerator.sha256Hex("hello")

        assertThat(hash).isEqualTo("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824")
    }

    @Test
    fun `sha256Hex of the empty string matches its known test vector`() {
        val hash = NonceGenerator.sha256Hex("")

        assertThat(hash).isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
    }

    @Test
    fun `generate produces a 64-char raw nonce and its sha256 hash`() {
        val pair = NonceGenerator(SecureRandom()).generate()

        assertThat(pair.rawNonce).hasLength(64)
        assertThat(pair.hashedNonce).isEqualTo(NonceGenerator.sha256Hex(pair.rawNonce))
    }
}
