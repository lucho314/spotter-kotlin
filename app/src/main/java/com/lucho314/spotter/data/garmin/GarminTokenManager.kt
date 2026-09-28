package com.lucho314.spotter.data.garmin

import com.lucho314.spotter.core.common.TimeProvider
import com.lucho314.spotter.data.garmin.local.GarminTokenStore
import com.lucho314.spotter.data.garmin.remote.GarminAuthRemoteDataSource
import com.lucho314.spotter.data.garmin.remote.GarminEndpoints
import com.lucho314.spotter.domain.model.GarminError
import com.lucho314.spotter.domain.model.GarminResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Single source of truth for a valid Garmin Bearer token. Everything runs under [mutex] so a
 * worker run racing a login/reconnect (or two upload attempts) never triggers two concurrent
 * refreshes against the same refresh token.
 */
@Singleton
class GarminTokenManager @Inject constructor(
    private val store: GarminTokenStore,
    private val remote: GarminAuthRemoteDataSource,
    private val timeProvider: TimeProvider,
) {
    private val mutex = Mutex()

    /** Refreshes if the token expires within [GarminEndpoints.REFRESH_MARGIN_SECONDS], or if [forceRefresh]. */
    suspend fun validAccessToken(userId: String, forceRefresh: Boolean = false): GarminResult<String> = mutex.withLock {
        val stored = store.load()
        if (stored == null || stored.ownerUserId != userId) return@withLock GarminResult.Failure(GarminError.NotConnected)
        if (stored.needsReconnect) return@withLock GarminResult.Failure(GarminError.ReauthRequired)

        val now = timeProvider.now().epochSecond
        if (!forceRefresh && now < stored.accessExpiresAtEpochSec - GarminEndpoints.REFRESH_MARGIN_SECONDS) {
            return@withLock GarminResult.Success(stored.accessToken)
        }

        val refreshToken = stored.refreshToken
        if (refreshToken == null) {
            store.save(stored.copy(needsReconnect = true))
            return@withLock GarminResult.Failure(GarminError.ReauthRequired)
        }

        when (val result = garminCall { remote.refresh(stored.clientId, refreshToken) }) {
            is GarminResult.Success -> {
                val refreshed = stored.copy(
                    accessToken = result.value.accessToken,
                    refreshToken = result.value.refreshToken ?: stored.refreshToken,
                    clientId = result.value.clientId,
                    accessExpiresAtEpochSec = result.value.expiresAtEpochSec,
                )
                store.save(refreshed)
                GarminResult.Success(refreshed.accessToken)
            }
            is GarminResult.Failure -> {
                // Only a rejected refresh means the user must reconnect; a transient error
                // (Network/RateLimited/Server) leaves the stored tokens untouched for the next try.
                if (result.error == GarminError.ReauthRequired) store.save(stored.copy(needsReconnect = true))
                result
            }
        }
    }

    /** Marks the stored tokens (if they belong to [userId]) as needing reconnection - used after a second consecutive 401 on upload. */
    suspend fun markNeedsReconnect(userId: String): Unit = mutex.withLock {
        val stored = store.load() ?: return@withLock
        if (stored.ownerUserId != userId) return@withLock
        store.save(stored.copy(needsReconnect = true))
    }
}
