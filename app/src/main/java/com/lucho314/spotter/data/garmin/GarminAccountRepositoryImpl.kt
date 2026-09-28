package com.lucho314.spotter.data.garmin

import com.lucho314.spotter.core.common.IdGenerator
import com.lucho314.spotter.core.common.Logger
import com.lucho314.spotter.core.common.TimeProvider
import com.lucho314.spotter.data.garmin.local.GarminTokenStore
import com.lucho314.spotter.data.garmin.local.StoredGarminTokens
import com.lucho314.spotter.data.garmin.remote.GarminAuthRemoteDataSource
import com.lucho314.spotter.data.garmin.remote.GarminSsoSession
import com.lucho314.spotter.data.garmin.remote.SsoStep
import com.lucho314.spotter.domain.model.GarminConnectionState
import com.lucho314.spotter.domain.model.GarminError
import com.lucho314.spotter.domain.model.GarminLoginResult
import com.lucho314.spotter.domain.model.GarminResult
import com.lucho314.spotter.domain.repository.GarminAccountRepository
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG = "GarminAccountRepository"
private const val MFA_TTL_SECONDS = 600L

@Singleton
class GarminAccountRepositoryImpl @Inject constructor(
    private val remote: GarminAuthRemoteDataSource,
    private val store: GarminTokenStore,
    private val timeProvider: TimeProvider,
    private val idGenerator: IdGenerator,
    private val logger: Logger,
) : GarminAccountRepository {

    private data class PendingMfa(val id: String, val userId: String, val session: GarminSsoSession, val method: String?, val expiresAt: Instant)

    private val mutex = Mutex()
    private var pendingMfa: PendingMfa? = null

    override fun observeConnection(userId: String): Flow<GarminConnectionState> =
        store.tokens.map { toConnectionState(it, userId) }

    override suspend fun getConnection(userId: String): GarminConnectionState = toConnectionState(store.load(), userId)

    override suspend fun login(userId: String, email: String, password: String): GarminResult<GarminLoginResult> {
        clearPendingMfa()
        return garminCall {
            when (val step = remote.login(email, password)) {
                is SsoStep.Ticket -> completeLogin(userId, step.ticket)
                is SsoStep.Mfa -> {
                    val challengeId = idGenerator.uuid()
                    val pending = PendingMfa(challengeId, userId, step.session, step.method, timeProvider.now().plusSeconds(MFA_TTL_SECONDS))
                    mutex.withLock { pendingMfa = pending }
                    GarminLoginResult.MfaRequired(challengeId, step.method)
                }
            }
        }
    }

    override suspend fun verifyMfa(userId: String, challengeId: String, code: String): GarminResult<Unit> {
        val pending = mutex.withLock { pendingMfa }
        if (pending == null || pending.id != challengeId || pending.userId != userId || timeProvider.now().isAfter(pending.expiresAt)) {
            clearPendingMfa()
            return GarminResult.Failure(GarminError.MfaSessionExpired)
        }
        return garminCall {
            try {
                val ticket = remote.verifyMfa(pending.session, pending.method, code)
                consumePendingMfa(pending.id)
                completeLogin(userId, ticket)
            } catch (e: GarminApiException) {
                when (e.error) {
                    // The user can retry without redoing the SSO round-trip: keep the session open.
                    GarminError.InvalidMfaCode, GarminError.RateLimited -> Unit
                    else -> {
                        logger.w(TAG, "garmin verifyMfa failed: ${e.error::class.simpleName}")
                        consumePendingMfa(pending.id)
                    }
                }
                throw e
            }
        }
    }

    override suspend fun setAutoUpload(userId: String, enabled: Boolean) {
        val stored = store.load() ?: return
        if (stored.ownerUserId != userId) return
        store.save(stored.copy(autoUpload = enabled))
    }

    override suspend fun disconnect() {
        clearPendingMfa()
        store.clear()
    }

    private suspend fun completeLogin(userId: String, ticket: String): GarminLoginResult {
        val tokens = remote.exchangeTicket(ticket)
        val displayName = try {
            remote.fetchDisplayName(tokens.accessToken)
        } catch (e: GarminApiException) {
            if (e.error == GarminError.ReauthRequired) throw e
            null
        }
        store.save(
            StoredGarminTokens(
                ownerUserId = userId,
                accessToken = tokens.accessToken,
                refreshToken = tokens.refreshToken,
                clientId = tokens.clientId,
                accessExpiresAtEpochSec = tokens.expiresAtEpochSec,
                displayName = displayName,
                autoUpload = true,
                needsReconnect = false,
                connectedAtEpochMs = timeProvider.now().toEpochMilli(),
            ),
        )
        return GarminLoginResult.Connected
    }

    private suspend fun consumePendingMfa(id: String) {
        val session = mutex.withLock {
            if (pendingMfa?.id == id) pendingMfa.also { pendingMfa = null }?.session else null
        }
        session?.close()
    }

    private suspend fun clearPendingMfa() {
        val session = mutex.withLock { pendingMfa.also { pendingMfa = null }?.session }
        session?.close()
    }

    private fun toConnectionState(tokens: StoredGarminTokens?, userId: String): GarminConnectionState =
        if (tokens == null || tokens.ownerUserId != userId) {
            GarminConnectionState.NotConnected
        } else {
            GarminConnectionState.Connected(tokens.displayName, tokens.autoUpload, tokens.needsReconnect)
        }
}
