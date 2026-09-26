package com.lucho314.spotter.domain.model

/** Authenticated user, derived from the Supabase session (see `data/mapper/AuthUserMapper.kt`). */
data class AuthUser(
    val id: String,
    val email: String?,
    val displayName: String,
    val avatarUrl: String?,
)

/** Current authentication state, driven by `AuthRepository.authState`. */
sealed interface AuthState {
    data object Loading : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val user: AuthUser) : AuthState
}
