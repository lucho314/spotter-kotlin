package com.lucho314.spotter.testutil

import com.lucho314.spotter.domain.model.GarminConnectionState
import com.lucho314.spotter.domain.model.GarminLoginResult
import com.lucho314.spotter.domain.model.GarminResult
import com.lucho314.spotter.domain.repository.GarminAccountRepository
import kotlinx.coroutines.flow.MutableStateFlow

class FakeGarminAccountRepository(
    initialConnection: GarminConnectionState = GarminConnectionState.NotConnected,
) : GarminAccountRepository {

    val connection = MutableStateFlow(initialConnection)

    var loginResult: GarminResult<GarminLoginResult> = GarminResult.Success(GarminLoginResult.Connected)
    var verifyResult: GarminResult<Unit> = GarminResult.Success(Unit)

    val loginCalls = mutableListOf<Triple<String, String, String>>()
    val verifyMfaCalls = mutableListOf<Triple<String, String, String>>()
    val setAutoUploadCalls = mutableListOf<Pair<String, Boolean>>()
    var disconnectCallCount = 0
        private set

    override fun observeConnection(userId: String) = connection

    override suspend fun getConnection(userId: String): GarminConnectionState = connection.value

    override suspend fun login(userId: String, email: String, password: String): GarminResult<GarminLoginResult> {
        loginCalls += Triple(userId, email, password)
        return loginResult
    }

    override suspend fun verifyMfa(userId: String, challengeId: String, code: String): GarminResult<Unit> {
        verifyMfaCalls += Triple(userId, challengeId, code)
        return verifyResult
    }

    override suspend fun setAutoUpload(userId: String, enabled: Boolean) {
        setAutoUploadCalls += userId to enabled
        val current = connection.value
        if (current is GarminConnectionState.Connected) connection.value = current.copy(autoUpload = enabled)
    }

    override suspend fun disconnect() {
        disconnectCallCount++
        connection.value = GarminConnectionState.NotConnected
    }
}
