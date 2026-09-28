package com.lucho314.spotter.data.garmin

import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.data.garmin.local.StoredGarminTokens
import com.lucho314.spotter.data.garmin.remote.DiTokens
import com.lucho314.spotter.domain.model.GarminError
import com.lucho314.spotter.domain.model.GarminResult
import com.lucho314.spotter.testutil.FakeGarminAuthRemoteDataSource
import com.lucho314.spotter.testutil.FakeGarminTokenStore
import com.lucho314.spotter.testutil.FakeTimeProvider
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Test

private const val USER_ID = "user-1"

class GarminTokenManagerTest {

    private val timeProvider = FakeTimeProvider(instant = Instant.ofEpochSecond(1_000_000))
    private val remote = FakeGarminAuthRemoteDataSource()

    private fun tokens(
        ownerUserId: String = USER_ID,
        accessToken: String = "access-old",
        refreshToken: String? = "refresh-old",
        expiresAt: Long = timeProvider.instant.epochSecond + 3600,
        needsReconnect: Boolean = false,
    ) = StoredGarminTokens(
        ownerUserId = ownerUserId, accessToken = accessToken, refreshToken = refreshToken, clientId = "client-old",
        accessExpiresAtEpochSec = expiresAt, needsReconnect = needsReconnect, connectedAtEpochMs = 0,
    )

    @Test
    fun `a token that is not near expiry is returned as-is, without refreshing`() = runTest {
        val store = FakeGarminTokenStore(tokens(expiresAt = timeProvider.instant.epochSecond + 3600))
        val manager = GarminTokenManager(store, remote, timeProvider)

        val result = manager.validAccessToken(USER_ID)

        assertThat(result).isEqualTo(GarminResult.Success("access-old"))
        assertThat(remote.refreshCallCount).isEqualTo(0)
    }

    @Test
    fun `a token expiring within the refresh margin triggers a refresh and persists it`() = runTest {
        val store = FakeGarminTokenStore(tokens(expiresAt = timeProvider.instant.epochSecond + 60))
        remote.refreshResult = DiTokens("access-new", "refresh-new", "client-new", timeProvider.instant.epochSecond + 3600)
        val manager = GarminTokenManager(store, remote, timeProvider)

        val result = manager.validAccessToken(USER_ID)

        assertThat(result).isEqualTo(GarminResult.Success("access-new"))
        assertThat(store.load()?.accessToken).isEqualTo("access-new")
        assertThat(store.load()?.refreshToken).isEqualTo("refresh-new")
        assertThat(store.load()?.clientId).isEqualTo("client-new")
    }

    @Test
    fun `a refresh response without a new refresh token keeps the previous one`() = runTest {
        val store = FakeGarminTokenStore(tokens(expiresAt = timeProvider.instant.epochSecond + 60, refreshToken = "refresh-old"))
        remote.refreshResult = DiTokens("access-new", refreshToken = null, "client-new", timeProvider.instant.epochSecond + 3600)
        val manager = GarminTokenManager(store, remote, timeProvider)

        manager.validAccessToken(USER_ID)

        assertThat(store.load()?.refreshToken).isEqualTo("refresh-old")
    }

    @Test
    fun `a rejected refresh marks needsReconnect and reports ReauthRequired`() = runTest {
        val store = FakeGarminTokenStore(tokens(expiresAt = timeProvider.instant.epochSecond + 60))
        remote.refreshException = GarminApiException(GarminError.ReauthRequired)
        val manager = GarminTokenManager(store, remote, timeProvider)

        val result = manager.validAccessToken(USER_ID)

        assertThat(result).isEqualTo(GarminResult.Failure(GarminError.ReauthRequired))
        assertThat(store.load()?.needsReconnect).isTrue()
    }

    @Test
    fun `a network error during refresh leaves the stored tokens untouched`() = runTest {
        val original = tokens(expiresAt = timeProvider.instant.epochSecond + 60)
        val store = FakeGarminTokenStore(original)
        remote.refreshException = GarminApiException(GarminError.Network)
        val manager = GarminTokenManager(store, remote, timeProvider)

        val result = manager.validAccessToken(USER_ID)

        assertThat(result).isEqualTo(GarminResult.Failure(GarminError.Network))
        assertThat(store.load()).isEqualTo(original)
    }

    @Test
    fun `tokens owned by a different user report NotConnected`() = runTest {
        val store = FakeGarminTokenStore(tokens(ownerUserId = "other-user"))
        val manager = GarminTokenManager(store, remote, timeProvider)

        val result = manager.validAccessToken(USER_ID)

        assertThat(result).isEqualTo(GarminResult.Failure(GarminError.NotConnected))
    }

    @Test
    fun `no stored tokens reports NotConnected`() = runTest {
        val manager = GarminTokenManager(FakeGarminTokenStore(null), remote, timeProvider)

        assertThat(manager.validAccessToken(USER_ID)).isEqualTo(GarminResult.Failure(GarminError.NotConnected))
    }

    @Test
    fun `needsReconnect already set reports ReauthRequired without calling refresh`() = runTest {
        val store = FakeGarminTokenStore(tokens(needsReconnect = true))
        val manager = GarminTokenManager(store, remote, timeProvider)

        val result = manager.validAccessToken(USER_ID)

        assertThat(result).isEqualTo(GarminResult.Failure(GarminError.ReauthRequired))
        assertThat(remote.refreshCallCount).isEqualTo(0)
    }

    @Test
    fun `forceRefresh triggers a refresh even when the token is still valid`() = runTest {
        val store = FakeGarminTokenStore(tokens(expiresAt = timeProvider.instant.epochSecond + 3600))
        remote.refreshResult = DiTokens("access-new", "refresh-new", "client-new", timeProvider.instant.epochSecond + 7200)
        val manager = GarminTokenManager(store, remote, timeProvider)

        val result = manager.validAccessToken(USER_ID, forceRefresh = true)

        assertThat(result).isEqualTo(GarminResult.Success("access-new"))
        assertThat(remote.refreshCallCount).isEqualTo(1)
    }

    @Test
    fun `a token with no refresh token marks needsReconnect instead of calling refresh`() = runTest {
        val store = FakeGarminTokenStore(tokens(expiresAt = timeProvider.instant.epochSecond + 60, refreshToken = null))
        val manager = GarminTokenManager(store, remote, timeProvider)

        val result = manager.validAccessToken(USER_ID)

        assertThat(result).isEqualTo(GarminResult.Failure(GarminError.ReauthRequired))
        assertThat(remote.refreshCallCount).isEqualTo(0)
        assertThat(store.load()?.needsReconnect).isTrue()
    }

    @Test
    fun `two concurrent calls only trigger a single refresh`() = runTest {
        val store = FakeGarminTokenStore(tokens(expiresAt = timeProvider.instant.epochSecond + 60))
        remote.refreshResult = DiTokens("access-new", "refresh-new", "client-new", timeProvider.instant.epochSecond + 3600)
        val gate = CompletableDeferred<Unit>()
        remote.refreshGate = gate
        val manager = GarminTokenManager(store, remote, timeProvider)

        val first = async { manager.validAccessToken(USER_ID) }
        val second = async { manager.validAccessToken(USER_ID) }
        gate.complete(Unit)

        val firstResult = first.await()
        val secondResult = second.await()

        assertThat(remote.refreshCallCount).isEqualTo(1)
        assertThat(firstResult).isEqualTo(GarminResult.Success("access-new"))
        assertThat(secondResult).isEqualTo(GarminResult.Success("access-new"))
    }
}
