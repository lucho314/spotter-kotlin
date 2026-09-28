package com.lucho314.spotter.data.repository

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.crypto.tink.Aead
import com.lucho314.spotter.core.common.AppError
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.core.security.AeadProvider
import com.lucho314.spotter.core.security.EncryptedSessionManager
import com.lucho314.spotter.domain.model.AuthState
import com.lucho314.spotter.testutil.FakeAead
import com.lucho314.spotter.testutil.FakeAeadProvider
import com.lucho314.spotter.testutil.FakeAuthDataSource
import com.lucho314.spotter.testutil.FakeLogger
import io.github.jan.supabase.auth.status.RefreshFailureCause
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val SESSION_KEY = stringPreferencesKey("encrypted_session")

private class ThrowingAeadProvider(private val error: Throwable) : AeadProvider {
    override fun aead(): Aead = throw error
}

private class CountingAeadProvider(private val delegate: Aead = FakeAead()) : AeadProvider {
    var callCount = 0
        private set

    override fun aead(): Aead {
        callCount++
        return delegate
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AuthRepositoryImplTest {

    private lateinit var sessionManager: EncryptedSessionManager
    private lateinit var dataSource: FakeAuthDataSource
    private lateinit var repository: AuthRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scope = TestScope(UnconfinedTestDispatcher())
        val store = PreferenceDataStoreFactory.create(scope = scope) {
            context.preferencesDataStoreFile("test_auth_repo_${System.nanoTime()}")
        }
        sessionManager = EncryptedSessionManager(store, FakeAeadProvider(), Json { ignoreUnknownKeys = true })
        dataSource = FakeAuthDataSource()
        repository = AuthRepositoryImpl(dataSource, sessionManager, FakeLogger(), Dispatchers.IO)
    }

    @Test
    fun `signOut success does not touch clearSession`() = runTest {
        val result = repository.signOut()

        assertThat(result).isEqualTo(AppResult.Success(Unit))
        assertThat(dataSource.signOutCallCount).isEqualTo(1)
        assertThat(dataSource.clearSessionCallCount).isEqualTo(0)
    }

    @Test
    fun `signOut offline still clears the local session and reports the failure`() = runTest {
        dataSource.signOutError = IOException("offline")

        val result = repository.signOut()

        assertThat(result).isEqualTo(AppResult.Failure(AppError.Network))
        assertThat(dataSource.clearSessionCallCount).isEqualTo(1)
    }

    @Test
    fun `signOut is idempotent even when the server rejects it for an unexpected reason`() = runTest {
        dataSource.signOutError = IllegalStateException("boom")

        val result = repository.signOut()

        assertThat(result).isInstanceOf(AppResult.Failure::class.java)
        assertThat(dataSource.clearSessionCallCount).isEqualTo(1)
    }

    @Test
    fun `signOut still reports the original error when clearSession itself throws`() = runTest {
        dataSource.signOutError = IOException("offline")
        dataSource.clearSessionError = IOException("datastore write failed")

        val result = repository.signOut()

        // The original signOut() failure is what's reported, not the clearSession() one.
        assertThat(result).isEqualTo(AppResult.Failure(AppError.Network))
        assertThat(dataSource.clearSessionCallCount).isEqualTo(1)
    }

    @Test
    fun `authState falls back to the last persisted session on RefreshFailure`() = runTest {
        val user = UserInfo(aud = "authenticated", id = "user-1", userMetadata = JsonObject(emptyMap()))
        sessionManager.saveSession(
            UserSession(accessToken = "a", refreshToken = "r", expiresIn = 3600, tokenType = "bearer", user = user),
        )

        dataSource.status.value = SessionStatus.RefreshFailure(RefreshFailureCause.NetworkError(IOException("offline")))

        val state = repository.authState.first()

        assertThat(state).isInstanceOf(AuthState.SignedIn::class.java)
        assertThat((state as AuthState.SignedIn).user.id).isEqualTo("user-1")
    }

    @Test
    fun `authState maps NotAuthenticated to SignedOut`() = runTest {
        dataSource.status.value = SessionStatus.NotAuthenticated()

        val state = repository.authState.first()

        assertThat(state).isEqualTo(AuthState.SignedOut)
    }

    @Test
    fun `currentUser stays consistent with authState during a RefreshFailure`() = runTest {
        val user = UserInfo(aud = "authenticated", id = "user-1", userMetadata = JsonObject(emptyMap()))
        sessionManager.saveSession(
            UserSession(accessToken = "a", refreshToken = "r", expiresIn = 3600, tokenType = "bearer", user = user),
        )
        dataSource.status.value = SessionStatus.RefreshFailure(RefreshFailureCause.NetworkError(IOException("offline")))
        // authDataSource.currentUserOrNull() (the SDK's raw in-memory user) is null in this scenario
        // - only authState's cached/persisted fallback has a user - which used to make every
        // currentUser() caller (WorkoutViewModel, RoutineDetailViewModel, SyncPendingWorkoutsUseCase...)
        // behave as if signed out while offline with an expired token.
        dataSource.currentUser = null

        // Observing authState at least once (as RootViewModel always does) primes the consistency
        // check; currentUser() must then report the same cached user authState did.
        val state = repository.authState.first()

        assertThat(state).isInstanceOf(AuthState.SignedIn::class.java)
        assertThat(repository.currentUser()?.id).isEqualTo("user-1")
    }

    @Test
    fun `currentUser returns null once authState resolves to a real sign-out`() = runTest {
        dataSource.status.value = SessionStatus.NotAuthenticated()

        repository.authState.first()

        assertThat(repository.currentUser()).isNull()
    }

    @Test
    fun `authState treats a fail-closed keystore error as no last known user instead of crashing`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scope = TestScope(UnconfinedTestDispatcher())
        val store = PreferenceDataStoreFactory.create(scope = scope) {
            context.preferencesDataStoreFile("test_auth_repo_throwing_${System.nanoTime()}")
        }
        // Any non-empty value makes loadSessionOrNull() reach aead(), which is what actually
        // throws here (KeysetRecoveryPolicy's fail-closed IllegalStateException).
        store.edit { it[SESSION_KEY] = "not-empty" }
        val throwingSessionManager = EncryptedSessionManager(
            store = store,
            aeadProvider = ThrowingAeadProvider(IllegalStateException("keyset is not backed by the Android Keystore")),
            json = Json { ignoreUnknownKeys = true },
        )
        val repositoryWithThrowingAead = AuthRepositoryImpl(dataSource, throwingSessionManager, FakeLogger(), Dispatchers.IO)
        dataSource.status.value = SessionStatus.RefreshFailure(RefreshFailureCause.NetworkError(IOException("offline")))

        val state = repositoryWithThrowingAead.authState.first()

        assertThat(state).isEqualTo(AuthState.Loading)
    }

