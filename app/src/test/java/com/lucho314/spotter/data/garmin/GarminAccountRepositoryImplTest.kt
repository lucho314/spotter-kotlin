package com.lucho314.spotter.data.garmin

import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.data.garmin.remote.GarminSsoSession
import com.lucho314.spotter.data.garmin.remote.SsoStep
import com.lucho314.spotter.domain.model.GarminConnectionState
import com.lucho314.spotter.domain.model.GarminError
import com.lucho314.spotter.domain.model.GarminLoginResult
import com.lucho314.spotter.domain.model.GarminResult
import com.lucho314.spotter.testutil.FakeGarminAuthRemoteDataSource
import com.lucho314.spotter.testutil.FakeGarminTokenStore
import com.lucho314.spotter.testutil.FakeIdGenerator
import com.lucho314.spotter.testutil.FakeLogger
import com.lucho314.spotter.testutil.FakeTimeProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

private const val USER_ID = "user-1"

class GarminAccountRepositoryImplTest {

    private val store = FakeGarminTokenStore()
    private val remote = FakeGarminAuthRemoteDataSource()
    private val timeProvider = FakeTimeProvider(instant = Instant.ofEpochSecond(1_000_000))
    private val idGenerator = FakeIdGenerator()
    private val repository = GarminAccountRepositoryImpl(remote, store, timeProvider, idGenerator, FakeLogger())

    private fun mfaSession() = GarminSsoSession(HttpClient(MockEngine) { engine { addHandler { respondOk() } } })

    @Test
    fun `a direct (no MFA) login stores tokens with the owner and autoUpload on`() = runTest {
        remote.loginResult = SsoStep.Ticket("ticket-1")
        remote.exchangeTicketResult = com.lucho314.spotter.data.garmin.remote.DiTokens("access-1", "refresh-1", "client-1", 2_000_000)

        val result = repository.login(USER_ID, "a@b.com", "pw")

        assertThat(result).isEqualTo(GarminResult.Success(GarminLoginResult.Connected))
        val stored = store.load()!!
        assertThat(stored.ownerUserId).isEqualTo(USER_ID)
        assertThat(stored.autoUpload).isTrue()
        assertThat(stored.needsReconnect).isFalse()
        assertThat(stored.accessToken).isEqualTo("access-1")
    }

    @Test
    fun `the full MFA flow connects and clears the pending challenge`() = runTest {
        remote.loginResult = SsoStep.Mfa(mfaSession(), "email")

        val loginResult = repository.login(USER_ID, "a@b.com", "pw") as GarminResult.Success
        val mfaRequired = loginResult.value as GarminLoginResult.MfaRequired

        remote.verifyMfaResult = "ticket-mfa"
        val verifyResult = repository.verifyMfa(USER_ID, mfaRequired.challengeId, "123456")

        assertThat(verifyResult).isEqualTo(GarminResult.Success(Unit))
        assertThat(store.load()?.ownerUserId).isEqualTo(USER_ID)

        // The challenge was consumed: verifying again reports it as expired/unknown.
        val secondVerify = repository.verifyMfa(USER_ID, mfaRequired.challengeId, "000000")
        assertThat(secondVerify).isEqualTo(GarminResult.Failure(GarminError.MfaSessionExpired))
    }

    @Test
    fun `an expired MFA challenge reports MfaSessionExpired`() = runTest {
        remote.loginResult = SsoStep.Mfa(mfaSession(), "email")
        val loginResult = repository.login(USER_ID, "a@b.com", "pw") as GarminResult.Success
        val mfaRequired = (loginResult.value as GarminLoginResult.MfaRequired)

        timeProvider.instant = timeProvider.instant.plusSeconds(700) // past the 600s TTL

        val result = repository.verifyMfa(USER_ID, mfaRequired.challengeId, "123456")

        assertThat(result).isEqualTo(GarminResult.Failure(GarminError.MfaSessionExpired))
    }

    @Test
    fun `an invalid MFA code keeps the challenge so the user can retry`() = runTest {
        remote.loginResult = SsoStep.Mfa(mfaSession(), "email")
        val loginResult = repository.login(USER_ID, "a@b.com", "pw") as GarminResult.Success
        val mfaRequired = loginResult.value as GarminLoginResult.MfaRequired

        remote.verifyMfaException = GarminApiException(GarminError.InvalidMfaCode)
        val firstAttempt = repository.verifyMfa(USER_ID, mfaRequired.challengeId, "000000")
        assertThat(firstAttempt).isEqualTo(GarminResult.Failure(GarminError.InvalidMfaCode))

        remote.verifyMfaException = null
        remote.verifyMfaResult = "ticket-mfa"
        val secondAttempt = repository.verifyMfa(USER_ID, mfaRequired.challengeId, "123456")
        assertThat(secondAttempt).isEqualTo(GarminResult.Success(Unit))
    }

    @Test
    fun `disconnect clears the stored tokens`() = runTest {
        remote.loginResult = SsoStep.Ticket("ticket-1")
        repository.login(USER_ID, "a@b.com", "pw")
        assertThat(store.load()).isNotNull()

        repository.disconnect()

        assertThat(store.load()).isNull()
    }

    @Test
    fun `observeConnection reports NotConnected when the stored tokens belong to a different user`() = runTest {
        remote.loginResult = SsoStep.Ticket("ticket-1")
        repository.login("other-user", "a@b.com", "pw")

        val state = repository.observeConnection(USER_ID).first()

        assertThat(state).isEqualTo(GarminConnectionState.NotConnected)
    }
}
