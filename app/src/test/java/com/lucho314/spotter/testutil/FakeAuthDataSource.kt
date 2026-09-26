package com.lucho314.spotter.testutil

import com.lucho314.spotter.data.remote.datasource.AuthDataSource
import io.github.jan.supabase.auth.SignOutScope
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.coroutines.flow.MutableStateFlow

class FakeAuthDataSource(
    initialStatus: SessionStatus = SessionStatus.NotAuthenticated(),
) : AuthDataSource {

    val status = MutableStateFlow(initialStatus)
    override val sessionStatus = status

    var currentUser: UserInfo? = null
    var signOutError: Throwable? = null
    var signInError: Throwable? = null
    var startOAuthError: Throwable? = null
    var clearSessionError: Throwable? = null

    var signOutCallCount = 0
    var clearSessionCallCount = 0
    val signInCalls = mutableListOf<Pair<String, String>>()
    var startOAuthCallCount = 0
    val updateDisplayNameCalls = mutableListOf<Pair<String, String>>()

    override fun currentUserOrNull(): UserInfo? = currentUser

    override suspend fun signInWithGoogleIdToken(idToken: String, rawNonce: String) {
        signInCalls += idToken to rawNonce
        signInError?.let { throw it }
    }

    override suspend fun startGoogleOAuth(redirectUrl: String) {
        startOAuthCallCount++
        startOAuthError?.let { throw it }
    }

    override suspend fun signOut(scope: SignOutScope) {
        signOutCallCount++
        signOutError?.let { throw it }
    }

    override suspend fun clearSession() {
        clearSessionCallCount++
        clearSessionError?.let { throw it }
        status.value = SessionStatus.NotAuthenticated(isSignOut = true)
    }

    override suspend fun updateDisplayName(userId: String, displayName: String) {
        updateDisplayNameCalls += userId to displayName
    }
}
