package com.lucho314.spotter.domain.repository

import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.domain.model.AuthState
import com.lucho314.spotter.domain.model.AuthUser
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val authState: Flow<AuthState>

    fun currentUser(): AuthUser?

    /** Primary sign-in path: Credential Manager native ID token flow. */
    suspend fun signInWithGoogleIdToken(idToken: String, rawNonce: String): AppResult<Unit>

    /** Fallback sign-in path: browser-based PKCE OAuth. The session arrives via `handleDeeplinks`. */
    suspend fun startGoogleOAuth(): AppResult<Unit>

    /** Updates the `profiles.display_name` row for [user]. Not an upsert: RLS has no INSERT policy. */
    suspend fun syncProfileDisplayName(user: AuthUser): AppResult<Unit>

    /** Signs out locally. If the network call fails, the local session is cleared anyway. */
    suspend fun signOut(): AppResult<Unit>
}