    @Test
    fun `authState caches the last known user instead of re-reading DataStore on every RefreshFailure`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scope = TestScope(UnconfinedTestDispatcher())
        val store = PreferenceDataStoreFactory.create(scope = scope) {
            context.preferencesDataStoreFile("test_auth_repo_caching_${System.nanoTime()}")
        }
        val countingAead = CountingAeadProvider()
        val cachingSessionManager = EncryptedSessionManager(store, countingAead, Json { ignoreUnknownKeys = true })
        val user = UserInfo(aud = "authenticated", id = "user-1", userMetadata = JsonObject(emptyMap()))
        cachingSessionManager.saveSession(
            UserSession(accessToken = "a", refreshToken = "r", expiresIn = 3600, tokenType = "bearer", user = user),
        )
        val callsAfterSave = countingAead.callCount
        val repositoryWithCounting = AuthRepositoryImpl(dataSource, cachingSessionManager, FakeLogger(), Dispatchers.IO)
        dataSource.status.value = SessionStatus.RefreshFailure(RefreshFailureCause.NetworkError(IOException("offline")))

        repositoryWithCounting.authState.first()
        repositoryWithCounting.authState.first()

        // Only the first collection should have hit aead(); the second is served from the
        // in-memory cache.
        assertThat(countingAead.callCount).isEqualTo(callsAfterSave + 1)
    }
}
