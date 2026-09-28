package com.lucho314.spotter.domain.repository

import com.lucho314.spotter.domain.model.GarminConnectionState
import com.lucho314.spotter.domain.model.GarminLoginResult
import com.lucho314.spotter.domain.model.GarminResult
import kotlinx.coroutines.flow.Flow

interface GarminAccountRepository {
    /** [GarminConnectionState.NotConnected] if there are no tokens, or they belong to another Spotter user (`ownerUserId != userId`). */
    fun observeConnection(userId: String): Flow<GarminConnectionState>
    suspend fun getConnection(userId: String): GarminConnectionState
    suspend fun login(userId: String, email: String, password: String): GarminResult<GarminLoginResult>
    suspend fun verifyMfa(userId: String, challengeId: String, code: String): GarminResult<Unit>
    suspend fun setAutoUpload(userId: String, enabled: Boolean)

    /** Clears the tokens (whoever owns them) and any pending MFA challenge. Never throws for "there was nothing". */
    suspend fun disconnect()
}
