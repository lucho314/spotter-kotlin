package com.lucho314.spotter.data.repository

import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.core.common.IoDispatcher
import com.lucho314.spotter.core.common.Logger
import com.lucho314.spotter.core.network.safeCall
import com.lucho314.spotter.core.security.EncryptedSessionManager
import com.lucho314.spotter.data.mapper.AuthStateMapper
import com.lucho314.spotter.data.mapper.toDomain
import com.lucho314.spotter.data.remote.datasource.AuthDataSource
import com.lucho314.spotter.domain.model.AuthState
import com.lucho314.spotter.domain.model.AuthUser
import com.lucho314.spotter.domain.repository.AuthRepository
import io.github.jan.supabase.auth.SignOutScope
import io.github.jan.supabase.auth.status.SessionStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

private const val AUTH_CALLBACK_URL = "spotter://auth/callback"
private const val TAG = "AuthRepository"

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val authDataSource: AuthDataSource,
    private val sessionManager: EncryptedSessionManager,
    private val logger: Logger,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AuthRepository {

    // Memoized so a RefreshFailure storm (e.g. repeated refresh attempts while offline) doesn't
    // re-read and re-decrypt the persisted session on every emission. Reset once we observe a
    // real session state (Authenticated/NotAuthenticated) so a later account never sees a
    // previous one's cached user.
    @Volatile
    private var cachedLastKnownUser: AuthUser? = null

    // Mirrors the last value emitted by [authState], so [currentUser] (a plain synchronous getter
    // many call sites use, e.g. in a ViewModel's constructor/init) never diverges from what the UI
    // is actually showing. Before this field existed, currentUser() delegated straight to
    // authDataSource.currentUserOrNull(), which is null unless SessionStatus is exactly
    // Authenticated - so offline with an expired token (RefreshFailure, authState still SignedIn
    // via the cached/persisted user) made every currentUser() caller behave as if signed out.
    @Volatile
    private var lastKnownAuthState: AuthState = AuthState.Loading

    override val authState = authDataSource.sessionStatus.map { status ->
        // A real session state always wins over the cache: refreshes it on sign-in, and clears it
        // on an actual sign-out so a later account on the same device never sees a stale user.
        when (status) {
            is SessionStatus.Authenticated -> cachedLastKnownUser = status.session.user?.toDomain()
            is SessionStatus.NotAuthenticated -> cachedLastKnownUser = null
            else -> Unit
        }
        val lastKnownUser = if (status is SessionStatus.RefreshFailure) {
            // Cold start (or mid-session) offline with an expired access token: fall back to the
            // last persisted session's user instead of tearing the authenticated app down.
            cachedLastKnownUser ?: lastKnownUserOrNull()?.also { cachedLastKnownUser = it }
        } else {
            null
        }
        AuthStateMapper.map(status, lastKnownUser).also { lastKnownAuthState = it }
    }
        // This flow is collected from RootViewModel's stateIn, i.e. on Main. loadSessionOrNull()
        // below does a DataStore read and, on first use, synchronous Android Keystore/Tink work
        // (see KeysetRecoveryPolicy) - never let that block Main.
        .flowOn(ioDispatcher)

    /**
     * Reads the last persisted session's user. `aead()`'s fail-closed check
     * ([com.lucho314.spotter.core.security.KeysetRecoveryPolicy]) can throw [IllegalStateException]
     * (e.g. the Keystore-backed keyset became unusable and got wiped); that must not crash
     * collectors of [authState], so it's treated the same as "no last known user".
     */
    private suspend fun lastKnownUserOrNull(): AuthUser? = try {
        sessionManager.loadSessionOrNull()?.user?.toDomain()
    } catch (e: CancellationException) {
        throw e
    } catch (e: IllegalStateException) {
        logger.w(TAG, "failed to read last known session, treating as signed out")
        null
    }

    // Consistent with authState: an actual SDK session wins, but a RefreshFailure that authState
    // is still reporting as SignedIn (cached/persisted user) must not look "signed out" here too.
    override fun currentUser(): AuthUser? =
        authDataSource.currentUserOrNull()?.toDomain() ?: (lastKnownAuthState as? AuthState.SignedIn)?.user

    override suspend fun signInWithGoogleIdToken(idToken: String, rawNonce: String): AppResult<Unit> = safeCall {
        authDataSource.signInWithGoogleIdToken(idToken, rawNonce)
    }

    override suspend fun startGoogleOAuth(): AppResult<Unit> = safeCall {
        authDataSource.startGoogleOAuth(AUTH_CALLBACK_URL)
    }

    override suspend fun syncProfileDisplayName(user: AuthUser): AppResult<Unit> = safeCall {
        authDataSource.updateDisplayName(user.id, user.displayName)
    }

    override suspend fun signOut(): AppResult<Unit> = safeCall {
        try {
            authDataSource.signOut(SignOutScope.LOCAL)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // The SDK only clears its session on a successful (or ignorable) server response; on
            // any other error (e.g. offline) force it here so sign-out always lands on the login
            // screen, then rethrow the *original* error so the caller still learns the server
            // wasn't notified. clearSession() itself can throw (it writes to DataStore before
            // flipping the in-memory status): don't let that failure mask `e`, but don't lose it
            // either.
            logger.w(TAG, "signOut failed, forcing local session clear")
            try {
                authDataSource.clearSession()
            } catch (ce: CancellationException) {
                throw ce
            } catch (clearError: Throwable) {
                e.addSuppressed(clearError)
            }
            throw e
        }
    }
}
