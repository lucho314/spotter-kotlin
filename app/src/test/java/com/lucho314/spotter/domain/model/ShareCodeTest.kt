package com.lucho314.spotter.domain.model

import com.google.common.truth.Truth.assertThat
import java.security.SecureRandom
import org.junit.Test

class ShareCodeTest {

    @Test
    fun `parse accepts an 8-char client-format code and normalizes to uppercase`() {
        val code = ShareCode.parse("k7mn3qxp")

        assertThat(code?.value).isEqualTo("K7MN3QXP")
    }

    @Test
    fun `parse accepts a 12-char lowercase hex code and keeps it lowercase`() {
        val code = ShareCode.parse("a1b2c3d4e5f6")

        assertThat(code?.value).isEqualTo("a1b2c3d4e5f6")
    }

    @Test
    fun `parse trims surrounding whitespace`() {
        val code = ShareCode.parse("  K7MN3QXP  ")

        assertThat(code?.value).isEqualTo("K7MN3QXP")
    }

    @Test
    fun `parse rejects blank input`() {
        assertThat(ShareCode.parse("")).isNull()
        assertThat(ShareCode.parse(null)).isNull()
        assertThat(ShareCode.parse("   ")).isNull()
    }

    @Test
    fun `parse rejects wrong length`() {
        assertThat(ShareCode.parse("K7MN3QX")).isNull()
        assertThat(ShareCode.parse("K7MN3QXPX")).isNull()
    }

    @Test
    fun `parse rejects ambiguous characters not in the client alphabet`() {
        // I, O, 0, 1 are excluded from the client alphabet.
        assertThat(ShareCode.parse("I7MN3QXP")).isNull()
        assertThat(ShareCode.parse("O7MN3QXP")).isNull()
        assertThat(ShareCode.parse("07MN3QXP")).isNull()
        assertThat(ShareCode.parse("17MN3QXP")).isNull()
    }

    @Test
    fun `generate produces an 8-char code from the client alphabet`() {
        val code = ShareCode.generate(SecureRandom())

        assertThat(code.value).hasLength(8)
        assertThat(code.value).matches("^[A-HJ-NP-Z2-9]{8}$")
    }
}
