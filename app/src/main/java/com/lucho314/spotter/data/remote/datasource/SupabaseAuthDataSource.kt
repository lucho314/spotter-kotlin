package com.lucho314.spotter.data.remote.datasource

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.SignOutScope
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.postgrest.Postgrest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class SupabaseAuthDataSource @Inject constructor(
    private val auth: Auth,
    private val postgrest: Postgrest,
) : AuthDataSource {

    override val sessionStatus: Flow<SessionStatus> get() = auth.sessionStatus

    override fun currentUserOrNull(): UserInfo? = auth.currentUserOrNull()

    override suspend fun signInWithGoogleIdToken(idToken: String, rawNonce: String) {
        auth.signInWith(IDToken) {
            this.idToken = idToken
            provider = Google
            nonce = rawNonce
        }
    }

    override suspend fun startGoogleOAuth(redirectUrl: String) {
        auth.signInWith(Google, redirectUrl = redirectUrl)
    }

    override suspend fun signOut(scope: SignOutScope) {
        auth.signOut(scope)
    }

    override suspend fun clearSession() {
        auth.clearSession()
    }

    override suspend fun updateDisplayName(userId: String, displayName: String) {
        postgrest.from("profiles").update({
            set("display_name", displayName)
        }) {
            filter { eq("id", userId) }
        }
    }
}
