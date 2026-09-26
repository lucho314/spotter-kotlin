package com.lucho314.spotter.data.mapper

import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.domain.model.AuthState
import com.lucho314.spotter.domain.model.AuthUser
import io.github.jan.supabase.auth.status.RefreshFailureCause
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import java.io.IOException
import kotlinx.serialization.json.JsonObject
import org.junit.Test

class AuthStateMapperTest {

    private val lastKnownUser = AuthUser(id = "user-1", email = "a@b.com", displayName = "Ada", avatarUrl = null)

    private fun userInfo(id: String = "user-1") = UserInfo(aud = "authenticated", id = id, userMetadata = JsonObject(emptyMap()))

    private fun session(user: UserInfo?) = UserSession(
        accessToken = "access",
        refreshToken = "refresh",
        expiresIn = 3600,
        tokenType = "bearer",
        user = user,
    )

    @Test
    fun `Initializing maps to Loading`() {
        val result = AuthStateMapper.map(SessionStatus.Initializing, lastKnownUser = null)

        assertThat(result).isEqualTo(AuthState.Loading)
    }

    @Test
    fun `Authenticated with a user maps to SignedIn`() {
        val status = SessionStatus.Authenticated(session(userInfo()))

        val result = AuthStateMapper.map(status, lastKnownUser = null)

        assertThat(result).isInstanceOf(AuthState.SignedIn::class.java)
        assertThat((result as AuthState.SignedIn).user.id).isEqualTo("user-1")
    }

    @Test
    fun `Authenticated without a user falls back to Loading instead of crashing`() {
        val status = SessionStatus.Authenticated(session(user = null))

        val result = AuthStateMapper.map(status, lastKnownUser = null)

        assertThat(result).isEqualTo(AuthState.Loading)
    }

    @Test
    fun `NotAuthenticated maps to SignedOut`() {
        val result = AuthStateMapper.map(SessionStatus.NotAuthenticated(isSignOut = true), lastKnownUser = null)

        assertThat(result).isEqualTo(AuthState.SignedOut)
    }

    @Test
    fun `RefreshFailure with a last known user stays signed in`() {
        val status = SessionStatus.RefreshFailure(RefreshFailureCause.NetworkError(IOException("offline")))

        val result = AuthStateMapper.map(status, lastKnownUser)

        assertThat(result).isEqualTo(AuthState.SignedIn(lastKnownUser))
    }

    @Test
    fun `RefreshFailure without a last known user maps to Loading`() {
        val status = SessionStatus.RefreshFailure(RefreshFailureCause.NetworkError(IOException("offline")))

        val result = AuthStateMapper.map(status, lastKnownUser = null)

        assertThat(result).isEqualTo(AuthState.Loading)
    }
}
