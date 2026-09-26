package com.lucho314.spotter.data.remote.datasource

import io.github.jan.supabase.auth.SignOutScope
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.coroutines.flow.Flow

/**
 * Wraps every supabase-kt `Auth`/`Postgrest` call [com.lucho314.spotter.data.repository.AuthRepositoryImpl]
 * needs. Per ADR A2, this is a plain interface so the repository can be unit tested with a fake
 * instead of mocking supabase-kt's `Auth`, which has too large a surface to fake by hand.
 */
interface AuthDataSource {
    val sessionStatus: Flow<SessionStatus>

    fun currentUserOrNull(): UserInfo?

    /** Primary sign-in path: Credential Manager native ID token flow. */
    suspend fun signInWithGoogleIdToken(idToken: String, rawNonce: String)

    /** Fallback sign-in path: browser-based PKCE OAuth. The session arrives via `handleDeeplinks`. */
    suspend fun startGoogleOAuth(redirectUrl: String)

    suspend fun signOut(scope: SignOutScope)

    /**
     * Forces the SDK's in-memory + persisted session to a signed-out state. **Can throw**: the
     * real implementation deletes the PKCE verifier and session from DataStore first (either of
     * which can throw `IOException`) and only then updates the in-memory `sessionStatus`. Callers
     * that use this as a last-resort recovery (e.g. `AuthRepositoryImpl.signOut`) must handle that.
     */
    suspend fun clearSession()

    /** Updates `profiles.display_name`. Not an upsert: RLS has no INSERT policy. */
    suspend fun updateDisplayName(userId: String, displayName: String)
}
