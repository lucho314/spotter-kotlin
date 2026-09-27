package com.lucho314.spotter.core.navigation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DeepLinkParserTest {

    @Test
    fun `auth callback is parsed`() {
        val link = DeepLinkParser.parse("spotter://auth/callback")

        assertThat(link).isEqualTo(DeepLink.AuthCallback)
    }

    @Test
    fun `auth callback with a PKCE code query is still parsed`() {
        val link = DeepLinkParser.parse("spotter://auth/callback?code=abc123&state=xyz")

        assertThat(link).isEqualTo(DeepLink.AuthCallback)
    }

    @Test
    fun `import routine with a valid code is parsed`() {
        val link = DeepLinkParser.parse("spotter://import/K7MN3QXP")

        assertThat(link).isEqualTo(DeepLink.ImportRoutine(requireNotNull(com.lucho314.spotter.domain.model.ShareCode.parse("K7MN3QXP"))))
    }

    @Test
    fun `wrong scheme is ignored`() {
        assertThat(DeepLinkParser.parse("https://auth/callback")).isNull()
    }

    @Test
    fun `unknown host is ignored`() {
        assertThat(DeepLinkParser.parse("spotter://unknown/whatever")).isNull()
    }

    @Test
    fun `import with an invalid code is ignored`() {
        assertThat(DeepLinkParser.parse("spotter://import/not-a-code")).isNull()
    }

    @Test
    fun `import with multiple path segments is ignored`() {
        assertThat(DeepLinkParser.parse("spotter://import/K7MN3QXP/extra")).isNull()
    }

    @Test
    fun `import with no path segment is ignored`() {
        assertThat(DeepLinkParser.parse("spotter://import/")).isNull()
        assertThat(DeepLinkParser.parse("spotter://import")).isNull()
    }

    @Test
    fun `null or blank input is ignored`() {
        assertThat(DeepLinkParser.parse(null)).isNull()
        assertThat(DeepLinkParser.parse("")).isNull()
    }

    @Test
    fun `malformed uri is ignored`() {
        assertThat(DeepLinkParser.parse("not a uri at all ://")).isNull()
    }

    @Test
    fun `DeepLinks importRoutine round-trips through DeepLinkParser for a client-format code`() {
        val code = requireNotNull(com.lucho314.spotter.domain.model.ShareCode.parse("K7MN3QXP"))

        val link = DeepLinkParser.parse(DeepLinks.importRoutine(code.value))

        assertThat(link).isEqualTo(DeepLink.ImportRoutine(code))
    }

    @Test
    fun `DeepLinks importRoutine round-trips for a legacy lowercase hex code`() {
        val code = requireNotNull(com.lucho314.spotter.domain.model.ShareCode.parse("a1b2c3d4e5f6"))

        val link = DeepLinkParser.parse(DeepLinks.importRoutine(code.value))

        assertThat(link).isEqualTo(DeepLink.ImportRoutine(code))
    }
}
