package com.lucho314.spotter.testutil

import com.lucho314.spotter.core.common.AppError
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.domain.model.AuthState
import com.lucho314.spotter.domain.model.AuthUser
import com.lucho314.spotter.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [AuthRepository] test double; call sites can inspect the recorded calls. */
class FakeAuthRepository(initialState: AuthState = AuthState.Loading) : AuthRepository {

    val state = MutableStateFlow(initialState)
    override val authState = state

    var signInWithIdTokenResult: AppResult<Unit> = AppResult.Success(Unit)
    var startGoogleOAuthResult: AppResult<Unit> = AppResult.Success(Unit)
    var syncProfileDisplayNameResult: AppResult<Unit> = AppResult.Success(Unit)
    var signOutResult: AppResult<Unit> = AppResult.Success(Unit)

    val signInWithIdTokenCalls = mutableListOf<Pair<String, String>>()
    var startGoogleOAuthCallCount = 0
    val syncProfileDisplayNameCalls = mutableListOf<AuthUser>()
    var signOutCallCount = 0

    /**
     * By default [currentUser] derives from [state], mirroring the production fix
     * (`AuthRepositoryImpl.currentUser()` never diverges from `authState`). Set this to
     * `true`/non-null to reproduce the historical bug - `authState` reporting `SignedIn` (e.g. via
     * a `RefreshFailure`'s cached user) while `currentUser()` still returned `null` - for tests that
     * need to exercise a caller's defensive handling of that divergence.
     */
    var currentUserOverrideEnabled = false
    var currentUserOverride: AuthUser? = null

    override fun currentUser(): AuthUser? =
        if (currentUserOverrideEnabled) currentUserOverride else (state.value as? AuthState.SignedIn)?.user

    override suspend fun signInWithGoogleIdToken(idToken: String, rawNonce: String): AppResult<Unit> {
        signInWithIdTokenCalls += idToken to rawNonce
        return signInWithIdTokenResult
    }

    override suspend fun startGoogleOAuth(): AppResult<Unit> {
        startGoogleOAuthCallCount++
        return startGoogleOAuthResult
    }

    override suspend fun syncProfileDisplayName(user: AuthUser): AppResult<Unit> {
        syncProfileDisplayNameCalls += user
        return syncProfileDisplayNameResult
    }

    override suspend fun signOut(): AppResult<Unit> {
        signOutCallCount++
        return signOutResult
    }
}

fun networkFailure(): AppResult<Unit> = AppResult.Failure(AppError.Network)
