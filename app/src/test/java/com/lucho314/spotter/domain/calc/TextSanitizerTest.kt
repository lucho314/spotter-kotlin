package com.lucho314.spotter.domain.calc

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TextSanitizerTest {

    @Test
    fun `singleLine collapses control characters and whitespace into a single space`() {
        assertThat(TextSanitizer.singleLine("Push\nDay\t ", 50)).isEqualTo("Push Day")
    }

    @Test
    fun `singleLine returns null for blank or null input`() {
        assertThat(TextSanitizer.singleLine("   ", 50)).isNull()
        assertThat(TextSanitizer.singleLine(null, 50)).isNull()
    }

    @Test
    fun `singleLine strips bidi control characters`() {
        assertThat(TextSanitizer.singleLine("‮abc", 50)).isEqualTo("abc")
    }

    @Test
    fun `singleLine truncation never splits a surrogate pair`() {
        // "abc" + an emoji (surrogate pair) right at the maxLength boundary.
        val emoji = "😀"
        val result = TextSanitizer.singleLine("abc$emoji", 4)
        assertThat(result).isEqualTo("abc")
    }

    @Test
    fun `multiLine keeps newlines, normalizes CRLF and CR, and caps at 2 consecutive newlines`() {
        assertThat(TextSanitizer.multiLine("line1\r\nline2\rline3", 100)).isEqualTo("line1\nline2\nline3")
        assertThat(TextSanitizer.multiLine("line1\n\n\n\nline2", 100)).isEqualTo("line1\n\nline2")
    }

    @Test
    fun `isUuid accepts valid uuids regardless of case`() {
        assertThat(TextSanitizer.isUuid("123e4567-e89b-12d3-a456-426614174000")).isTrue()
        assertThat(TextSanitizer.isUuid("123E4567-E89B-12D3-A456-426614174000")).isTrue()
    }

    @Test
    fun `isUuid rejects malformed values`() {
        assertThat(TextSanitizer.isUuid("")).isFalse()
        assertThat(TextSanitizer.isUuid("r1")).isFalse()
        assertThat(TextSanitizer.isUuid("{123e4567-e89b-12d3-a456-426614174000}")).isFalse()
        assertThat(TextSanitizer.isUuid("123e4567-e89b-12d3-a456 426614174000")).isFalse()
        assertThat(TextSanitizer.isUuid(null)).isFalse()
    }
}
