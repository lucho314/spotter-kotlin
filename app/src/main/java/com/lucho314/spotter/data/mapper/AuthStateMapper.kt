package com.lucho314.spotter.data.mapper

import com.lucho314.spotter.domain.model.AuthState
import com.lucho314.spotter.domain.model.AuthUser
import io.github.jan.supabase.auth.status.SessionStatus

/**
 * Pure mapping from the SDK's [SessionStatus] to our domain [AuthState]. Extracted so the
 * `RefreshFailure` (network unreachable, session not necessarily invalid) branch can be unit
 * tested without a real [io.github.jan.supabase.auth.Auth] instance.
 */
object AuthStateMapper {

    /**
     * @param lastKnownUser the most recently seen authenticated user, used to keep the app signed
     * in during a [SessionStatus.RefreshFailure] (e.g. cold start offline with an expired token).
     * Callers typically source this from `EncryptedSessionManager.loadSessionOrNull()?.user`.
     */
    fun map(status: SessionStatus, lastKnownUser: AuthUser?): AuthState = when (status) {
        is SessionStatus.Initializing -> AuthState.Loading

        // `user` is nullable in the SDK's model but always populated once authenticated in
        // practice; fall back to Loading rather than using `!!` for the rare case it is not.
        is SessionStatus.Authenticated -> status.session.user?.toDomain()?.let(AuthState::SignedIn)
            ?: AuthState.Loading

        is SessionStatus.NotAuthenticated -> AuthState.SignedOut

        // A refresh failure means the network is unreachable, not that the session is invalid:
        // keep the user signed in with whatever data we still have instead of logging them out.
        is SessionStatus.RefreshFailure -> lastKnownUser?.let(AuthState::SignedIn) ?: AuthState.Loading
    }
}
