package com.lucho314.spotter.core.security

import com.google.common.truth.Truth.assertThat
import java.io.IOException
import java.security.GeneralSecurityException
import org.junit.Test

private data class FakeHandle(val usingKeystore: Boolean = true)

class KeysetRecoveryPolicyTest {

    @Test
    fun `succeeds on the first try without ever wiping or sleeping`() {
        var buildCalls = 0
        var wipeCalls = 0
        var sleepCalls = 0
        val policy = KeysetRecoveryPolicy<FakeHandle>(
            isUsingKeystore = { it.usingKeystore },
            build = { buildCalls++; FakeHandle() },
            wipe = { wipeCalls++ },
            sleep = { sleepCalls++ },
        )

        val result = policy.run()

        assertThat(result).isEqualTo(FakeHandle())
        assertThat(buildCalls).isEqualTo(1)
        assertThat(wipeCalls).isEqualTo(0)
        assertThat(sleepCalls).isEqualTo(0)
    }

    @Test
    fun `a transient failure backs off once in the 100 to 300 ms range before retrying`() {
        var buildCalls = 0
        val sleeps = mutableListOf<Long>()
        val policy = KeysetRecoveryPolicy<FakeHandle>(
            isUsingKeystore = { it.usingKeystore },
            build = {
                buildCalls++
                if (buildCalls == 1) throw GeneralSecurityException("transient") else FakeHandle()
            },
            wipe = { throw AssertionError("wipe should not be called") },
            sleep = { sleeps.add(it) },
        )

        val result = policy.run()

        assertThat(result).isEqualTo(FakeHandle())
        assertThat(buildCalls).isEqualTo(2)
        assertThat(sleeps).hasSize(1)
        assertThat(sleeps.single()).isIn(100L..300L)
    }

    @Test
    fun `a persistent IOException wipes once and rebuilds`() {
        var buildCalls = 0
        var wipeCalls = 0
        val policy = KeysetRecoveryPolicy<FakeHandle>(
            isUsingKeystore = { it.usingKeystore },
            build = {
                buildCalls++
                if (buildCalls <= 2) throw IOException("cannot decrypt wrapped keyset") else FakeHandle()
            },
            wipe = { wipeCalls++ },
            sleep = {},
        )

        val result = policy.run()

        assertThat(result).isEqualTo(FakeHandle())
        assertThat(buildCalls).isEqualTo(3)
        assertThat(wipeCalls).isEqualTo(1)
    }

    @Test
    fun `a failure after the post-wipe rebuild propagates with the first error suppressed`() {
        var wipeCalls = 0
        val policy = KeysetRecoveryPolicy<FakeHandle>(
            isUsingKeystore = { it.usingKeystore },
            build = { throw IOException("still broken") },
            wipe = { wipeCalls++ },
            sleep = {},
        )

        try {
            policy.run()
            throw AssertionError("Expected IOException to propagate")
        } catch (e: IOException) {
            assertThat(e).hasMessageThat().isEqualTo("still broken")
            assertThat(e.suppressed).hasLength(1)
        }
        assertThat(wipeCalls).isEqualTo(1)
    }

    @Test
    fun `a non-recoverable second failure propagates without wiping, with the first error suppressed`() {
        var buildCalls = 0
        var wipeCalls = 0
        val policy = KeysetRecoveryPolicy<FakeHandle>(
            isUsingKeystore = { it.usingKeystore },
            build = {
                buildCalls++
                throw IllegalArgumentException("not a recognized recovery case")
            },
            wipe = { wipeCalls++ },
            sleep = {},
        )

        try {
            policy.run()
            throw AssertionError("Expected IllegalArgumentException to propagate")
        } catch (e: IllegalArgumentException) {
            assertThat(e).hasMessageThat().isEqualTo("not a recognized recovery case")
            assertThat(e.suppressed).hasLength(1)
        }
        assertThat(buildCalls).isEqualTo(2)
        assertThat(wipeCalls).isEqualTo(0)
    }

    @Test
    fun `a handle not backed by the keystore wipes the cleartext keyset and is rejected`() {
        var wipeCalls = 0
        val policy = KeysetRecoveryPolicy<FakeHandle>(
            isUsingKeystore = { it.usingKeystore },
            build = { FakeHandle(usingKeystore = false) },
            wipe = { wipeCalls++ },
            sleep = { throw AssertionError("sleep should not be called") },
        )

        try {
            policy.run()
            throw AssertionError("Expected IllegalStateException to propagate")
        } catch (e: IllegalStateException) {
            assertThat(e).hasMessageThat().contains("Android Keystore")
        }
        assertThat(wipeCalls).isEqualTo(1)
    }
}
