package com.lucho314.spotter.core.config

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppConfigTest {

    private fun config(url: String, key: String = "anon-key") = AppConfig(
        supabaseUrl = url,
        supabaseAnonKey = key,
        googleWebClientId = null,
    )

    @Test
    fun `a plain project URL is valid`() {
        assertThat(config("https://abcdefgh.example.test").isValid).isTrue()
        assertThat(config("https://abcdefgh.example.test/").isValid).isTrue()
    }

    @Test
    fun `an empty url is invalid`() {
        assertThat(config("").isValid).isFalse()
    }

    @Test
    fun `a blank key is invalid`() {
        assertThat(config("https://abcdefgh.example.test", key = "").isValid).isFalse()
        assertThat(config("https://abcdefgh.example.test", key = "   ").isValid).isFalse()
    }

    @Test
    fun `http (non-https) is invalid`() {
        assertThat(config("http://abcdefgh.example.test").isValid).isFalse()
    }

    @Test
    fun `a url with a rest or auth path is invalid`() {
        // supabase-kt's SupabaseClientBuilder throws for these instead of degrading gracefully.
        assertThat(config("https://abcdefgh.example.test/rest/v1").isValid).isFalse()
        assertThat(config("https://abcdefgh.example.test/auth/v1").isValid).isFalse()
    }

    @Test
    fun `a url without a host is invalid`() {
        assertThat(config("https:///no-host").isValid).isFalse()
    }

    @Test
    fun `a malformed url is invalid`() {
        assertThat(config("not a url ://").isValid).isFalse()
    }
}
